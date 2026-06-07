package com.churchmanagement.service;

import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.repository.SmsSettingsRepository;

import java.time.Clock;

public class SmsServiceFactory {
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final SmsSettingsRepository smsSettingsRepository;
    private final SmsService mockSmsService;
    private final SmsService simDongleSmsService;
    private final SystemConfigurationCache configurationCache;

    public SmsServiceFactory() {
        this(new SmsSettingsRepository(), new MockSmsService(), new SimDongleSmsService());
    }

    public SmsServiceFactory(SmsSettingsRepository smsSettingsRepository) {
        this(smsSettingsRepository, new MockSmsService(),
                new SimDongleSmsService(smsSettingsRepository, new SerialPortService(), Clock.systemDefaultZone()));
    }

    public SmsServiceFactory(SmsSettingsRepository smsSettingsRepository, SmsService mockSmsService,
                             SmsService simDongleSmsService) {
        this(smsSettingsRepository, mockSmsService, simDongleSmsService, SystemConfigurationCache.getInstance());
    }

    public SmsServiceFactory(SmsSettingsRepository smsSettingsRepository, SmsService mockSmsService,
                             SmsService simDongleSmsService, SystemConfigurationCache configurationCache) {
        this.smsSettingsRepository = smsSettingsRepository;
        this.mockSmsService = mockSmsService;
        this.simDongleSmsService = simDongleSmsService;
        this.configurationCache = configurationCache;
    }

    public SmsService createSmsService() {
        SmsSettings settings = smsSettingsRepository.getSettings();
        if (settings.getGatewayType() == SmsSettings.GatewayType.SIM_DONGLE) {
            return simDongleSmsService;
        }
        return mockSmsService;
    }

    public SmsService createRoutingSmsService() {
        return this::sendWithConfiguredRetry;
    }

    private SmsResult sendWithConfiguredRetry(String mobileNumber, String message) {
        int maxAttempts = maxAttempts();
        SmsResult lastResult = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            lastResult = createSmsService().sendSms(mobileNumber, message);
            if (lastResult.isSuccess() || !retryEnabled()) {
                return lastResult.withAttemptCount(attempt);
            }
        }
        return lastResult.withAttemptCount(maxAttempts);
    }

    private boolean retryEnabled() {
        String value = configurationCache.getString("sms.retry.enabled");
        return value == null || value.isBlank() || Boolean.parseBoolean(value);
    }

    private int maxAttempts() {
        String value = configurationCache.getString("sms.retry.max.attempts");
        if (value == null || value.isBlank()) {
            return DEFAULT_MAX_ATTEMPTS;
        }

        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return DEFAULT_MAX_ATTEMPTS;
        }
    }
}
