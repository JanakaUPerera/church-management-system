package com.churchmanagement.dto.finance;

import com.churchmanagement.entity.Account;

import java.math.BigDecimal;
import java.util.List;

/** Posted movements on one account with running balances in the account's normal direction. */
public record AccountLedger(Account account, BigDecimal openingBalance, List<Row> rows, BigDecimal closingBalance) {
    public record Row(LedgerLine line, BigDecimal balance) {
    }
}
