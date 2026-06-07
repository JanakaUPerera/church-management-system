package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;

public class BackupSettingsRepository {
    private static final String BACKUP_FOLDER_KEY = "backup.folder";
    private static final String RETENTION_DAYS_KEY = "backup.retention.days";
    private static final String MYSQLDUMP_PATH_KEY = "backup.mysqldump.path";
    private static final String MYSQL_CLIENT_PATH_KEY = "backup.mysql.client.path";

    private final SystemSettingRepository systemSettingRepository;

    public BackupSettingsRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public BackupSettingsRepository(DataSource dataSource) {
        this(new SystemSettingRepository(dataSource));
    }

    BackupSettingsRepository(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    public BackupSettingsDto getSettings() {
        BackupSettingsDto settings = new BackupSettingsDto();
        settings.setBackupFolder(defaultText(systemSettingRepository.getValue(BACKUP_FOLDER_KEY), "./backups"));
        settings.setRetentionDays(defaultInt(systemSettingRepository.getValue(RETENTION_DAYS_KEY), 30));
        settings.setMysqldumpPath(blankToNull(systemSettingRepository.getValue(MYSQLDUMP_PATH_KEY)));
        settings.setMysqlClientPath(blankToNull(systemSettingRepository.getValue(MYSQL_CLIENT_PATH_KEY)));
        return settings;
    }

    public BackupSettingsDto updateSettings(BackupSettingsDto settings) {
        try {
            systemSettingRepository.updateSetting(BACKUP_FOLDER_KEY, defaultText(settings.getBackupFolder(), "./backups"));
            systemSettingRepository.updateSetting(RETENTION_DAYS_KEY, Integer.toString(settings.getRetentionDays()));
            systemSettingRepository.updateSetting(MYSQLDUMP_PATH_KEY, blankToNull(settings.getMysqldumpPath()));
            systemSettingRepository.updateSetting(MYSQL_CLIENT_PATH_KEY, blankToNull(settings.getMysqlClientPath()));
            return getSettings();
        } catch (RuntimeException exception) {
            throw new DatabaseException("Unable to save backup settings.", exception);
        }
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
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
