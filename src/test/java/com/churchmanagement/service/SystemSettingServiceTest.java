package com.churchmanagement.service;

import com.churchmanagement.dto.SystemSettingDto;
import com.churchmanagement.dto.UpdateSystemSettingRequest;
import com.churchmanagement.entity.SystemSetting;
import com.churchmanagement.repository.SystemSettingRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SystemSettingServiceTest {
    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void loadsSettings() {
        AuthContext.setCurrentUser(settingsAdmin());
        SystemSettingService service = serviceWithDefaults(new TestSystemConfigurationCache());

        List<SystemSettingDto> settings = service.loadSettings();

        assertEquals(19, settings.size());
        assertEquals("organization.name", settings.getFirst().getSettingKey());
    }

    @Test
    void updatesEditableSetting() {
        AuthContext.setCurrentUser(settingsAdmin());
        TestSystemConfigurationCache cache = new TestSystemConfigurationCache();
        SystemSettingService service = serviceWithDefaults(cache);

        service.updateSettings(List.of(new UpdateSystemSettingRequest("organization.name", "Gethsemana")));

        assertEquals("Gethsemana", cache.repository.getValue("organization.name"));
    }

    @Test
    void rejectsInvalidPadding() {
        assertInvalid("receipt.sequence.padding", "3",
                "Receipt sequence padding must be between 4 and 10.");
    }

    @Test
    void rejectsInvalidRetryAttempts() {
        assertInvalid("sms.retry.max.attempts", "11",
                "SMS retry max attempts must be between 0 and 10.");
    }

    @Test
    void rejectsInvalidRetentionDays() {
        assertInvalid("backup.retention.days", "366",
                "Backup retention days must be between 1 and 365.");
    }

    @Test
    void rejectsInvalidLanguage() {
        assertInvalid("receipt.default.language", "FRENCH",
                "Receipt default language must be ENGLISH, SINHALA, or TAMIL.");
    }

    @Test
    void rejectsInvalidPdfChartsEnabledFlag() {
        assertInvalid("reports.pdf.charts.enabled", "sometimes",
                "PDF report charts setting must be true or false.");
    }

    @Test
    void rejectsInvalidLateReasonRequiredFlag() {
        assertInvalid("receipt.late.reason.required", "sometimes",
                "Late submission reason required setting must be true or false.");
    }

    @Test
    void rejectsUpdateOfNonEditableSetting() {
        AuthContext.setCurrentUser(settingsAdmin());
        TestSystemConfigurationCache cache = new TestSystemConfigurationCache();
        cache.repository.settings.get("system.date.format").setEditable(false);
        SystemSettingService service = new SystemSettingService(cache.repository, cache, new ActivityLogService(null));

        SystemSettingService.SystemSettingException exception = assertThrows(
                SystemSettingService.SystemSettingException.class,
                () -> service.updateSettings(List.of(new UpdateSystemSettingRequest("system.date.format", "dd/MM/yyyy"))));

        assertEquals("Setting cannot be edited: system.date.format", exception.getMessage());
    }

    @Test
    void reloadsCacheAfterUpdate() {
        AuthContext.setCurrentUser(settingsAdmin());
        TestSystemConfigurationCache cache = new TestSystemConfigurationCache();
        SystemSettingService service = new SystemSettingService(cache.repository, cache, new ActivityLogService(null));

        service.updateSettings(List.of(new UpdateSystemSettingRequest("organization.name", "Updated")));

        assertEquals(1, cache.reloadCount);
        assertEquals("Updated", cache.getString("organization.name"));
    }

    @Test
    void enforcesSettingsManagePermission() {
        AuthContext.setCurrentUser(new AuthenticatedUser(2L, "viewer", "Viewer", 2L, "User", List.of()));
        SystemSettingService service = serviceWithDefaults(new TestSystemConfigurationCache());

        SystemSettingService.SystemSettingException exception = assertThrows(
                SystemSettingService.SystemSettingException.class,
                service::loadSettings);

        assertEquals("You do not have permission to manage settings.", exception.getMessage());
    }

    private void assertInvalid(String key, String value, String message) {
        AuthContext.setCurrentUser(settingsAdmin());
        SystemSettingService service = serviceWithDefaults(new TestSystemConfigurationCache());

        SystemSettingService.SystemSettingException exception = assertThrows(
                SystemSettingService.SystemSettingException.class,
                () -> service.updateSettings(List.of(new UpdateSystemSettingRequest(key, value))));

        assertEquals(message, exception.getMessage());
        AuthContext.clear();
    }

    private SystemSettingService serviceWithDefaults(TestSystemConfigurationCache cache) {
        return new SystemSettingService(cache.repository, cache, new ActivityLogService(null));
    }

    private AuthenticatedUser settingsAdmin() {
        return new AuthenticatedUser(1L, "admin", "Admin", 1L, "Manager", List.of("settings.manage"));
    }

    private static SystemSetting setting(String key, String value, String type, String category) {
        SystemSetting setting = new SystemSetting();
        setting.setId((long) key.hashCode());
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        setting.setSettingType(type);
        setting.setCategory(category);
        setting.setDescription(key);
        setting.setEditable(true);
        return setting;
    }

    private static class TestSystemConfigurationCache extends SystemConfigurationCache {
        private final FakeSystemSettingRepository repository;
        private int reloadCount;
        private final Map<String, String> values = new LinkedHashMap<>();

        private TestSystemConfigurationCache() {
            this(new FakeSystemSettingRepository());
        }

        private TestSystemConfigurationCache(FakeSystemSettingRepository repository) {
            super(repository);
            this.repository = repository;
        }

        @Override
        public synchronized void reload() {
            reloadCount++;
            values.clear();
            for (SystemSetting setting : repository.findAll()) {
                values.put(setting.getSettingKey(), setting.getSettingValue());
            }
        }

        @Override
        public String getString(String key) {
            return values.get(key);
        }
    }

    private static class FakeSystemSettingRepository extends SystemSettingRepository {
        private final Map<String, SystemSetting> settings = new LinkedHashMap<>();

        private FakeSystemSettingRepository() {
            super((DataSource) null);
            add(setting("organization.name", "Default Church", "STRING", "GENERAL"));
            add(setting("organization.address", "", "TEXT", "GENERAL"));
            add(setting("organization.phone", "", "STRING", "GENERAL"));
            add(setting("receipt.number.prefix", "REC", "STRING", "RECEIPT"));
            add(setting("receipt.sequence.padding", "6", "INTEGER", "RECEIPT"));
            add(setting("receipt.allow.back.week", "true", "BOOLEAN", "RECEIPT"));
            add(setting("receipt.late.reason.required", "false", "BOOLEAN", "RECEIPT"));
            add(setting("receipt.default.language", "ENGLISH", "ENUM", "RECEIPT"));
            add(setting("sms.enabled", "false", "BOOLEAN", "SMS"));
            add(setting("sms.gateway.type", "SIM_DONGLE", "ENUM", "SMS"));
            add(setting("sms.retry.enabled", "true", "BOOLEAN", "SMS"));
            add(setting("sms.retry.max.attempts", "3", "INTEGER", "SMS"));
            add(setting("backup.auto.enabled", "false", "BOOLEAN", "BACKUP"));
            add(setting("backup.retention.days", "30", "INTEGER", "BACKUP"));
            add(setting("system.date.format", "yyyy-MM-dd", "STRING", "SYSTEM"));
            add(setting("reports.export.folder", "./reports", "STRING", "SYSTEM"));
            add(setting("reports.pdf.charts.enabled", "true", "BOOLEAN", "SYSTEM"));
            add(setting("system.time.format", "HH:mm:ss", "STRING", "SYSTEM"));
            add(setting("system.theme", "ORCHID", "ENUM", "SYSTEM"));
        }

        @Override
        public List<SystemSetting> findAll() {
            return new ArrayList<>(settings.values());
        }

        @Override
        public List<SystemSetting> findByCategory(String category) {
            return settings.values().stream()
                    .filter(setting -> setting.getCategory().equals(category))
                    .toList();
        }

        @Override
        public SystemSetting updateSetting(String key, String value) {
            settings.get(key).setSettingValue(value);
            return settings.get(key);
        }

        @Override
        public String getValue(String key) {
            return settings.get(key).getSettingValue();
        }

        private void add(SystemSetting setting) {
            settings.put(setting.getSettingKey(), setting);
        }
    }
}
