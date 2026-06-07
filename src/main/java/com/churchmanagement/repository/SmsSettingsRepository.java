package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;

public class SmsSettingsRepository {
    private static final String SMS_ENABLED_KEY = "sms.enabled";
    private static final String SMS_GATEWAY_TYPE_KEY = "sms.gateway.type";
    private static final String SMS_COM_PORT_KEY = "sms.com.port";
    private static final String SMS_BAUD_RATE_KEY = "sms.baud.rate";

    private final SystemSettingRepository systemSettingRepository;

    public SmsSettingsRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public SmsSettingsRepository(DataSource dataSource) {
        this(new SystemSettingRepository(dataSource));
    }

    SmsSettingsRepository(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    public SmsSettings getSettings() {
        SmsSettings settings = new SmsSettings();
        settings.setSmsEnabled(Boolean.parseBoolean(systemSettingRepository.getValue(SMS_ENABLED_KEY)));
        settings.setGatewayType(gatewayType(systemSettingRepository.getValue(SMS_GATEWAY_TYPE_KEY)));
        settings.setComPort(blankToNull(systemSettingRepository.getValue(SMS_COM_PORT_KEY)));
        settings.setBaudRate(defaultInt(systemSettingRepository.getValue(SMS_BAUD_RATE_KEY), 9600));
        return settings;
    }

    public SmsSettings saveSettings(boolean smsEnabled, SmsSettings.GatewayType gatewayType,
                                    String comPort, Integer baudRate) {
        try {
            systemSettingRepository.updateSetting(SMS_ENABLED_KEY, Boolean.toString(smsEnabled));
            systemSettingRepository.updateSetting(SMS_GATEWAY_TYPE_KEY,
                    (gatewayType == null ? SmsSettings.GatewayType.MOCK : gatewayType).name());
            systemSettingRepository.updateSetting(SMS_COM_PORT_KEY, blankToNull(comPort));
            systemSettingRepository.updateSetting(SMS_BAUD_RATE_KEY,
                    Integer.toString(baudRate == null ? 9600 : baudRate));
            return getSettings();
        } catch (RuntimeException exception) {
            throw new DatabaseException("Unable to save SMS settings.", exception);
        }
    }

    private SmsSettings.GatewayType gatewayType(String value) {
        if (value == null || value.isBlank()) {
            return SmsSettings.GatewayType.MOCK;
        }
        try {
            return SmsSettings.GatewayType.valueOf(value.strip());
        } catch (IllegalArgumentException exception) {
            return SmsSettings.GatewayType.MOCK;
        }
    }

    private int defaultInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
