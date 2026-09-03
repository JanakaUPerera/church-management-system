package com.churchmanagement.service;

import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.dto.SmsSettings;
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
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public ReceiptSmsNotificationService() {
        this(new ReceiptRepository(), new ChurchRepository(), new SmsSettingsRepository(), new SmsLogRepository(),
                new ActivityLogService(), Clock.systemDefaultZone());
    }

    public ReceiptSmsNotificationService(ReceiptRepository receiptRepository, ChurchRepository churchRepository,
                                         SmsSettingsRepository smsSettingsRepository, SmsLogRepository smsLogRepository,
                                         ActivityLogService activityLogService, Clock clock) {
        this.receiptRepository = receiptRepository;
        this.churchRepository = churchRepository;
        this.smsSettingsRepository = smsSettingsRepository;
        this.smsLogRepository = smsLogRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    /**
     * Queues the receipt-submission SMS for the server machine's {@link SmsQueueProcessor}
     * to send. Does not touch the modem itself — this may run on any machine.
     */
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
            throw new SmsNotificationException("Can't send a SMS without a Mobile number.");
        }

        ReceiptResponseDto receiptDetails = receiptRepository.findReceiptDetailsById(receiptId)
                .orElseThrow(() -> new SmsNotificationException("Receipt details could not be found for SMS notification."));
        String message = buildMessage(receipt, church, receiptDetails.getTotalAmount());
        try {
            smsLogRepository.enqueue(receiptId, church.getId(), mobileNumber, message, userId,
                    LocalDateTime.now(clock));
        } catch (DatabaseException exception) {
            throw new SmsNotificationException("Unable to queue SMS notification.", exception);
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
