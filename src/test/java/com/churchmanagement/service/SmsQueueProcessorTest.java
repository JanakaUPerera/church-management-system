package com.churchmanagement.service;

import com.churchmanagement.dto.SmsLogDto;
import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.enums.SmsSendStatus;
import com.churchmanagement.repository.SmsLogRepository;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsQueueProcessorTest {
    @Test
    void secondaryMachineNeverProcesses() {
        FakeSmsLogRepository repository = new FakeSmsLogRepository();
        repository.rows.add(queuedRow(1L, LocalDateTime.of(2026, 6, 6, 10, 0)));
        SmsQueueProcessor processor = processor(repository, new FakeSmsServiceFactory(true), () -> false);

        processor.start();
        processor.cancel();

        assertEquals(SmsSendStatus.QUEUED.name(), repository.rows.get(0).getStatus());
    }

    @Test
    void drainsAllQueuedRowsOldestFirstInOneTick() {
        FakeSmsLogRepository repository = new FakeSmsLogRepository();
        repository.rows.add(queuedRow(1L, LocalDateTime.of(2026, 6, 6, 10, 2)));
        repository.rows.add(queuedRow(2L, LocalDateTime.of(2026, 6, 6, 10, 0)));
        repository.rows.add(queuedRow(3L, LocalDateTime.of(2026, 6, 6, 10, 1)));
        SmsQueueProcessor processor = processor(repository, new FakeSmsServiceFactory(true), () -> true);

        processor.processTick();

        assertEquals(List.of(2L, 3L, 1L), repository.processedOrder);
        assertTrue(repository.rows.stream().allMatch(row -> "SENT".equals(row.getStatus())));
    }

    @Test
    void staleSendingRowIsReclaimedAndReprocessed() {
        FakeSmsLogRepository repository = new FakeSmsLogRepository();
        SmsLogDto stale = queuedRow(1L, LocalDateTime.of(2026, 6, 6, 9, 0));
        stale.setStatus(SmsSendStatus.SENDING.name());
        stale.setLastAttemptAt(LocalDateTime.of(2026, 6, 6, 9, 55));
        repository.rows.add(stale);
        SmsQueueProcessor processor = processor(repository, new FakeSmsServiceFactory(true), () -> true);

        processor.processTick();

        assertEquals("SENT", repository.rows.get(0).getStatus());
    }

    @Test
    void freshSendingRowIsLeftAloneUntilStale() {
        FakeSmsLogRepository repository = new FakeSmsLogRepository();
        SmsLogDto fresh = queuedRow(1L, LocalDateTime.of(2026, 6, 6, 9, 59));
        fresh.setStatus(SmsSendStatus.SENDING.name());
        fresh.setLastAttemptAt(LocalDateTime.of(2026, 6, 6, 9, 59));
        repository.rows.add(fresh);
        SmsQueueProcessor processor = processor(repository, new FakeSmsServiceFactory(true), () -> true);

        processor.processTick();

        assertEquals("SENDING", repository.rows.get(0).getStatus());
    }

    @Test
    void failedSendRecordsErrorAndDoesNotBlockRemainingRows() {
        FakeSmsLogRepository repository = new FakeSmsLogRepository();
        repository.rows.add(queuedRow(1L, LocalDateTime.of(2026, 6, 6, 10, 0)));
        repository.rows.add(queuedRow(2L, LocalDateTime.of(2026, 6, 6, 10, 1)));
        SmsQueueProcessor processor = processor(repository, new FakeSmsServiceFactory(false), () -> true);

        processor.processTick();

        assertTrue(repository.rows.stream().allMatch(row -> "FAILED".equals(row.getStatus())));
        assertEquals("Gateway rejected message.", repository.rows.get(0).getErrorMessage());
    }

    @Test
    void resendRowLogsThroughResendActivityMethods() {
        FakeSmsLogRepository repository = new FakeSmsLogRepository();
        SmsLogDto resendRow = queuedRow(1L, LocalDateTime.of(2026, 6, 6, 10, 0));
        resendRow.setResendOfSmsLogId(50L);
        resendRow.setQueuedByUserId(7L);
        repository.rows.add(resendRow);
        FakeActivityLogService activityLogService = new FakeActivityLogService();
        SmsQueueProcessor processor = new SmsQueueProcessor(repository, new FakeSmsServiceFactory(true),
                activityLogService, new FakeSystemConfigurationCache(Map.of("sms.retry.max.attempts", "3")),
                () -> true, fixedClock());

        processor.processTick();

        assertEquals(ActivityLogService.SMS_RESENT_SUCCESS, activityLogService.action);
    }

    @Test
    void originalRowLogsThroughOriginalActivityMethods() {
        FakeSmsLogRepository repository = new FakeSmsLogRepository();
        repository.rows.add(queuedRow(1L, LocalDateTime.of(2026, 6, 6, 10, 0)));
        FakeActivityLogService activityLogService = new FakeActivityLogService();
        SmsQueueProcessor processor = new SmsQueueProcessor(repository, new FakeSmsServiceFactory(true),
                activityLogService, new FakeSystemConfigurationCache(Map.of("sms.retry.max.attempts", "3")),
                () -> true, fixedClock());

        processor.processTick();

        assertEquals(ActivityLogService.SMS_SENT_ACCEPTED_BY_MODEM, activityLogService.action);
    }

    private SmsQueueProcessor processor(FakeSmsLogRepository repository, FakeSmsServiceFactory factory,
                                        BooleanSupplier isPrimaryMachine) {
        return new SmsQueueProcessor(repository, factory, new FakeActivityLogService(),
                new FakeSystemConfigurationCache(Map.of("sms.retry.max.attempts", "3")), isPrimaryMachine,
                fixedClock());
    }

    private SmsLogDto queuedRow(long id, LocalDateTime createdAt) {
        SmsLogDto row = new SmsLogDto();
        row.setId(id);
        row.setReceiptId(20L);
        row.setChurchId(10L);
        row.setMobileNumber("+94712345678");
        row.setMessage("Receipt received.");
        row.setStatus(SmsSendStatus.QUEUED.name());
        row.setAttemptCount(0);
        row.setCreatedAt(createdAt);
        return row;
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-06-06T10:00:00Z"), ZoneId.of("UTC"));
    }

    private static class FakeSmsLogRepository extends SmsLogRepository {
        private final List<SmsLogDto> rows = new ArrayList<>();
        private final List<Long> processedOrder = new ArrayList<>();

        private FakeSmsLogRepository() {
            super((DataSource) null);
        }

        @Override
        public int reclaimStaleSending(LocalDateTime staleBefore) {
            int count = 0;
            for (SmsLogDto row : rows) {
                if (SmsSendStatus.SENDING.name().equals(row.getStatus())
                        && row.getLastAttemptAt() != null && row.getLastAttemptAt().isBefore(staleBefore)) {
                    row.setStatus(SmsSendStatus.QUEUED.name());
                    count++;
                }
            }
            return count;
        }

        @Override
        public Optional<SmsLogDto> findOldestQueued() {
            return rows.stream()
                    .filter(row -> SmsSendStatus.QUEUED.name().equals(row.getStatus()))
                    .min(Comparator.comparing(SmsLogDto::getCreatedAt));
        }

        @Override
        public boolean markSending(long id, LocalDateTime attemptAt) {
            for (SmsLogDto row : rows) {
                if (row.getId() == id && SmsSendStatus.QUEUED.name().equals(row.getStatus())) {
                    row.setStatus(SmsSendStatus.SENDING.name());
                    row.setLastAttemptAt(attemptAt);
                    processedOrder.add(id);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void updateSendResult(long id, SmsSendStatus sendStatus, SmsDeliveryStatus deliveryStatus,
                                     String provider, String modemMessageReference, String modemRawResponse,
                                     String errorCode, String errorMessage, int attemptCount,
                                     LocalDateTime lastAttemptAt, LocalDateTime sentAt) {
            for (SmsLogDto row : rows) {
                if (row.getId() == id) {
                    row.setStatus(sendStatus.name());
                    row.setDeliveryStatus(deliveryStatus.name());
                    row.setProvider(provider);
                    row.setModemMessageReference(modemMessageReference);
                    row.setErrorMessage(errorMessage);
                    row.setAttemptCount(attemptCount);
                    row.setLastAttemptAt(lastAttemptAt);
                    row.setSentAt(sentAt);
                }
            }
        }
    }

    private static class FakeSmsServiceFactory extends SmsServiceFactory {
        private final boolean succeed;

        private FakeSmsServiceFactory(boolean succeed) {
            super(null);
            this.succeed = succeed;
        }

        @Override
        public SmsService createRoutingSmsService(int maxAttempts) {
            return (mobileNumber, message) -> succeed
                    ? new SmsResult(true, "SMS sent successfully.", MockSmsService.PROVIDER,
                            LocalDateTime.of(2026, 6, 6, 10, 0))
                    : new SmsResult(false, "Gateway rejected message.", MockSmsService.PROVIDER, null);
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String action;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logSmsSentAcceptedByModem(Long userId, Long receiptId, Long churchId, String mobileNumber,
                                             String provider, String modemReference) {
            action = SMS_SENT_ACCEPTED_BY_MODEM;
        }

        @Override
        public void logSmsSendFailed(Long userId, Long receiptId, Long churchId, String mobileNumber, String reason) {
            action = SMS_SEND_FAILED;
        }

        @Override
        public void logSmsDeliveryStatusUnknown(Long userId, Long receiptId, Long churchId, String mobileNumber) {
        }

        @Override
        public void logSmsResendSuccess(Long userId, Long originalSmsLogId, Long newSmsLogId, Long receiptId,
                                        Long churchId, String mobileNumber) {
            action = SMS_RESENT_SUCCESS;
        }

        @Override
        public void logSmsResendFailed(Long userId, Long originalSmsLogId, Long newSmsLogId, Long receiptId,
                                       Long churchId, String mobileNumber) {
            action = SMS_RESENT_FAILED;
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
