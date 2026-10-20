package com.churchmanagement.dto.finance;

import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.entity.JournalEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Opening balances: an amount per asset / liability account (positive = normal side) and an
 * Accumulated Fund amount per category. {@code entry} is the live OPENING entry, or null.
 */
public record OpeningBalanceForm(LocalDate goLiveDate, List<Account> balanceAccounts,
                                 List<CollectionCategory> categories, Map<Long, BigDecimal> accountAmounts,
                                 Map<Long, BigDecimal> fundAmounts, JournalEntry entry) {
}
