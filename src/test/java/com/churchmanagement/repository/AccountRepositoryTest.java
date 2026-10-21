package com.churchmanagement.repository;

import com.churchmanagement.testsupport.RecordingDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountRepositoryTest {
    @Test
    void usageChecksJournalLines() {
        RecordingDataSource dataSource = new RecordingDataSource();

        assertFalse(new AccountRepository(dataSource).isUsed(7L));
        assertTrue(dataSource.lastSql().contains("FROM journal_lines WHERE account_id = ?"));
        assertEquals(7L, dataSource.lastValues().getFirst());
    }

    @Test
    void balancesCountPostedEntriesOnly() {
        RecordingDataSource dataSource = new RecordingDataSource();

        assertTrue(new AccountRepository(dataSource).postedNetDebits().isEmpty());
        assertTrue(dataSource.lastSql().contains("je.status = 'POSTED'"));
    }

    @Test
    void deactivationChecksReadPostedDraftAndOpenYearLines() {
        RecordingDataSource dataSource = new RecordingDataSource();
        AccountRepository repository = new AccountRepository(dataSource);

        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(repository.postedNetDebit(7L)));
        assertTrue(dataSource.lastSql().contains("je.status = 'POSTED'"));
        assertFalse(repository.hasDraftLines(7L));
        assertTrue(dataSource.lastSql().contains("je.status = 'DRAFT'"));
        assertTrue(repository.openYearWithPostedLines(7L).isEmpty());
        assertTrue(dataSource.lastSql().contains("fy.status = 'OPEN'"));
    }

    @Test
    void activeChildrenCheck() {
        RecordingDataSource dataSource = new RecordingDataSource();

        new AccountRepository(dataSource).hasActiveChildren(3L);

        assertTrue(dataSource.lastSql().contains("parent_id = ? AND status = 'ACTIVE'"));
    }
}
