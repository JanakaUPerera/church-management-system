package com.churchmanagement.dto.finance;

import java.math.BigDecimal;

/** A line as typed by the user; null amounts mean zero. */
public record JournalLineRequest(Long accountId, BigDecimal debit, BigDecimal credit, Long categoryId,
                                 Long churchId, String memo) {
    public BigDecimal debitOrZero() {
        return debit == null ? BigDecimal.ZERO : debit;
    }

    public BigDecimal creditOrZero() {
        return credit == null ? BigDecimal.ZERO : credit;
    }
}
