package com.churchmanagement.service;

/** A finance rule was broken; the message is shown to the user as-is. */
public class FinanceException extends RuntimeException {
    public FinanceException(String message) {
        super(message);
    }

    public FinanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
