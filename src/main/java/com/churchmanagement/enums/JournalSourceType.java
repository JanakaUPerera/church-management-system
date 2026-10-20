package com.churchmanagement.enums;

/** What created a journal entry. F1 uses OPENING, JOURNAL and REVERSAL; the rest are for F2, F3 and F6. */
public enum JournalSourceType {
    OPENING,
    JOURNAL,
    REVERSAL,
    RECEIPT,
    RECEIPT_CANCEL,
    RECEIPT_VOUCHER,
    PAYMENT_VOUCHER,
    CONTRA,
    CLOSING
}
