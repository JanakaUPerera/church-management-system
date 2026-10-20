package com.churchmanagement.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTypeTest {
    @Test
    void typeFollowsFirstCodeDigit() {
        assertEquals(AccountType.ASSET, AccountType.forCode("1201"));
        assertEquals(AccountType.LIABILITY, AccountType.forCode("2100"));
        assertEquals(AccountType.EQUITY, AccountType.forCode("3100"));
        assertEquals(AccountType.INCOME, AccountType.forCode("4900"));
        assertEquals(AccountType.EXPENSE, AccountType.forCode("5990"));
    }

    @Test
    void rejectsCodesOutsideTheChart() {
        assertThrows(IllegalArgumentException.class, () -> AccountType.forCode("6000"));
        assertThrows(IllegalArgumentException.class, () -> AccountType.forCode("120"));
        assertThrows(IllegalArgumentException.class, () -> AccountType.forCode("12A0"));
        assertThrows(IllegalArgumentException.class, () -> AccountType.forCode(null));
    }

    @Test
    void normalBalanceAndCategoryRules() {
        assertTrue(AccountType.ASSET.isDebitNormal());
        assertTrue(AccountType.EXPENSE.isDebitNormal());
        assertFalse(AccountType.INCOME.isDebitNormal());
        assertTrue(AccountType.INCOME.requiresCategory());
        assertTrue(AccountType.EXPENSE.requiresCategory());
        assertTrue(AccountType.EQUITY.requiresCategory());
        assertFalse(AccountType.ASSET.requiresCategory());
        assertFalse(AccountType.LIABILITY.requiresCategory());
    }
}
