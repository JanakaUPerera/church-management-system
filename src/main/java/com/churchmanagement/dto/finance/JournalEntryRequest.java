package com.churchmanagement.dto.finance;

import com.churchmanagement.enums.JournalSourceType;

import java.time.LocalDate;
import java.util.List;

/** A draft to create ({@code id == null}) or update. */
public record JournalEntryRequest(Long id, JournalSourceType sourceType, LocalDate entryDate, String narration,
                                  List<JournalLineRequest> lines) {
    public JournalEntryRequest {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
