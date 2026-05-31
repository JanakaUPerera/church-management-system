package com.churchmanagement.dto;

import java.time.LocalDateTime;

public class SmsResult {
    private final boolean success;
    private final String message;
    private final String provider;
    private final LocalDateTime sentAt;

    public SmsResult(boolean success, String message, String provider, LocalDateTime sentAt) {
        this.success = success;
        this.message = message;
        this.provider = provider;
        this.sentAt = sentAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getProvider() {
        return provider;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
