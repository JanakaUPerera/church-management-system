package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Optional;

public class BackupSettingsRepository {
    private final DataSource dataSource;

    public BackupSettingsRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public BackupSettingsRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public BackupSettingsDto getSettings() {
        String sql = """
                SELECT id, backup_folder, auto_backup_enabled, auto_backup_time, retention_days,
                       mysqldump_path, mysql_client_path
                FROM backup_settings
                ORDER BY id
                LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return mapSettings(resultSet);
            }
            return createDefaultSettings();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load backup settings.", exception);
        }
    }

    public BackupSettingsDto updateSettings(BackupSettingsDto settings) {
        try {
            Optional<Long> settingsId = findSettingsId();
            if (settingsId.isPresent()) {
                updateSettings(settingsId.get(), settings);
            } else {
                insertSettings(settings);
            }
            return getSettings();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save backup settings.", exception);
        }
    }

    private BackupSettingsDto createDefaultSettings() throws SQLException {
        BackupSettingsDto settings = new BackupSettingsDto();
        settings.setBackupFolder("./backups");
        settings.setRetentionDays(30);
        insertSettings(settings);
        return getSettings();
    }

    private Optional<Long> findSettingsId() throws SQLException {
        String sql = "SELECT id FROM backup_settings ORDER BY id LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? Optional.of(resultSet.getLong("id")) : Optional.empty();
        }
    }

    private void insertSettings(BackupSettingsDto settings) throws SQLException {
        String sql = """
                INSERT INTO backup_settings
                    (backup_folder, auto_backup_enabled, auto_backup_time, retention_days,
                     mysqldump_path, mysql_client_path, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setSettingsParameters(statement, settings);
            statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
        }
    }

    private void updateSettings(long id, BackupSettingsDto settings) throws SQLException {
        String sql = """
                UPDATE backup_settings
                SET backup_folder = ?, auto_backup_enabled = ?, auto_backup_time = ?, retention_days = ?,
                    mysqldump_path = ?, mysql_client_path = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setSettingsParameters(statement, settings);
            statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            statement.setLong(8, id);
            statement.executeUpdate();
        }
    }

    private void setSettingsParameters(PreparedStatement statement, BackupSettingsDto settings) throws SQLException {
        statement.setString(1, settings.getBackupFolder());
        statement.setBoolean(2, settings.isAutoBackupEnabled());
        if (settings.getAutoBackupTime() == null) {
            statement.setNull(3, Types.TIME);
        } else {
            statement.setTime(3, Time.valueOf(settings.getAutoBackupTime()));
        }
        statement.setInt(4, settings.getRetentionDays());
        setNullableString(statement, 5, settings.getMysqldumpPath());
        setNullableString(statement, 6, settings.getMysqlClientPath());
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.strip());
        }
    }

    private BackupSettingsDto mapSettings(ResultSet resultSet) throws SQLException {
        BackupSettingsDto settings = new BackupSettingsDto();
        settings.setBackupFolder(resultSet.getString("backup_folder"));
        settings.setAutoBackupEnabled(resultSet.getBoolean("auto_backup_enabled"));
        Time backupTime = resultSet.getTime("auto_backup_time");
        settings.setAutoBackupTime(backupTime == null ? null : backupTime.toLocalTime());
        settings.setRetentionDays(resultSet.getInt("retention_days"));
        settings.setMysqldumpPath(resultSet.getString("mysqldump_path"));
        settings.setMysqlClientPath(resultSet.getString("mysql_client_path"));
        return settings;
    }
}
