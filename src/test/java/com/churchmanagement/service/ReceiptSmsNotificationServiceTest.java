package com.churchmanagement.service;

import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Receipt;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.repository.ReceiptRepository;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.repository.SmsSettingsRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptSmsNotificationServiceTest {
    private FakeReceiptRepository receiptRepository;
    private FakeChurchRepository churchRepository;
    private FakeSmsSettingsRepository smsSettingsRepository;
    private FakeSmsLogRepository smsLogRepository;
    private FakeSmsService smsService;
    private FakeActivityLogService activityLogService;
    private ReceiptSmsNotificationService notificationService;

    @BeforeEach
    void setUp() {
        receiptRepository = new FakeReceiptRepository();
        churchRepository = new FakeChurchRepository();
        smsSettingsRepository = new FakeSmsSettingsRepository();
        smsLogRepository = new FakeSmsLogRepository();
        smsService = new FakeSmsService();
        activityLogService = new FakeActivityLogService();
        notificationService = new ReceiptSmsNotificationService(receiptRepository, churchRepository,
                smsSettingsRepository, smsLogRepository, smsService, activityLogService, fixedClock());
        AuthContext.setCurrentUser(new AuthenticatedUser(7L, "admin", "System Administrator", 1L,
                "Admin", List.of("sms.settings.manage")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void sendSmsSuccessfullyWithMockService() {
        notificationService.sendReceiptSubmissionSms(100L);

        assertEquals("0712345678", smsService.mobileNumber);
        assertTrue(smsService.message.contains("Receipt REC26000001 received for CH001 - St. Mary's Church"));
        assertEquals(SmsLogRepository.SmsStatus.SUCCESS, smsLogRepository.status);
        assertEquals(ActivityLogService.SMS_SENT, activityLogService.action);
    }

    @Test
    void skipIfSmsDisabled() {
        smsSettingsRepository.enabled = false;

        notificationService.sendReceiptSubmissionSms(100L);

        assertFalse(smsService.called);
        assertEquals(0, smsLogRepository.insertCount);
        assertEquals(ActivityLogService.SMS_SKIPPED, activityLogService.action);
    }

    @Test
    void skipIfChurchMobileNumberMissing() {
        churchRepository.smsMobileNumber = " ";

        notificationService.sendReceiptSubmissionSms(100L);

        assertFalse(smsService.called);
        assertEquals(0, smsLogRepository.insertCount);
        assertEquals(ActivityLogService.SMS_SKIPPED, activityLogService.action);
    }

    @Test
    void insertSmsLogOnFailure() {
        smsService.result = new SmsResult(false, "Gateway rejected message.", MockSmsService.PROVIDER, null);

        notificationService.sendReceiptSubmissionSms(100L);

        assertEquals(SmsLogRepository.SmsStatus.FAILED, smsLogRepository.status);
        assertEquals("Gateway rejected message.", smsLogRepository.errorMessage);
        assertEquals(ActivityLogService.SMS_FAILED, activityLogService.action);
    }

    @Test
    void mockSmsServiceValidatesAndSends() {
        MockSmsService mockSmsService = new MockSmsService(fixedClock());

        SmsResult result = mockSmsService.sendSms("0712345678", "Receipt received.");

        assertTrue(result.isSuccess());
        assertEquals(MockSmsService.PROVIDER, result.getProvider());
        assertEquals(LocalDateTime.of(2026, 5, 18, 9, 0), result.getSentAt());
        assertFalse(mockSmsService.sendSms(" ", "Receipt received.").isSuccess());
        assertFalse(mockSmsService.sendSms("0712345678", " ").isSuccess());
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-18T09:00:00Z"), ZoneId.of("UTC"));
    }

    private static class FakeReceiptRepository extends ReceiptRepository {
        private FakeReceiptRepository() {
            super((DataSource) null);
        }

        @Override
        public Optional<Receipt> findReceiptById(long receiptId) {
            Receipt receipt = new Receipt();
            receipt.setId(receiptId);
            receipt.setReceiptNo("REC26000001");
            receipt.setChurchId(10L);
            receipt.setRegionId(1L);
            receipt.setWeekStartDate(LocalDate.of(2026, 5, 11));
            receipt.setWeekEndDate(LocalDate.of(2026, 5, 17));
            receipt.setStatus(ReceiptStatus.ACTIVE);
            return Optional.of(receipt);
        }

        @Override
        public Optional<ReceiptResponseDto> findReceiptDetailsById(long receiptId) {
            ReceiptResponseDto dto = new ReceiptResponseDto();
            dto.setId(receiptId);
            dto.setTotalAmount(new BigDecimal("12500.00"));
            return Optional.of(dto);
        }
    }

    private static class FakeChurchRepository extends ChurchRepository {
        private String smsMobileNumber = "0712345678";

        private FakeChurchRepository() {
            super((DataSource) null);
        }

        @Override
        public Optional<Church> findById(long id) {
            Church church = new Church(id, "CH001", "St. Mary's Church", 1L, "REG001", "North",
                    Church.Status.ACTIVE, LocalDateTime.now(), null);
            church.setSmsMobileNumber(smsMobileNumber);
            return Optional.of(church);
        }
    }

    private static class FakeSmsSettingsRepository extends SmsSettingsRepository {
        private boolean enabled = true;

        private FakeSmsSettingsRepository() {
            super(null);
        }

        @Override
        public SmsSettings getSettings() {
            SmsSettings settings = new SmsSettings();
            settings.setSmsEnabled(enabled);
            settings.setGatewayType(SmsSettings.GatewayType.MOCK);
            return settings;
        }
    }

    private static class FakeSmsLogRepository extends SmsLogRepository {
        private int insertCount;
        private SmsStatus status;
        private String errorMessage;

        private FakeSmsLogRepository() {
            super(null);
        }

        @Override
        public void insertSmsLog(Long receiptId, Long churchId, String mobileNumber, String message, String provider,
                                 SmsStatus status, String errorMessage, LocalDateTime sentAt, LocalDateTime createdAt) {
            insertCount++;
            this.status = status;
            this.errorMessage = errorMessage;
        }
    }

    private static class FakeSmsService implements SmsService {
        private SmsResult result = new SmsResult(true, "SMS sent successfully.", MockSmsService.PROVIDER,
                LocalDateTime.of(2026, 5, 18, 9, 0));
        private boolean called;
        private String mobileNumber;
        private String message;

        @Override
        public SmsResult sendSms(String mobileNumber, String message) {
            called = true;
            this.mobileNumber = mobileNumber;
            this.message = message;
            return result;
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String action;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logSmsSent(Long userId, long receiptId, Long churchId, String mobileNumber, String provider) {
            action = SMS_SENT;
        }

        @Override
        public void logSmsFailed(Long userId, Long receiptId, Long churchId, String mobileNumber, String reason) {
            action = SMS_FAILED;
        }

        @Override
        public void logSmsSkipped(Long userId, Long receiptId, Long churchId, String reason) {
            action = SMS_SKIPPED;
        }
    }
}
