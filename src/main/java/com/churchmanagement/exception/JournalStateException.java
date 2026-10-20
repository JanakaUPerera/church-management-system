package com.churchmanagement.exception;

/** A journal entry is not in the state an operation needs (e.g. posting a posted entry). */
public class JournalStateException extends RuntimeException {
    public JournalStateException(String message) {
        super(message);
    }
}
