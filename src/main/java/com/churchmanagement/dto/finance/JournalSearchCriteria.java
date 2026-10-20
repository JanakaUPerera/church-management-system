package com.churchmanagement.dto.finance;

import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;

import java.time.LocalDate;

/** Journal list filters; any field may be null. */
public record JournalSearchCriteria(LocalDate dateFrom, LocalDate dateTo, JournalStatus status,
                                    JournalSourceType sourceType, String text) {
}
