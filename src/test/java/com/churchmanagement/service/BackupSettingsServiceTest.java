package com.churchmanagement.service;

import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.repository.BackupSettingsRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackupSettingsServiceTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void loadsDefaultSettings() {
        BackupSettingsDto defaults = new BackupSettingsDto();
        defaults.setBackupFolder("./backups");
        defaults.setRetentionDays(30);
        BackupSettingsService service = new BackupSettingsService(
                new FakeBackupSettingsRepository(defaults), new BackupFileService(), new ActivityLogService(null));

        BackupSettingsDto settings = service.getSettings();

        assertEquals("./backups", settings.getBackupFolder());
        assertEquals(30, settings.getRetentionDays());
    }

    @Test
    void validatesRetentionDays() {
        AuthContext.setCurrentUser(adminUser());
        BackupSettingsDto settings = validSettings();
        settings.setRetentionDays(0);
        BackupSettingsService service = new BackupSettingsService(
                new FakeBackupSettingsRepository(settings), new BackupFileService(), new ActivityLogService(null));

        BackupSettingsService.BackupSettingsException exception = assertThrows(
                BackupSettingsService.BackupSettingsException.class,
                () -> service.updateSettings(settings));

        assertEquals("Retention days must be greater than zero.", exception.getMessage());
    }

    @Test
    void validatesBackupFolder() {
        AuthContext.setCurrentUser(adminUser());
        BackupSettingsDto settings = validSettings();
        settings.setBackupFolder(" ");
        BackupSettingsService service = new BackupSettingsService(
                new FakeBackupSettingsRepository(settings), new BackupFileService(), new ActivityLogService(null));

        BackupSettingsService.BackupSettingsException exception = assertThrows(
                BackupSettingsService.BackupSettingsException.class,
                () -> service.updateSettings(settings));

        assertEquals("Backup folder is required.", exception.getMessage());
    }

    private BackupSettingsDto validSettings() {
        BackupSettingsDto settings = new BackupSettingsDto();
        settings.setBackupFolder(tempDir.toString());
        settings.setRetentionDays(30);
        return settings;
    }

    private AuthenticatedUser adminUser() {
        return new AuthenticatedUser(1L, "admin", "Admin", 1L, "Admin",
                List.of("backup.settings.manage"));
    }

    private static class FakeBackupSettingsRepository extends BackupSettingsRepository {
        private BackupSettingsDto settings;

        private FakeBackupSettingsRepository(BackupSettingsDto settings) {
            super((DataSource) null);
            this.settings = settings;
        }

        @Override
        public BackupSettingsDto getSettings() {
            return settings;
        }

        @Override
        public BackupSettingsDto updateSettings(BackupSettingsDto settings) {
            this.settings = settings;
            return settings;
        }
    }
}
