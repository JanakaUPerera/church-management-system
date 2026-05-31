package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Optional;

public class SmsSettingsRepository {
    private final DataSource dataSource;

    public SmsSettingsRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public SmsSettingsRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public SmsSettings getSettings() {
        String sql = """
                SELECT id, sms_enabled, gateway_type, com_port, baud_rate, created_at, updated_at
                FROM sms_settings
                ORDER BY id
                LIMIT 1
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapSettings(resultSet);
                }
            }
            return createDefaultSettings();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load SMS settings.", exception);
        }
    }

    public SmsSettings saveSettings(boolean smsEnabled, SmsSettings.GatewayType gatewayType,
                                    String comPort, Integer baudRate) {
        try {
            Optional<Long> settingsId = findSettingsId();
            if (settingsId.isPresent()) {
                updateSettings(settingsId.get(), smsEnabled, gatewayType, comPort, baudRate);
            } else {
                insertSettings(smsEnabled, gatewayType, comPort, baudRate);
            }
            return getSettings();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save SMS settings.", exception);
        }
    }

    private SmsSettings createDefaultSettings() throws SQLException {
        insertSettings(false, SmsSettings.GatewayType.MOCK, null, 9600);
        return getSettings();
    }

    private Optional<Long> findSettingsId() {
        String sql = "SELECT id FROM sms_settings ORDER BY id LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? Optional.of(resultSet.getLong("id")) : Optional.empty();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to find SMS settings.", exception);
        }
    }

    private void insertSettings(boolean smsEnabled, SmsSettings.GatewayType gatewayType,
                                String comPort, Integer baudRate) throws SQLException {
        String sql = """
                INSERT INTO sms_settings (sms_enabled, gateway_type, com_port, baud_rate, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            setSettingsParameters(statement, smsEnabled, gatewayType, comPort, baudRate);
            statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
        }
    }

    private void updateSettings(long id, boolean smsEnabled, SmsSettings.GatewayType gatewayType,
                                String comPort, Integer baudRate) {
        String sql = """
                UPDATE sms_settings
                SET sms_enabled = ?, gateway_type = ?, com_port = ?, baud_rate = ?, updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setSettingsParameters(statement, smsEnabled, gatewayType, comPort, baudRate);
            statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            statement.setLong(6, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save SMS settings.", exception);
        }
    }

    private void setSettingsParameters(PreparedStatement statement, boolean smsEnabled,
                                       SmsSettings.GatewayType gatewayType, String comPort,
                                       Integer baudRate) throws SQLException {
        statement.setBoolean(1, smsEnabled);
        statement.setString(2, (gatewayType == null ? SmsSettings.GatewayType.MOCK : gatewayType).name());
        if (comPort == null || comPort.isBlank()) {
            statement.setNull(3, Types.VARCHAR);
        } else {
            statement.setString(3, comPort.strip());
        }
        if (baudRate == null) {
            statement.setNull(4, Types.INTEGER);
        } else {
            statement.setInt(4, baudRate);
        }
    }

    private SmsSettings mapSettings(ResultSet resultSet) throws SQLException {
        SmsSettings settings = new SmsSettings();
        settings.setId(resultSet.getLong("id"));
        settings.setSmsEnabled(resultSet.getBoolean("sms_enabled"));
        settings.setGatewayType(SmsSettings.GatewayType.valueOf(resultSet.getString("gateway_type")));
        settings.setComPort(resultSet.getString("com_port"));
        int baudRate = resultSet.getInt("baud_rate");
        settings.setBaudRate(resultSet.wasNull() ? null : baudRate);
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        settings.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        settings.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        return settings;
    }
}
