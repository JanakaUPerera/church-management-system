package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;

public class BackupSettingsRepository {
    /** Shared, global policy — same retention window applies everywhere. */
    private static final String RETENTION_DAYS_KEY = "backup.retention.days";

    /**
     * Local per-machine keys — persisted to this machine's own external
     * {@code application.properties} via {@link DatabaseConfig#setProperty},
     * NOT the shared {@code system_settings} table. The backup destination
     * folder and the mysqldump/mysql executable paths only make sense on the
     * machine running the backup; sharing them via the database meant one
     * client's configured tool path silently broke every other client's
     * automatic backup ("Backup failed. Please check database credentials
     * and tool paths.").
     */
    private static final String BACKUP_FOLDER_KEY = "backup.folder";
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
        settings.setBackupFolder(defaultText(DatabaseConfig.getProperty(BACKUP_FOLDER_KEY), "./backups"));
        settings.setRetentionDays(defaultInt(systemSettingRepository.getValue(RETENTION_DAYS_KEY), 30));
        settings.setMysqldumpPath(blankToNull(DatabaseConfig.getProperty(MYSQLDUMP_PATH_KEY)));
        settings.setMysqlClientPath(blankToNull(DatabaseConfig.getProperty(MYSQL_CLIENT_PATH_KEY)));
        return settings;
    }

    public BackupSettingsDto updateSettings(BackupSettingsDto settings) {
        try {
            DatabaseConfig.setProperty(BACKUP_FOLDER_KEY, defaultText(settings.getBackupFolder(), "./backups"));
            systemSettingRepository.updateSetting(RETENTION_DAYS_KEY, Integer.toString(settings.getRetentionDays()));
            DatabaseConfig.setProperty(MYSQLDUMP_PATH_KEY, emptyIfNull(blankToNull(settings.getMysqldumpPath())));
            DatabaseConfig.setProperty(MYSQL_CLIENT_PATH_KEY, emptyIfNull(blankToNull(settings.getMysqlClientPath())));
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

    /** Properties.setProperty() rejects null values — use "" to mean "not configured". */
    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}
