package com.churchmanagement.exception;

public class ReceiptSequenceLimitExceededException extends RuntimeException {
    public ReceiptSequenceLimitExceededException(int year, long sequence) {
        super("Receipt sequence limit exceeded for " + year + ". Attempted sequence: " + sequence);
    }
}
