package com.churchmanagement.util;

import com.churchmanagement.exception.ReceiptSequenceLimitExceededException;

public class ReceiptNumberFormatter {
    public static final long MAX_SEQUENCE = 999_999L;

    public String format(int year, long sequence) {
        if (sequence < 1 || sequence > MAX_SEQUENCE) {
            throw new ReceiptSequenceLimitExceededException(year, sequence);
        }

        int twoDigitYear = Math.floorMod(year, 100);
        return "REC" + String.format("%02d%06d", twoDigitYear, sequence);
    }
}
