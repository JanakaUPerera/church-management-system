package com.churchmanagement.service;

import com.churchmanagement.entity.SystemSetting;
import com.churchmanagement.repository.SystemSettingRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SystemConfigurationCache {
    private final SystemSettingRepository systemSettingRepository;
    private final Map<String, String> values = new ConcurrentHashMap<>();

    public SystemConfigurationCache(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    public static SystemConfigurationCache getInstance() {
        return Holder.INSTANCE;
    }

    public synchronized void loadSettings() {
        values.clear();
        values.putAll(systemSettingRepository.findAll().stream()
                .collect(Collectors.toMap(SystemSetting::getSettingKey,
                        setting -> nullToBlank(setting.getSettingValue()))));
    }

    public synchronized void reload() {
        loadSettings();
    }

    public String getString(String key) {
        return values.get(key);
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(values.get(key));
    }

    public int getInt(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Integer.parseInt(value);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static class Holder {
        private static final SystemConfigurationCache INSTANCE =
                new SystemConfigurationCache(new SystemSettingRepository());
    }
}
