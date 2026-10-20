package com.churchmanagement.entity;

import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** A journal entry with its lines. {@code entryNo} is null until the entry is posted. */
public record JournalEntry(Long id, String entryNo, LocalDate entryDate, int fiscalYear, String narration,
                           JournalSourceType sourceType, Long sourceId, JournalStatus status, Long reversalOfId,
                           Long preparedBy, LocalDateTime preparedAt, Long postedBy, LocalDateTime postedAt,
                           List<JournalLine> lines) {
    public JournalEntry {
        lines = List.copyOf(lines);
    }

    public boolean isPosted() {
        return status == JournalStatus.POSTED;
    }

    public BigDecimal totalDebit() {
        return lines.stream().map(JournalLine::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalCredit() {
        return lines.stream().map(JournalLine::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
