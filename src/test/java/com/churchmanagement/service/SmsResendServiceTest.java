package com.churchmanagement.service;

import com.churchmanagement.dto.SmsLogDto;
import com.churchmanagement.dto.SmsResendRequest;
import com.churchmanagement.dto.SmsResult;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsResendServiceTest {
    private FakeSmsLogRepository smsLogRepository;
    private FakeSmsService smsService;
    private FakeActivityLogService activityLogService;
    private SmsResendService smsResendService;

    @BeforeEach
    void setUp() {
        smsLogRepository = new FakeSmsLogRepository();
        smsService = new FakeSmsService();
        activityLogService = new FakeActivityLogService();
        smsResendService = new SmsResendService(smsLogRepository, smsService, activityLogService,
                fixedClock("2026-05-05T10:00:00Z"));
        AuthContext.setCurrentUser(new AuthenticatedUser(7L, "admin", "System Administrator", 1L,
                "Admin", List.of("sms.resend")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void userWithPermissionCanResendSmsWithinSevenDays() {
        SmsResult result = smsResendService.resendSms(validRequest());

        assertTrue(result.isSuccess());
        assertEquals(1, smsLogRepository.insertCount);
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
        assertEquals(0, smsLogRepository.insertCount);
        assertEquals(ActivityLogService.SMS_RESEND_BLOCKED_PERMISSION, activityLogService.action);
    }

    @Test
    void rejectResendAfterSevenDays() {
        smsLogRepository.original.setCreatedAt(LocalDateTime.of(2026, 5, 1, 10, 0));
        smsResendService = new SmsResendService(smsLogRepository, smsService, activityLogService,
                fixedClock("2026-05-08T10:00:01Z"));

        SmsResendService.SmsResendException exception = assertThrows(
                SmsResendService.SmsResendException.class,
                () -> smsResendService.resendSms(validRequest()));

        assertEquals("SMS resend is allowed only within 7 days from the original SMS.", exception.getMessage());
        assertEquals(0, smsLogRepository.insertCount);
        assertEquals(ActivityLogService.SMS_RESEND_BLOCKED_EXPIRED, activityLogService.action);
    }

    @Test
    void resendCreatesNewSmsLogRecord() {
        smsResendService.resendSms(validRequest());

        assertEquals(1, smsLogRepository.insertCount);
        assertEquals(500L, smsLogRepository.newSmsLogId);
        assertEquals(SmsLogRepository.SmsStatus.SUCCESS.name(), smsLogRepository.insertedLog.getStatus());
    }

    @Test
    void originalSmsLogRecordIsNotModified() {
        String originalMessage = smsLogRepository.original.getMessage();
        String originalStatus = smsLogRepository.original.getStatus();

        smsResendService.resendSms(validRequest());

        assertEquals(originalMessage, smsLogRepository.original.getMessage());
        assertEquals(originalStatus, smsLogRepository.original.getStatus());
        assertFalse(smsLogRepository.original == smsLogRepository.insertedLog);
    }

    @Test
    void failedResendCreatesFailedLog() {
        smsService.result = new SmsResult(false, "Gateway unavailable.", MockSmsService.PROVIDER, null);

        SmsResult result = smsResendService.resendSms(validRequest());

        assertFalse(result.isSuccess());
        assertEquals(SmsLogRepository.SmsStatus.FAILED.name(), smsLogRepository.insertedLog.getStatus());
        assertEquals("Gateway unavailable.", smsLogRepository.insertedLog.getErrorMessage());
    }

    @Test
    void resendReasonMaxLengthValidation() {
        SmsResendRequest request = validRequest();
        request.setResendReason("x".repeat(256));

        SmsResendService.SmsResendException exception = assertThrows(
                SmsResendService.SmsResendException.class,
                () -> smsResendService.resendSms(request));

        assertEquals("Resend reason cannot exceed 255 characters.", exception.getMessage());
        assertEquals(0, smsLogRepository.insertCount);
    }

    @Test
    void resendReasonIsRequired() {
        SmsResendRequest request = validRequest();
        request.setResendReason(" ");

        SmsResendService.SmsResendException exception = assertThrows(
                SmsResendService.SmsResendException.class,
                () -> smsResendService.resendSms(request));

        assertEquals("Resend reason is required.", exception.getMessage());
        assertEquals(0, smsLogRepository.insertCount);
    }

    @Test
    void resendUsesSameMobileNumberAndSameMessage() {
        smsResendService.resendSms(validRequest());

        assertEquals("+94712345678", smsService.mobileNumber);
        assertEquals("Receipt REC26000001 received.", smsService.message);
        assertEquals("+94712345678", smsLogRepository.insertedLog.getMobileNumber());
        assertEquals("Receipt REC26000001 received.", smsLogRepository.insertedLog.getMessage());
    }

    @Test
    void activityLogCreatedOnResendSuccess() {
        smsResendService.resendSms(validRequest());

        assertEquals(ActivityLogService.SMS_RESENT_SUCCESS, activityLogService.action);
        assertEquals(100L, activityLogService.originalSmsLogId);
        assertEquals(500L, activityLogService.newSmsLogId);
    }

    @Test
    void activityLogCreatedOnResendFailure() {
        smsService.result = new SmsResult(false, "Gateway unavailable.", MockSmsService.PROVIDER, null);

        smsResendService.resendSms(validRequest());

        assertEquals(ActivityLogService.SMS_RESENT_FAILED, activityLogService.action);
        assertEquals(100L, activityLogService.originalSmsLogId);
        assertEquals(500L, activityLogService.newSmsLogId);
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
        private int insertCount;
        private long originalSmsLogId;
        private long resentByUserId;
        private long newSmsLogId;
        private SmsLogDto insertedLog;

        private FakeSmsLogRepository() {
            super((DataSource) null);
        }

        @Override
        public Optional<SmsLogDto> findByIdForResend(long smsLogId) {
            return smsLogId == 100L ? Optional.of(original) : Optional.empty();
        }

        @Override
        public long insertResendSmsLog(SmsLogDto newLog, long originalSmsLogId, long resentByUserId,
                                       String resendReason) {
            insertCount++;
            this.originalSmsLogId = originalSmsLogId;
            this.resentByUserId = resentByUserId;
            this.insertedLog = newLog;
            newSmsLogId = 500L;
            return newSmsLogId;
        }

        private static SmsLogDto originalLog() {
            SmsLogDto log = new SmsLogDto();
            log.setId(100L);
            log.setReceiptId(20L);
            log.setChurchId(10L);
            log.setMobileNumber("+94712345678");
            log.setMessage("Receipt REC26000001 received.");
            log.setProvider(MockSmsService.PROVIDER);
            log.setStatus(SmsLogRepository.SmsStatus.FAILED.name());
            log.setCreatedAt(LocalDateTime.of(2026, 5, 1, 10, 0));
            return log;
        }
    }

    private static class FakeSmsService implements SmsService {
        private SmsResult result = new SmsResult(true, "SMS sent successfully.", MockSmsService.PROVIDER,
                LocalDateTime.of(2026, 5, 5, 10, 0));
        private String mobileNumber;
        private String message;

        @Override
        public SmsResult sendSms(String mobileNumber, String message) {
            this.mobileNumber = mobileNumber;
            this.message = message;
            return result;
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String action;
        private Long originalSmsLogId;
        private Long newSmsLogId;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logSmsResendSuccess(Long userId, Long originalSmsLogId, Long newSmsLogId, Long receiptId,
                                        Long churchId, String mobileNumber) {
            action = SMS_RESENT_SUCCESS;
            this.originalSmsLogId = originalSmsLogId;
            this.newSmsLogId = newSmsLogId;
        }

        @Override
        public void logSmsResendFailed(Long userId, Long originalSmsLogId, Long newSmsLogId, Long receiptId,
                                       Long churchId, String mobileNumber) {
            action = SMS_RESENT_FAILED;
            this.originalSmsLogId = originalSmsLogId;
            this.newSmsLogId = newSmsLogId;
        }

        @Override
        public void logSmsResendBlockedExpired(Long userId, Long originalSmsLogId) {
            action = SMS_RESEND_BLOCKED_EXPIRED;
            this.originalSmsLogId = originalSmsLogId;
        }

        @Override
        public void logSmsResendBlockedPermission(Long userId, Long originalSmsLogId) {
            action = SMS_RESEND_BLOCKED_PERMISSION;
            this.originalSmsLogId = originalSmsLogId;
        }
    }
}
