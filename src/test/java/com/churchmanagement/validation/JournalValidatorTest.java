package com.churchmanagement.validation;

import com.churchmanagement.dto.finance.JournalEntryRequest;
import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.entity.Account;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.enums.JournalSourceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JournalValidatorTest {
    private static final LocalDate GO_LIVE = LocalDate.of(2027, 1, 1);
    private static final Map<Long, Account> ACCOUNTS = Map.of(
            1L, account(1L, "1100", "Cash in Hand", AccountType.ASSET, false, Account.Status.ACTIVE),
            2L, account(2L, "4400", "Rent Income", AccountType.INCOME, false, Account.Status.ACTIVE),
            3L, account(3L, "1200", "Bank Accounts", AccountType.ASSET, true, Account.Status.ACTIVE),
            4L, account(4L, "1300", "Receivables", AccountType.ASSET, false, Account.Status.INACTIVE));
    private static final Set<Long> CATEGORIES = Set.of(10L);

    @Test
    void acceptsBalancedEntry() {
        assertDoesNotThrow(() -> validate(request(line(1L, "100.00", null, null), line(2L, null, "100.00", 10L))));
    }

    @Test
    void requiresNarration() {
        assertMessage("Enter a narration.", new JournalEntryRequest(null, JournalSourceType.JOURNAL, GO_LIVE, " ",
                List.of(line(1L, "1", null, null), line(2L, null, "1", 10L))));
    }

    @Test
    void rejectsDateBeforeGoLive() {
        assertMessage("Entries cannot be dated before the go-live date 2027-01-01.",
                new JournalEntryRequest(null, JournalSourceType.JOURNAL, LocalDate.of(2026, 12, 31), "Rent",
                        List.of(line(1L, "1", null, null), line(2L, null, "1", 10L))));
    }

    @Test
    void needsTwoLines() {
        assertMessage("An entry needs at least two lines.", request(line(1L, "100", null, null)));
    }

    @Test
    void rejectsMissingAccount() {
        assertMessage("Line 2: choose an account.", request(line(1L, "100", null, null), line(null, null, "100", 10L)));
    }

    @Test
    void rejectsGroupAccount() {
        assertMessage("Line 1: 1200 Bank Accounts is a group; choose an account under it.",
                request(line(3L, "100", null, null), line(2L, null, "100", 10L)));
    }

    @Test
    void rejectsInactiveAccount() {
        assertMessage("Line 1: 1300 Receivables is inactive.",
                request(line(4L, "100", null, null), line(2L, null, "100", 10L)));
    }

    @Test
    void lineNeedsExactlyOneSide() {
        assertMessage("Line 1: enter either a debit or a credit.",
                request(line(1L, "100", "100", null), line(2L, null, "100", 10L)));
        assertMessage("Line 1: enter either a debit or a credit.",
                request(line(1L, null, null, null), line(2L, null, "100", 10L)));
    }

    @Test
    void rejectsNegativeAndThreeDecimalAmounts() {
        assertMessage("Line 1: amounts cannot be negative.",
                request(line(1L, "-5", null, null), line(2L, null, "5", 10L)));
        assertMessage("Line 1: use at most 2 decimal places.",
                request(line(1L, "1.005", null, null), line(2L, null, "1.005", 10L)));
    }

    @Test
    void requiresCategoryOnIncome() {
        assertMessage("Line 2: choose a category for 4400 Rent Income.",
                request(line(1L, "100", null, null), line(2L, null, "100", null)));
    }

    @Test
    void rejectsInactiveCategory() {
        assertMessage("Line 2: the selected category is not active.",
                request(line(1L, "100", null, null), line(2L, null, "100", 99L)));
    }

    @Test
    void rejectsUnbalancedEntry() {
        assertMessage("Debits (100.00) and credits (99.99) do not balance.",
                request(line(1L, "100", null, null), line(2L, null, "99.99", 10L)));
    }

    private static void validate(JournalEntryRequest request) {
        JournalValidator.validate(request, ACCOUNTS, CATEGORIES, GO_LIVE);
    }

    private static void assertMessage(String expected, JournalEntryRequest request) {
        JournalValidationException exception = assertThrows(JournalValidationException.class, () -> validate(request));
        assertEquals(expected, exception.getMessage());
    }

    private static JournalEntryRequest request(JournalLineRequest... lines) {
        return new JournalEntryRequest(null, JournalSourceType.JOURNAL, GO_LIVE, "Rent for January",
                new ArrayList<>(List.of(lines)));
    }

    private static JournalLineRequest line(Long accountId, String debit, String credit, Long categoryId) {
        return new JournalLineRequest(accountId, debit == null ? null : new BigDecimal(debit),
                credit == null ? null : new BigDecimal(credit), categoryId, null, null);
    }

    private static Account account(long id, String code, String name, AccountType type, boolean group,
                                   Account.Status status) {
        return new Account(id, code, name, type, 1000L, group, CashFlowClass.OPERATING, null, null, status);
    }
}
