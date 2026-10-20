package com.churchmanagement.dto.finance;

import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record JournalEntrySummary(long id, String entryNo, LocalDate entryDate, String narration,
                                  JournalSourceType sourceType, JournalStatus status, BigDecimal total,
                                  String preparedByName, String postedByName) {
}
