package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
                SELECT id, backup_folder, retention_days, mysqldump_path, mysql_client_path
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
                    (backup_folder, retention_days, mysqldump_path, mysql_client_path, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setSettingsParameters(statement, settings);
            statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
        }
    }

    private void updateSettings(long id, BackupSettingsDto settings) throws SQLException {
        String sql = """
                UPDATE backup_settings
                SET backup_folder = ?, retention_days = ?, mysqldump_path = ?, mysql_client_path = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setSettingsParameters(statement, settings);
            statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            statement.setLong(6, id);
            statement.executeUpdate();
        }
    }

    private void setSettingsParameters(PreparedStatement statement, BackupSettingsDto settings) throws SQLException {
        statement.setString(1, settings.getBackupFolder());
        statement.setInt(2, settings.getRetentionDays());
        setNullableString(statement, 3, settings.getMysqldumpPath());
        setNullableString(statement, 4, settings.getMysqlClientPath());
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
        settings.setRetentionDays(resultSet.getInt("retention_days"));
        settings.setMysqldumpPath(resultSet.getString("mysqldump_path"));
        settings.setMysqlClientPath(resultSet.getString("mysql_client_path"));
        return settings;
    }
}
