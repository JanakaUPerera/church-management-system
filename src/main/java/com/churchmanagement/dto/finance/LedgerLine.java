package com.churchmanagement.dto.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A posted line on one account, for the Account Ledger. */
public record LedgerLine(long entryId, String entryNo, LocalDate entryDate, String narration, String memo,
                         String categoryCode, String churchCode, BigDecimal debit, BigDecimal credit) {
}
