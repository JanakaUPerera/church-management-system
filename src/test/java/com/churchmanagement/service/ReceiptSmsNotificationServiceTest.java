package com.churchmanagement.service;

import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Receipt;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.exception.DatabaseException;
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
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptSmsNotificationServiceTest {
    private FakeReceiptRepository receiptRepository;
    private FakeChurchRepository churchRepository;
    private FakeSmsSettingsRepository smsSettingsRepository;
    private FakeSmsLogRepository smsLogRepository;
    private FakeActivityLogService activityLogService;
    private ReceiptSmsNotificationService notificationService;

    @BeforeEach
    void setUp() {
        receiptRepository = new FakeReceiptRepository();
        churchRepository = new FakeChurchRepository();
        smsSettingsRepository = new FakeSmsSettingsRepository();
        smsLogRepository = new FakeSmsLogRepository();
        activityLogService = new FakeActivityLogService();
        notificationService = new ReceiptSmsNotificationService(receiptRepository, churchRepository,
                smsSettingsRepository, smsLogRepository, activityLogService, fixedClock());
        AuthContext.setCurrentUser(new AuthenticatedUser(7L, "admin", "System Administrator", 1L,
                "Admin", List.of("sms.settings.manage")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void queuesSmsForReceiptSubmission() {
        notificationService.sendReceiptSubmissionSms(100L);

        assertEquals(1, smsLogRepository.enqueueCount);
        assertEquals("0712345678", smsLogRepository.mobileNumber);
        assertTrue(smsLogRepository.message.contains("Receipt REC26000001 received for CH001 - St. Mary's Church"));
        assertEquals(7L, smsLogRepository.queuedByUserId);
        assertEquals(LocalDateTime.of(2026, 5, 18, 9, 0), smsLogRepository.queuedAt);
    }

    @Test
    void skipIfSmsDisabled() {
        smsSettingsRepository.enabled = false;

        notificationService.sendReceiptSubmissionSms(100L);

        assertEquals(0, smsLogRepository.enqueueCount);
        assertEquals(ActivityLogService.SMS_SKIPPED, activityLogService.action);
    }

    @Test
    void throwsWhenChurchMobileNumberMissing() {
        churchRepository.smsMobileNumber = " ";

        ReceiptSmsNotificationService.SmsNotificationException exception = assertThrows(
                ReceiptSmsNotificationService.SmsNotificationException.class,
                () -> notificationService.sendReceiptSubmissionSms(100L));

        assertEquals("Can't send a SMS without a Mobile number.", exception.getMessage());
        assertEquals(0, smsLogRepository.enqueueCount);
        assertEquals(ActivityLogService.SMS_SKIPPED, activityLogService.action);
    }

    @Test
    void wrapsQueueFailureAsNotificationException() {
        smsLogRepository.throwOnEnqueue = true;

        ReceiptSmsNotificationService.SmsNotificationException exception = assertThrows(
                ReceiptSmsNotificationService.SmsNotificationException.class,
                () -> notificationService.sendReceiptSubmissionSms(100L));

        assertEquals("Unable to queue SMS notification.", exception.getMessage());
    }

    @Test
    void mockSmsServiceValidatesAndSends() {
        MockSmsService mockSmsService = new MockSmsService(fixedClock());

        var result = mockSmsService.sendSms("0712345678", "Receipt received.");

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
        private int enqueueCount;
        private String mobileNumber;
        private String message;
        private Long queuedByUserId;
        private LocalDateTime queuedAt;
        private boolean throwOnEnqueue;

        private FakeSmsLogRepository() {
            super((DataSource) null);
        }

        @Override
        public void enqueue(Long receiptId, Long churchId, String mobileNumber, String message,
                            Long queuedByUserId, LocalDateTime queuedAt) {
            if (throwOnEnqueue) {
                throw new DatabaseException("Unable to queue SMS.", new SQLException("boom"));
            }
            enqueueCount++;
            this.mobileNumber = mobileNumber;
            this.message = message;
            this.queuedByUserId = queuedByUserId;
            this.queuedAt = queuedAt;
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String action;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logSmsSkipped(Long userId, Long receiptId, Long churchId, String reason) {
            action = SMS_SKIPPED;
        }
    }
}
