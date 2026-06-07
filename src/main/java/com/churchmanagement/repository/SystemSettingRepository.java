package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.SystemSetting;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SystemSettingRepository {
    private final DataSource dataSource;

    public SystemSettingRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public SystemSettingRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<SystemSetting> findAll() {
        String sql = baseSelect() + " ORDER BY category, setting_key";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return mapSettings(resultSet);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load system settings.", exception);
        }
    }

    public List<SystemSetting> findByCategory(String category) {
        String sql = baseSelect() + " WHERE category = ? ORDER BY setting_key";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapSettings(resultSet);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load settings by category.", exception);
        }
    }

    public Optional<SystemSetting> findByKey(String key) {
        String sql = baseSelect() + " WHERE setting_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapSetting(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load system setting.", exception);
        }
    }

    public SystemSetting updateSetting(String key, String value) {
        String sql = """
                UPDATE system_settings
                SET setting_value = ?, updated_at = ?
                WHERE setting_key = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableString(statement, 1, value);
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(3, key);
            statement.executeUpdate();
            return findByKey(key).orElseThrow(() -> new DatabaseException("System setting was not found: " + key));
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update system setting.", exception);
        }
    }

    public String getValue(String key) {
        return findByKey(key).map(SystemSetting::getSettingValue).orElse(null);
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(getValue(key));
    }

    public int getInt(String key) {
        String value = getValue(key);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException exception) {
            throw new DatabaseException("System setting is not a valid integer: " + key, exception);
        }
    }

    private String baseSelect() {
        return """
                SELECT id, setting_key, setting_value, setting_type, category, description,
                       editable, created_at, updated_at
                FROM system_settings
                """;
    }

    private List<SystemSetting> mapSettings(ResultSet resultSet) throws SQLException {
        List<SystemSetting> settings = new ArrayList<>();
        while (resultSet.next()) {
            settings.add(mapSetting(resultSet));
        }
        return settings;
    }

    private SystemSetting mapSetting(ResultSet resultSet) throws SQLException {
        SystemSetting setting = new SystemSetting();
        setting.setId(resultSet.getLong("id"));
        setting.setSettingKey(resultSet.getString("setting_key"));
        setting.setSettingValue(resultSet.getString("setting_value"));
        setting.setSettingType(resultSet.getString("setting_type"));
        setting.setCategory(resultSet.getString("category"));
        setting.setDescription(resultSet.getString("description"));
        setting.setEditable(resultSet.getBoolean("editable"));
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        setting.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        setting.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        return setting;
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.LONGVARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}
