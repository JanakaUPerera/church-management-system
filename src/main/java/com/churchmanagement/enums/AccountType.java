package com.churchmanagement.enums;

/** Account type; the first digit of an account code decides it (1 Asset … 5 Expense). */
public enum AccountType {
    ASSET('1'),
    LIABILITY('2'),
    EQUITY('3'),
    INCOME('4'),
    EXPENSE('5');

    private final char codeDigit;

    AccountType(char codeDigit) {
        this.codeDigit = codeDigit;
    }

    public char codeDigit() {
        return codeDigit;
    }

    /** Assets and expenses grow with debits; liabilities, equity and income with credits. */
    public boolean isDebitNormal() {
        return this == ASSET || this == EXPENSE;
    }

    /** Lines on these accounts must carry a collection category (fund). */
    public boolean requiresCategory() {
        return this == INCOME || this == EXPENSE || this == EQUITY;
    }

    public static AccountType forCode(String code) {
        if (code == null || !code.matches("\\d{4}")) {
            throw new IllegalArgumentException("Account code must be 4 digits.");
        }
        for (AccountType type : values()) {
            if (type.codeDigit == code.charAt(0)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Account codes start with 1 to 5.");
    }
}
