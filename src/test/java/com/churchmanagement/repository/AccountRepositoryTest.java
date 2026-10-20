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
    void activeChildrenCheck() {
        RecordingDataSource dataSource = new RecordingDataSource();

        new AccountRepository(dataSource).hasActiveChildren(3L);

        assertTrue(dataSource.lastSql().contains("parent_id = ? AND status = 'ACTIVE'"));
    }
}
