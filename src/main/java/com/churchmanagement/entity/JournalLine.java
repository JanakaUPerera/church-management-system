package com.churchmanagement.entity;

import java.math.BigDecimal;

/** One debit or credit line. Exactly one of debit / credit is greater than zero. */
public record JournalLine(Long id, int lineNo, long accountId, BigDecimal debit, BigDecimal credit,
                          Long categoryId, Long churchId, String memo) {
}
