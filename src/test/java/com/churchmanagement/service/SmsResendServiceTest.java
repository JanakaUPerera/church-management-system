package com.churchmanagement.service;

import com.churchmanagement.dto.SmsLogDto;
import com.churchmanagement.dto.SmsResendRequest;
import com.churchmanagement.entity.Church;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmsResendServiceTest {
    private FakeSmsLogRepository smsLogRepository;
    private FakeChurchRepository churchRepository;
    private FakeActivityLogService activityLogService;
    private SmsResendService smsResendService;

    @BeforeEach
    void setUp() {
        smsLogRepository = new FakeSmsLogRepository();
        churchRepository = new FakeChurchRepository();
        activityLogService = new FakeActivityLogService();
        smsResendService = new SmsResendService(smsLogRepository, churchRepository, activityLogService,
                fixedClock("2026-05-05T10:00:00Z"), new FakeSystemConfigurationCache(Map.of(
                "sms.retry.max.attempts", "3"
        )));
        AuthContext.setCurrentUser(new AuthenticatedUser(7L, "admin", "System Administrator", 1L,
                "Admin", List.of("sms.resend")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void userWithPermissionCanResendSmsWithinSevenDays() {
        smsResendService.resendSms(validRequest());

        assertEquals(1, smsLogRepository.enqueueCount);
        assertEquals(100L, smsLogRepository.originalSmsLogId);
        assertEquals(7L, smsLogRepository.resentByUserId);
    }

    @Test
    void userWithoutPermissionCannotResend() {
        AuthContext.setCurrentUser(new AuthenticatedUser(8L, "user", "Standard User", 2L,
                "User", List.of("sms.logs.view")));

        SmsResendService.SmsResendException exception = assertThrows(
                SmsResendService.SmsResendException.class,
                () -> smsResendService.resendSms(validRequest()));

        assertEquals("You do not have permission to resend SMS.", exception.getMessage());
        assertEquals(0, smsLogRepository.enqueueCount);
        assertEquals(ActivityLogService.SMS_RESEND_BLOCKED_PERMISSION, activityLogService.action);
    }

    @Test
    void rejectResendAfterSevenDays() {
        smsLogRepository.original.setCreatedAt(LocalDateTime.of(2026, 5, 1, 10, 0));
        smsResendService = new SmsResendService(smsLogRepository, churchRepository, activityLogService,
                fixedClock("2026-05-08T10:00:01Z"), new FakeSystemConfigurationCache(Map.of(
                "sms.retry.max.attempts", "3"
        )));

        SmsResendService.SmsResendException exception = assertThrows(
                SmsResendService.SmsResendException.class,
                () -> smsResendService.resendSms(validRequest()));

        assertEquals("SMS resend is allowed only within 7 days from the original SMS.", exception.getMessage());
        assertEquals(0, smsLogRepository.enqueueCount);
        assertEquals(ActivityLogService.SMS_RESEND_BLOCKED_EXPIRED, activityLogService.action);
    }

    @Test
    void resendEnqueuesRowLinkedToOriginal() {
        smsResendService.resendSms(validRequest());

        assertEquals(1, smsLogRepository.enqueueCount);
        assertEquals(20L, smsLogRepository.receiptId);
        assertEquals(10L, smsLogRepository.churchId);
        assertEquals("Asked by church office", smsLogRepository.resendReason);
    }

    @Test
    void resendReasonMaxLengthValidation() {
        SmsResendRequest request = validRequest();
        request.setResendReason("x".repeat(256));

        SmsResendService.SmsResendException exception = assertThrows(
                SmsResendService.SmsResendException.class,
                () -> smsResendService.resendSms(request));

        assertEquals("Resend reason cannot exceed 255 characters.", exception.getMessage());
        assertEquals(0, smsLogRepository.enqueueCount);
    }

    @Test
    void resendReasonIsRequired() {
        SmsResendRequest request = validRequest();
        request.setResendReason(" ");

        SmsResendService.SmsResendException exception = assertThrows(
                SmsResendService.SmsResendException.class,
                () -> smsResendService.resendSms(request));

        assertEquals("Resend reason is required.", exception.getMessage());
        assertEquals(0, smsLogRepository.enqueueCount);
    }

    @Test
    void resendUsesCurrentChurchMobileNumberAndSameMessage() {
        churchRepository.smsMobileNumber = "+94770000002";

        smsResendService.resendSms(validRequest());

        assertEquals("+94770000002", smsLogRepository.mobileNumber);
        assertEquals("Receipt REC26000001 received.", smsLogRepository.message);
    }

    @Test
    void rejectResendWhenCurrentChurchMobileNumberMissing() {
        churchRepository.smsMobileNumber = " ";

        SmsResendService.SmsResendException exception = assertThrows(
                SmsResendService.SmsResendException.class,
                () -> smsResendService.resendSms(validRequest()));

        assertEquals("Church SMS mobile number is missing.", exception.getMessage());
        assertEquals(0, smsLogRepository.enqueueCount);
    }

    @Test
    void rejectResendWhenSelectedSmsAlreadyHasResend() {
        smsLogRepository.hasResend = true;

        SmsResendService.SmsResendException exception = assertThrows(
                SmsResendService.SmsResendException.class,
                () -> smsResendService.resendSms(validRequest()));

        assertEquals("A newer resend already exists for this SMS.", exception.getMessage());
        assertEquals(0, smsLogRepository.enqueueCount);
    }

    @Test
    void rejectResendWhenSelectedSmsReachedConfiguredAttemptLimit() {
        smsLogRepository.original.setAttemptCount(3);

        SmsResendService.SmsResendException exception = assertThrows(
                SmsResendService.SmsResendException.class,
                () -> smsResendService.resendSms(validRequest()));

        assertEquals("SMS resend attempt limit has been reached.", exception.getMessage());
        assertEquals(0, smsLogRepository.enqueueCount);
    }

    @Test
    void resendCarriesPriorAttemptCountForProcessorBudget() {
        smsLogRepository.original.setAttemptCount(2);

        smsResendService.resendSms(validRequest());

        assertEquals(2, smsLogRepository.priorAttemptCount);
    }

    private SmsResendRequest validRequest() {
        SmsResendRequest request = new SmsResendRequest();
        request.setSmsLogId(100L);
        request.setResendReason("Asked by church office");
        return request;
    }

    private Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneId.of("UTC"));
    }

    private static class FakeSmsLogRepository extends SmsLogRepository {
        private final SmsLogDto original = originalLog();
        private int enqueueCount;
        private long originalSmsLogId;
        private long resentByUserId;
        private Long receiptId;
        private Long churchId;
        private String mobileNumber;
        private String message;
        private String resendReason;
        private int priorAttemptCount;
        private boolean hasResend;

        private FakeSmsLogRepository() {
            super((DataSource) null);
        }

        @Override
        public Optional<SmsLogDto> findByIdForResend(long smsLogId) {
            return smsLogId == 100L ? Optional.of(original) : Optional.empty();
        }

        @Override
        public boolean hasResend(long smsLogId) {
            return hasResend;
        }

        @Override
        public void enqueueResend(Long receiptId, Long churchId, String mobileNumber, String message,
                                  long originalSmsLogId, long resentByUserId, String resendReason,
                                  int priorAttemptCount, LocalDateTime queuedAt) {
            enqueueCount++;
            this.receiptId = receiptId;
            this.churchId = churchId;
            this.mobileNumber = mobileNumber;
            this.message = message;
            this.originalSmsLogId = originalSmsLogId;
            this.resentByUserId = resentByUserId;
            this.resendReason = resendReason;
            this.priorAttemptCount = priorAttemptCount;
        }

        private static SmsLogDto originalLog() {
            SmsLogDto log = new SmsLogDto();
            log.setId(100L);
            log.setReceiptId(20L);
            log.setChurchId(10L);
            log.setMobileNumber("+94712345678");
            log.setMessage("Receipt REC26000001 received.");
            log.setStatus(SmsLogRepository.SmsStatus.FAILED.name());
            log.setCreatedAt(LocalDateTime.of(2026, 5, 1, 10, 0));
            return log;
        }
    }

    private static class FakeChurchRepository extends ChurchRepository {
        private String smsMobileNumber = "+94712345678";

        private FakeChurchRepository() {
            super((DataSource) null);
        }

        @Override
        public Optional<Church> findById(long id) {
            if (id != 10L) {
                return Optional.empty();
            }
            Church church = new Church();
            church.setId(id);
            church.setSmsMobileNumber(smsMobileNumber);
            return Optional.of(church);
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String action;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logSmsResendBlockedExpired(Long userId, Long originalSmsLogId) {
            action = SMS_RESEND_BLOCKED_EXPIRED;
        }

        @Override
        public void logSmsResendBlockedPermission(Long userId, Long originalSmsLogId) {
            action = SMS_RESEND_BLOCKED_PERMISSION;
        }
    }

    private static class FakeSystemConfigurationCache extends SystemConfigurationCache {
        private final Map<String, String> values;

        private FakeSystemConfigurationCache(Map<String, String> values) {
            super(null);
            this.values = values;
        }

        @Override
        public String getString(String key) {
            return values.get(key);
        }
    }
}
