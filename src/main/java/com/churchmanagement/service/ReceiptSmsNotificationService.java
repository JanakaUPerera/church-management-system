package com.churchmanagement.service;

import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.enums.SmsSendStatus;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Receipt;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.repository.ReceiptRepository;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.repository.SmsSettingsRepository;
import com.churchmanagement.security.AuthContext;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

public class ReceiptSmsNotificationService {
    private final ReceiptRepository receiptRepository;
    private final ChurchRepository churchRepository;
    private final SmsSettingsRepository smsSettingsRepository;
    private final SmsLogRepository smsLogRepository;
    private final SmsService smsService;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public ReceiptSmsNotificationService() {
        this(new ReceiptRepository(), new ChurchRepository(), new SmsSettingsRepository(), new SmsLogRepository(),
                new SmsServiceFactory().createRoutingSmsService(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public ReceiptSmsNotificationService(ReceiptRepository receiptRepository, ChurchRepository churchRepository,
                                         SmsSettingsRepository smsSettingsRepository, SmsLogRepository smsLogRepository,
                                         SmsService smsService, ActivityLogService activityLogService, Clock clock) {
        this.receiptRepository = receiptRepository;
        this.churchRepository = churchRepository;
        this.smsSettingsRepository = smsSettingsRepository;
        this.smsLogRepository = smsLogRepository;
        this.smsService = smsService;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public void sendReceiptSubmissionSms(long receiptId) {
        Long userId = currentUserId();
        Receipt receipt = receiptRepository.findReceiptById(receiptId)
                .orElseThrow(() -> new SmsNotificationException("Receipt could not be found for SMS notification."));

        SmsSettings settings = smsSettingsRepository.getSettings();
        if (!settings.isSmsEnabled()) {
            activityLogService.logSmsSkipped(userId, receiptId, receipt.getChurchId(), "SMS is disabled.");
            return;
        }

        Church church = churchRepository.findById(receipt.getChurchId())
                .orElseThrow(() -> new SmsNotificationException("Church could not be found for SMS notification."));
        String mobileNumber = church.getSmsMobileNumber();
        if (mobileNumber == null || mobileNumber.isBlank()) {
            activityLogService.logSmsSkipped(userId, receiptId, church.getId(), "Church SMS mobile number is not set.");
            return;
        }

        ReceiptResponseDto receiptDetails = receiptRepository.findReceiptDetailsById(receiptId)
                .orElseThrow(() -> new SmsNotificationException("Receipt details could not be found for SMS notification."));
        String message = buildMessage(receipt, church, receiptDetails.getTotalAmount());
        try {
            SmsResult result = smsService.sendSms(mobileNumber, message);
            LocalDateTime now = LocalDateTime.now(clock);
            if (result.isSuccess()) {
                smsLogRepository.insertSmsLog(receiptId, church.getId(), mobileNumber, message, result.getProvider(),
                        result.getSendStatus(), result.getDeliveryStatus(), result.getModemMessageReference(),
                        result.getModemRawResponse(), null, result.getErrorCode(), null, result.getAttemptCount(), now,
                        result.getSentAt(), now);
                activityLogService.logSmsSentAcceptedByModem(userId, receiptId, church.getId(), mobileNumber,
                        result.getProvider(), result.getModemMessageReference());
                if (result.getDeliveryStatus() == SmsDeliveryStatus.NOT_SUPPORTED
                        || result.getDeliveryStatus() == SmsDeliveryStatus.UNKNOWN) {
                    activityLogService.logSmsDeliveryStatusUnknown(userId, receiptId, church.getId(), mobileNumber);
                }
            } else {
                smsLogRepository.insertSmsLog(receiptId, church.getId(), mobileNumber, message, result.getProvider(),
                        SmsSendStatus.FAILED, SmsDeliveryStatus.FAILED, result.getModemMessageReference(),
                        result.getModemRawResponse(), null, result.getErrorCode(), result.getErrorMessage(),
                        result.getAttemptCount(), now, result.getSentAt(), now);
                activityLogService.logSmsSendFailed(userId, receiptId, church.getId(), mobileNumber,
                        result.getErrorMessage());
            }
        } catch (RuntimeException exception) {
            try {
                smsLogRepository.insertSmsLog(receiptId, church.getId(), mobileNumber, message, MockSmsService.PROVIDER,
                        SmsSendStatus.FAILED, SmsDeliveryStatus.FAILED, null, null, null, null,
                        exception.getMessage(), 1, LocalDateTime.now(clock), null, LocalDateTime.now(clock));
            } catch (DatabaseException logException) {
                System.err.println("SMS failure log insert failed: " + logException.getMessage());
            }
            activityLogService.logSmsSendFailed(userId, receiptId, church.getId(), mobileNumber, exception.getMessage());
        }
    }

    String buildMessage(Receipt receipt, Church church, BigDecimal totalAmount) {
        return "Receipt " + receipt.getReceiptNo()
                + " received for " + church.getChurchCode()
                + " - " + church.getChurchName()
                + ". Week: " + receipt.getWeekStartDate()
                + " to " + receipt.getWeekEndDate()
                + ". Total: Rs. " + formatAmount(totalAmount)
                + ". Thank you.";
    }

    private String formatAmount(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private Long currentUserId() {
        return AuthContext.getCurrentUser().map(user -> user.getUserId()).orElse(null);
    }

    public static class SmsNotificationException extends RuntimeException {
        public SmsNotificationException(String message) {
            super(message);
        }

        public SmsNotificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
