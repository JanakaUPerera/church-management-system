package com.churchmanagement.repository;

import com.churchmanagement.dto.finance.JournalSearchCriteria;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.exception.JournalStateException;
import com.churchmanagement.testsupport.RecordingDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JournalRepositoryTest {
    @Test
    void entryNumbersArePerYearAndPadded() {
        assertEquals("JE-2027-00001", JournalRepository.formatEntryNo(2027, 1));
        assertEquals("JE-2028-12345", JournalRepository.formatEntryNo(2028, 12345));
    }

    @Test
    void postLocksTheEntryAndRollsBackWhenItIsMissing() {
        RecordingDataSource dataSource = new RecordingDataSource();
        JournalRepository repository = new JournalRepository(dataSource);

        JournalStateException exception = assertThrows(JournalStateException.class,
                () -> repository.post(9L, 1L, LocalDateTime.of(2027, 1, 5, 10, 0)));

        assertEquals("Only draft entries can be posted.", exception.getMessage());
        assertTrue(dataSource.sqls().getFirst().contains("FOR UPDATE"));
        assertTrue(dataSource.rolledBack());
    }

    @Test
    void searchAppliesEveryFilter() {
        RecordingDataSource dataSource = new RecordingDataSource();

        new JournalRepository(dataSource).search(new JournalSearchCriteria(LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 31), JournalStatus.POSTED, JournalSourceType.JOURNAL, "rent"));

        String sql = dataSource.lastSql();
        assertTrue(sql.contains("je.entry_date >= ?"));
        assertTrue(sql.contains("je.entry_date <= ?"));
        assertTrue(sql.contains("je.status = ?"));
        assertTrue(sql.contains("je.source_type = ?"));
        assertTrue(sql.contains("je.narration LIKE ?"));
        assertEquals("POSTED", dataSource.lastValues().get(2));
        assertEquals("JOURNAL", dataSource.lastValues().get(3));
        assertEquals("%rent%", dataSource.lastValues().get(4));
    }

    @Test
    void ledgerReadsPostedLinesOnly() {
        RecordingDataSource dataSource = new RecordingDataSource();

        new JournalRepository(dataSource).ledgerLines(4L, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));

        assertTrue(dataSource.lastSql().contains("je.status = 'POSTED'"));
        assertEquals(4L, dataSource.lastValues().getFirst());
    }

    @Test
    void openingBalanceBeforeDateIsZeroWithoutRows() {
        RecordingDataSource dataSource = new RecordingDataSource();

        BigDecimal net = new JournalRepository(dataSource).netDebitBefore(4L, LocalDate.of(2027, 2, 1));

        assertEquals(0, BigDecimal.ZERO.compareTo(net));
        assertTrue(dataSource.lastSql().contains("je.entry_date < ?"));
    }
}
