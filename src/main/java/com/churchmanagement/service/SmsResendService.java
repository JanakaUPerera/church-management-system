package com.churchmanagement.service;

import com.churchmanagement.dto.SmsLogDto;
import com.churchmanagement.dto.SmsResendRequest;
import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.entity.Church;
import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.enums.SmsSendStatus;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

import java.time.Clock;
import java.time.LocalDateTime;

public class SmsResendService {
    private static final int RESEND_WINDOW_DAYS = 7;
    private static final int MAX_REASON_LENGTH = 255;

    private final SmsLogRepository smsLogRepository;
    private final ChurchRepository churchRepository;
    private final SmsService smsService;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public SmsResendService() {
        this(new SmsLogRepository(), new ChurchRepository(), new SmsServiceFactory().createRoutingSmsService(),
                new ActivityLogService(), Clock.systemDefaultZone());
    }

    public SmsResendService(SmsLogRepository smsLogRepository, SmsService smsService,
                            ActivityLogService activityLogService, Clock clock) {
        this(smsLogRepository, new ChurchRepository(), smsService, activityLogService, clock);
    }

    public SmsResendService(SmsLogRepository smsLogRepository, ChurchRepository churchRepository, SmsService smsService,
                            ActivityLogService activityLogService, Clock clock) {
        this.smsLogRepository = smsLogRepository;
        this.churchRepository = churchRepository;
        this.smsService = smsService;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public SmsResult resendSms(SmsResendRequest request) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new SmsResendException("Please sign in to resend SMS."));
        Long smsLogId = request == null ? null : request.getSmsLogId();
        if (!new PermissionGuard(currentUser).can("sms.resend")) {
            activityLogService.logSmsResendBlockedPermission(currentUser.getUserId(), smsLogId);
            throw new SmsResendException("You do not have permission to resend SMS.");
        }
        if (smsLogId == null) {
            throw new SmsResendException("SMS log is required.");
        }

        String resendReason = normalizeReason(request.getResendReason());
        SmsLogDto original = smsLogRepository.findByIdForResend(smsLogId)
                .orElseThrow(() -> new SmsResendException("Original SMS log could not be found."));
        validateResendWindow(currentUser, original);
        if (original.getMobileNumber() == null || original.getMobileNumber().isBlank()) {
            throw new SmsResendException("Original SMS mobile number is missing.");
        }
        if (original.getMessage() == null || original.getMessage().isBlank()) {
            throw new SmsResendException("Original SMS message is missing.");
        }
        String resendMobileNumber = resolveCurrentMobileNumber(original);

        SmsResult result;
        try {
            result = smsService.sendSms(resendMobileNumber, original.getMessage());
        } catch (RuntimeException exception) {
            result = new SmsResult(false, exception.getMessage(), MockSmsService.PROVIDER, null);
        }

        SmsLogDto resendLog = buildResendLog(original, resendMobileNumber, result);
        long newSmsLogId = smsLogRepository.insertResendSmsLog(
                resendLog, original.getId(), currentUser.getUserId(), resendReason);
        if (result.isSuccess()) {
            activityLogService.logSmsResendSuccess(currentUser.getUserId(), original.getId(), newSmsLogId,
                    original.getReceiptId(), original.getChurchId(), resendMobileNumber);
        } else {
            activityLogService.logSmsResendFailed(currentUser.getUserId(), original.getId(), newSmsLogId,
                    original.getReceiptId(), original.getChurchId(), resendMobileNumber);
        }
        return result;
    }

    private SmsLogDto buildResendLog(SmsLogDto original, String resendMobileNumber, SmsResult result) {
        SmsLogDto log = new SmsLogDto();
        log.setReceiptId(original.getReceiptId());
        log.setChurchId(original.getChurchId());
        log.setMobileNumber(resendMobileNumber);
        log.setMessage(original.getMessage());
        log.setProvider(result.getProvider());
        log.setSendStatus(result.isSuccess() ? result.getSendStatus().name() : SmsSendStatus.FAILED.name());
        log.setDeliveryStatus(result.getDeliveryStatus() == null
                ? SmsDeliveryStatus.UNKNOWN.name()
                : result.getDeliveryStatus().name());
        log.setModemMessageReference(result.getModemMessageReference());
        log.setModemRawResponse(result.getModemRawResponse());
        log.setErrorCode(result.getErrorCode());
        log.setErrorMessage(result.isSuccess() ? null : result.getErrorMessage());
        log.setAttemptCount(Math.max(1, original.getAttemptCount()) + result.getAttemptCount());
        log.setLastAttemptAt(LocalDateTime.now(clock));
        log.setSentAt(result.isSuccess() ? result.getSentAt() : null);
        log.setCreatedAt(LocalDateTime.now(clock));
        return log;
    }

    private String resolveCurrentMobileNumber(SmsLogDto original) {
        if (original.getChurchId() == null) {
            return original.getMobileNumber();
        }
        Church church = churchRepository.findById(original.getChurchId())
                .orElseThrow(() -> new SmsResendException("Church could not be found for SMS resend."));
        String currentMobileNumber = church.getSmsMobileNumber();
        if (currentMobileNumber == null || currentMobileNumber.isBlank()) {
            throw new SmsResendException("Church SMS mobile number is missing.");
        }
        return currentMobileNumber;
    }

    private void validateResendWindow(AuthenticatedUser currentUser, SmsLogDto original) {
        if (original.getCreatedAt() == null
                || LocalDateTime.now(clock).isAfter(original.getCreatedAt().plusDays(RESEND_WINDOW_DAYS))) {
            activityLogService.logSmsResendBlockedExpired(currentUser.getUserId(), original.getId());
            throw new SmsResendException("SMS resend is allowed only within 7 days from the original SMS.");
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new SmsResendException("Resend reason is required.");
        }
        String normalized = reason.strip();
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw new SmsResendException("Resend reason cannot exceed 255 characters.");
        }
        return normalized;
    }

    public static class SmsResendException extends RuntimeException {
        public SmsResendException(String message) {
            super(message);
        }
    }
}
