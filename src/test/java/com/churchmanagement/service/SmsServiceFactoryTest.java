package com.churchmanagement.service;

import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.repository.SmsSettingsRepository;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertSame;

class SmsServiceFactoryTest {
    @Test
    void selectsMockSmsServiceForMockGateway() {
        FakeSmsService mockService = new FakeSmsService();
        FakeSmsService simDongleService = new FakeSmsService();
        SmsServiceFactory factory = new SmsServiceFactory(
                new FakeSmsSettingsRepository(SmsSettings.GatewayType.MOCK), mockService, simDongleService);

        assertSame(mockService, factory.createSmsService());
    }

    @Test
    void selectsSimDongleSmsServiceForSimDongleGateway() {
        FakeSmsService mockService = new FakeSmsService();
        FakeSmsService simDongleService = new FakeSmsService();
        SmsServiceFactory factory = new SmsServiceFactory(
                new FakeSmsSettingsRepository(SmsSettings.GatewayType.SIM_DONGLE), mockService, simDongleService);

        assertSame(simDongleService, factory.createSmsService());
    }

    private static class FakeSmsSettingsRepository extends SmsSettingsRepository {
        private final SmsSettings.GatewayType gatewayType;

        private FakeSmsSettingsRepository(SmsSettings.GatewayType gatewayType) {
            super((DataSource) null);
            this.gatewayType = gatewayType;
        }

        @Override
        public SmsSettings getSettings() {
            SmsSettings settings = new SmsSettings();
            settings.setGatewayType(gatewayType);
            return settings;
        }
    }

    private static class FakeSmsService implements SmsService {
        @Override
        public SmsResult sendSms(String mobileNumber, String message) {
            return null;
        }
    }
}
