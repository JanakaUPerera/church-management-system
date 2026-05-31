package com.churchmanagement.service;

import com.churchmanagement.dto.SmsResult;

import java.time.Clock;
import java.time.LocalDateTime;

public class MockSmsService implements SmsService {
    public static final String PROVIDER = "Mock SMS Gateway";

    private final Clock clock;

    public MockSmsService() {
        this(Clock.systemDefaultZone());
    }

    public MockSmsService(Clock clock) {
        this.clock = clock;
    }

    @Override
    public SmsResult sendSms(String mobileNumber, String message) {
        if (mobileNumber == null || mobileNumber.isBlank()) {
            return new SmsResult(false, "Mobile number is required.", PROVIDER, null);
        }
        if (message == null || message.isBlank()) {
            return new SmsResult(false, "SMS message is required.", PROVIDER, null);
        }

        return new SmsResult(true, "SMS sent successfully.", PROVIDER, LocalDateTime.now(clock));
    }
}
