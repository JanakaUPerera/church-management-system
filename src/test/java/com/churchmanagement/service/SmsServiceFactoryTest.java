package com.churchmanagement.service;

import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.repository.SmsSettingsRepository;
import com.churchmanagement.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void routingServiceRetriesFailedSmsUsingConfiguredAttempts() {
        FakeSmsService mockService = new FakeSmsService(false);
        SmsServiceFactory factory = new SmsServiceFactory(
                new FakeSmsSettingsRepository(SmsSettings.GatewayType.MOCK),
                mockService,
                new FakeSmsService(),
                new FakeSystemConfigurationCache(Map.of(
                        "sms.retry.enabled", "true",
                        "sms.retry.max.attempts", "3"
                )));

        SmsResult result = factory.createRoutingSmsService().sendSms("+94771234567", "Message");

        assertEquals(3, mockService.sendCount);
        assertEquals(3, result.getAttemptCount());
    }

    @Test
    void routingServiceDoesNotRetryWhenRetryDisabled() {
        FakeSmsService mockService = new FakeSmsService(false);
        SmsServiceFactory factory = new SmsServiceFactory(
                new FakeSmsSettingsRepository(SmsSettings.GatewayType.MOCK),
                mockService,
                new FakeSmsService(),
                new FakeSystemConfigurationCache(Map.of(
                        "sms.retry.enabled", "false",
                        "sms.retry.max.attempts", "3"
                )));

        SmsResult result = factory.createRoutingSmsService().sendSms("+94771234567", "Message");

        assertEquals(1, mockService.sendCount);
        assertEquals(1, result.getAttemptCount());
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
        private final boolean success;
        private int sendCount;

        private FakeSmsService() {
            this(true);
        }

        private FakeSmsService(boolean success) {
            this.success = success;
        }

        @Override
        public SmsResult sendSms(String mobileNumber, String message) {
            sendCount++;
            return new SmsResult(success, success ? "Sent" : "Failed", MockSmsService.PROVIDER, null);
        }
    }

    private static class FakeSystemConfigurationCache extends SystemConfigurationCache {
        private final Map<String, String> values;

        private FakeSystemConfigurationCache(Map<String, String> values) {
            super(new SystemSettingRepository((DataSource) null));
            this.values = values;
        }

        @Override
        public String getString(String key) {
            return values.get(key);
        }
    }
}
