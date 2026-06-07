package com.churchmanagement.service;

import com.churchmanagement.entity.ReceiptSequence;
import com.churchmanagement.exception.ReceiptSequenceLimitExceededException;
import com.churchmanagement.repository.ReceiptSequenceRepository;
import com.churchmanagement.util.ReceiptNumberFormatter;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReceiptNumberGeneratorServiceTest {
    private final FakeReceiptSequenceRepository receiptSequenceRepository = new FakeReceiptSequenceRepository();
    private final FakeActivityLogService activityLogService = new FakeActivityLogService();
    private final ReceiptNumberFormatter formatter = new ReceiptNumberFormatter();

    @Test
    void firstReceiptOfYear() {
        ReceiptNumberGeneratorService service = serviceForYear(2026);

        String receiptNumber = service.generateReceiptNumber();

        assertEquals("REC26000001", receiptNumber);
        assertEquals(1L, receiptSequenceRepository.sequenceForYear(2026).getLastNumber());
    }

    @Test
    void secondReceiptOfYear() {
        ReceiptNumberGeneratorService service = serviceForYear(2026);

        service.generateReceiptNumber();
        String receiptNumber = service.generateReceiptNumber();

        assertEquals("REC26000002", receiptNumber);
    }

    @Test
    void sequenceIncrementsCorrectly() {
        ReceiptNumberGeneratorService service = serviceForYear(2026);

        for (int index = 1; index <= 10; index++) {
            assertEquals("REC26" + String.format("%06d", index), service.generateReceiptNumber());
        }

        assertEquals(10L, receiptSequenceRepository.sequenceForYear(2026).getLastNumber());
    }

    @Test
    void yearResetWorks() {
        serviceForYear(2026).generateReceiptNumber();

        String nextYearReceiptNumber = serviceForYear(2027).generateReceiptNumber();

        assertEquals("REC27000001", nextYearReceiptNumber);
        assertEquals(1L, receiptSequenceRepository.sequenceForYear(2026).getLastNumber());
        assertEquals(1L, receiptSequenceRepository.sequenceForYear(2027).getLastNumber());
    }

    @Test
    void formatIsCorrect() {
        assertEquals("REC26000001", formatter.format(2026, 1));
        assertEquals("REC26000025", formatter.format(2026, 25));
    }

    @Test
    void formatUsesConfiguredPrefixAndPadding() {
        ReceiptNumberFormatter customFormatter = new ReceiptNumberFormatter("DON", 4);

        assertEquals("DON260001", customFormatter.format(2026, 1));
        assertEquals(9_999L, customFormatter.maxSequence());
    }

    @Test
    void sequenceLimitExceptionWorks() {
        ReceiptSequence sequence = receiptSequenceRepository.createYear(2026);
        receiptSequenceRepository.updateLastNumber(sequence.getId(), ReceiptNumberFormatter.MAX_SEQUENCE);

        ReceiptNumberGeneratorService service = serviceForYear(2026);

        assertThrows(ReceiptSequenceLimitExceededException.class, service::generateReceiptNumber);
        assertEquals(ReceiptNumberFormatter.MAX_SEQUENCE, receiptSequenceRepository.sequenceForYear(2026).getLastNumber());
    }

    @Test
    void activityLogStoresGeneratedReceiptNumberDetails() {
        ReceiptNumberGeneratorService service = serviceForYear(2026);

        service.generateReceiptNumber();

        assertEquals(ActivityLogService.RECEIPT_NUMBER_GENERATED, activityLogService.action);
        assertEquals(2026, activityLogService.year);
        assertEquals(1L, activityLogService.sequence);
        assertEquals("REC26000001", activityLogService.receiptNumber);
    }

    private ReceiptNumberGeneratorService serviceForYear(int year) {
        return new ReceiptNumberGeneratorService(
                receiptSequenceRepository,
                activityLogService,
                formatter,
                Clock.fixed(Instant.parse(year + "-05-29T00:00:00Z"), ZoneId.of("UTC"))
        );
    }

    private static class FakeReceiptSequenceRepository extends ReceiptSequenceRepository {
        private final Map<Integer, ReceiptSequence> sequencesByYear = new HashMap<>();
        private final Map<Long, ReceiptSequence> sequencesById = new HashMap<>();
        private long nextId = 1L;

        private FakeReceiptSequenceRepository() {
            super((DataSource) null);
        }

        @Override
        public ReceiptSequence createYear(int year) {
            ReceiptSequence sequence = new ReceiptSequence(nextId++, year, 0L, LocalDateTime.now(), null);
            sequencesByYear.put(year, sequence);
            sequencesById.put(sequence.getId(), sequence);
            return sequence;
        }

        @Override
        public ReceiptSequence lockByYear(int year) {
            return sequencesByYear.get(year);
        }

        @Override
        public ReceiptSequence updateLastNumber(long id, long newNumber) {
            ReceiptSequence sequence = sequencesById.get(id);
            sequence.setLastNumber(newNumber);
            sequence.setUpdatedAt(LocalDateTime.now());
            return sequence;
        }

        private ReceiptSequence sequenceForYear(int year) {
            return sequencesByYear.get(year);
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String action;
        private int year;
        private long sequence;
        private String receiptNumber;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logReceiptNumberGenerated(Long userId, int year, long sequence, String receiptNumber) {
            this.action = RECEIPT_NUMBER_GENERATED;
            this.year = year;
            this.sequence = sequence;
            this.receiptNumber = receiptNumber;
        }
    }
}
