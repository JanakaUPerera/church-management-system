package com.churchmanagement.service;

import com.churchmanagement.config.PrimaryMachine;
import com.churchmanagement.dto.SmsLogDto;
import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.enums.SmsSendStatus;
import com.churchmanagement.repository.SmsLogRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Drains {@code sms_logs} rows queued by any machine, sending each through the modem
 * one at a time in request order. Runs only on the designated primary/server machine
 * (see {@link PrimaryMachine}) — the only one with the physical SIM modem attached.
 */
public class SmsQueueProcessor {
    static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    static final Duration STALE_SENDING_THRESHOLD = Duration.ofMinutes(2);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final SmsLogRepository smsLogRepository;
    private final SmsServiceFactory smsServiceFactory;
    private final ActivityLogService activityLogService;
    private final SystemConfigurationCache configurationCache;
    private final BooleanSupplier isPrimaryMachine;
    private final Clock clock;
    private final ScheduledExecutorService executorService;
    private ScheduledFuture<?> scheduledTask;

    public SmsQueueProcessor() {
        this(new SmsLogRepository(), new SmsServiceFactory(), new ActivityLogService(),
                SystemConfigurationCache.getInstance(), PrimaryMachine::isPrimary, Clock.systemDefaultZone());
    }

    public static SmsQueueProcessor getInstance() {
        return Holder.INSTANCE;
    }

    SmsQueueProcessor(SmsLogRepository smsLogRepository, SmsServiceFactory smsServiceFactory,
                      ActivityLogService activityLogService, SystemConfigurationCache configurationCache,
                      BooleanSupplier isPrimaryMachine, Clock clock) {
        this.smsLogRepository = smsLogRepository;
        this.smsServiceFactory = smsServiceFactory;
        this.activityLogService = activityLogService;
        this.configurationCache = configurationCache;
        this.isPrimaryMachine = isPrimaryMachine;
        this.clock = clock;
        this.executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sms-queue-processor");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void start() {
        cancel();
        if (!isPrimaryMachine.getAsBoolean()) {
            // Client machine — the modem lives on the primary/server machine only,
            // so nothing here should ever try to drain the queue.
            return;
        }
        scheduledTask = executorService.scheduleWithFixedDelay(this::processTick,
                0, POLL_INTERVAL.toSeconds(), TimeUnit.SECONDS);
    }

    public synchronized void cancel() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
    }

    void processTick() {
        try {
            smsLogRepository.reclaimStaleSending(LocalDateTime.now(clock).minus(STALE_SENDING_THRESHOLD));
            Optional<SmsLogDto> next = smsLogRepository.findOldestQueued();
            while (next.isPresent()) {
                if (!processOne(next.get())) {
                    return;
                }
                next = smsLogRepository.findOldestQueued();
            }
        } catch (RuntimeException exception) {
            System.err.println("SMS queue processing failed: " + exception.getMessage());
        }
    }

    private boolean processOne(SmsLogDto row) {
        LocalDateTime attemptAt = LocalDateTime.now(clock);
        if (!smsLogRepository.markSending(row.getId(), attemptAt)) {
            // Row changed between findOldestQueued() and this claim attempt — stop this
            // tick rather than risk looping on the same unclaimable row.
            return false;
        }

        int priorAttemptCount = row.getAttemptCount();
        try {
            int remainingAttempts = Math.max(1, configuredMaxAttempts() - priorAttemptCount);
            SmsResult result = smsServiceFactory.createRoutingSmsService(remainingAttempts)
                    .sendSms(row.getMobileNumber(), row.getMessage());
            recordResult(row, result, priorAttemptCount);
        } catch (RuntimeException exception) {
            recordUnexpectedFailure(row, priorAttemptCount, exception);
        }
        return true;
    }

    private void recordResult(SmsLogDto row, SmsResult result, int priorAttemptCount) {
        LocalDateTime finishedAt = LocalDateTime.now(clock);
        int finalAttemptCount = priorAttemptCount + result.getAttemptCount();
        smsLogRepository.updateSendResult(row.getId(),
                result.isSuccess() ? result.getSendStatus() : SmsSendStatus.FAILED,
                result.isSuccess() ? result.getDeliveryStatus() : SmsDeliveryStatus.FAILED,
                result.getProvider(), result.getModemMessageReference(), result.getModemRawResponse(),
                result.getErrorCode(), result.isSuccess() ? null : result.getErrorMessage(),
                finalAttemptCount, finishedAt, result.isSuccess() ? result.getSentAt() : null);
        logActivity(row, result.isSuccess(), result.getProvider(), result.getModemMessageReference(),
                result.getDeliveryStatus(), result.getErrorMessage());
    }

    private void recordUnexpectedFailure(SmsLogDto row, int priorAttemptCount, RuntimeException exception) {
        LocalDateTime finishedAt = LocalDateTime.now(clock);
        smsLogRepository.updateSendResult(row.getId(), SmsSendStatus.FAILED, SmsDeliveryStatus.FAILED,
                null, null, null, null, exception.getMessage(), priorAttemptCount + 1, finishedAt, null);
        logActivity(row, false, null, null, null, exception.getMessage());
    }

    private void logActivity(SmsLogDto row, boolean success, String provider, String modemMessageReference,
                             SmsDeliveryStatus deliveryStatus, String errorMessage) {
        Long userId = row.getQueuedByUserId();
        boolean isResend = row.getResendOfSmsLogId() != null;
        if (isResend) {
            if (success) {
                activityLogService.logSmsResendSuccess(userId, row.getResendOfSmsLogId(), row.getId(),
                        row.getReceiptId(), row.getChurchId(), row.getMobileNumber());
            } else {
                activityLogService.logSmsResendFailed(userId, row.getResendOfSmsLogId(), row.getId(),
                        row.getReceiptId(), row.getChurchId(), row.getMobileNumber());
            }
            return;
        }
        if (success) {
            activityLogService.logSmsSentAcceptedByModem(userId, row.getReceiptId(), row.getChurchId(),
                    row.getMobileNumber(), provider, modemMessageReference);
            if (deliveryStatus == SmsDeliveryStatus.NOT_SUPPORTED || deliveryStatus == SmsDeliveryStatus.UNKNOWN) {
                activityLogService.logSmsDeliveryStatusUnknown(userId, row.getReceiptId(), row.getChurchId(),
                        row.getMobileNumber());
            }
        } else {
            activityLogService.logSmsSendFailed(userId, row.getReceiptId(), row.getChurchId(),
                    row.getMobileNumber(), errorMessage);
        }
    }

    private int configuredMaxAttempts() {
        String value = configurationCache.getString("sms.retry.max.attempts");
        if (value == null || value.isBlank()) {
            return DEFAULT_MAX_ATTEMPTS;
        }
        try {
            return Math.max(1, Integer.parseInt(value.strip()));
        } catch (NumberFormatException exception) {
            return DEFAULT_MAX_ATTEMPTS;
        }
    }

    private static class Holder {
        private static final SmsQueueProcessor INSTANCE = new SmsQueueProcessor();
    }
}
