package com.churchmanagement.validation;

import com.churchmanagement.dto.finance.JournalEntryRequest;
import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.entity.Account;
import com.churchmanagement.util.MoneyFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/** Double-entry rules every journal entry must satisfy before it is saved or posted. */
public final class JournalValidator {
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");
    private static final int MAX_NARRATION = 500;

    private JournalValidator() {
    }

    public static void validate(JournalEntryRequest request, Map<Long, Account> accounts, Set<Long> activeCategoryIds,
                                LocalDate goLiveDate) {
        if (request.narration() == null || request.narration().isBlank()) {
            throw new JournalValidationException("Enter a narration.");
        }
        if (request.narration().strip().length() > MAX_NARRATION) {
            throw new JournalValidationException("The narration cannot exceed 500 characters.");
        }
        if (request.entryDate() == null) {
            throw new JournalValidationException("Choose the entry date.");
        }
        if (request.entryDate().isBefore(goLiveDate)) {
            throw new JournalValidationException("Entries cannot be dated before the go-live date " + goLiveDate + ".");
        }
        if (request.lines().size() < 2) {
            throw new JournalValidationException("An entry needs at least two lines.");
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (int index = 0; index < request.lines().size(); index++) {
            JournalLineRequest line = request.lines().get(index);
            String prefix = "Line " + (index + 1) + ": ";
            Account account = line.accountId() == null ? null : accounts.get(line.accountId());
            if (account == null) {
                throw new JournalValidationException(prefix + "choose an account.");
            }
            if (account.group()) {
                throw new JournalValidationException(prefix + account.displayText()
                        + " is a group; choose an account under it.");
            }
            if (!account.isActive()) {
                throw new JournalValidationException(prefix + account.displayText() + " is inactive.");
            }
            BigDecimal debit = line.debitOrZero();
            BigDecimal credit = line.creditOrZero();
            if (debit.signum() < 0 || credit.signum() < 0) {
                throw new JournalValidationException(prefix + "amounts cannot be negative.");
            }
            if ((debit.signum() > 0) == (credit.signum() > 0)) {
                throw new JournalValidationException(prefix + "enter either a debit or a credit.");
            }
            BigDecimal amount = debit.signum() > 0 ? debit : credit;
            if (amount.stripTrailingZeros().scale() > 2) {
                throw new JournalValidationException(prefix + "use at most 2 decimal places.");
            }
            if (amount.compareTo(MAX_AMOUNT) > 0) {
                throw new JournalValidationException(prefix + "the amount is too large.");
            }
            if (account.type().requiresCategory() && line.categoryId() == null) {
                throw new JournalValidationException(prefix + "choose a category for " + account.displayText() + ".");
            }
            if (line.categoryId() != null && !activeCategoryIds.contains(line.categoryId())) {
                throw new JournalValidationException(prefix + "the selected category is not active.");
            }
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new JournalValidationException("Debits (" + MoneyFormat.format(totalDebit) + ") and credits ("
                    + MoneyFormat.format(totalCredit) + ") do not balance.");
        }
    }
}
