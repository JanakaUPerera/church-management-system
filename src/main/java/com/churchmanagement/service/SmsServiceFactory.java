package com.churchmanagement.service;

import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.repository.SmsSettingsRepository;

import java.time.Clock;

public class SmsServiceFactory {
    private final SmsSettingsRepository smsSettingsRepository;
    private final SmsService mockSmsService;
    private final SmsService simDongleSmsService;

    public SmsServiceFactory() {
        this(new SmsSettingsRepository(), new MockSmsService(), new SimDongleSmsService());
    }

    public SmsServiceFactory(SmsSettingsRepository smsSettingsRepository) {
        this(smsSettingsRepository, new MockSmsService(),
                new SimDongleSmsService(smsSettingsRepository, new SerialPortService(), Clock.systemDefaultZone()));
    }

    public SmsServiceFactory(SmsSettingsRepository smsSettingsRepository, SmsService mockSmsService,
                             SmsService simDongleSmsService) {
        this.smsSettingsRepository = smsSettingsRepository;
        this.mockSmsService = mockSmsService;
        this.simDongleSmsService = simDongleSmsService;
    }

    public SmsService createSmsService() {
        SmsSettings settings = smsSettingsRepository.getSettings();
        if (settings.getGatewayType() == SmsSettings.GatewayType.SIM_DONGLE) {
            return simDongleSmsService;
        }
        return mockSmsService;
    }

    public SmsService createRoutingSmsService() {
        return (mobileNumber, message) -> createSmsService().sendSms(mobileNumber, message);
    }
}
