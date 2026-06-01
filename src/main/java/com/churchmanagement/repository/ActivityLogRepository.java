package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.ActivityLogDto;
import com.churchmanagement.dto.ActivityLogSearchCriteria;
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

public class ActivityLogRepository {
    private final DataSource dataSource;

    public ActivityLogRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public ActivityLogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void save(Long userId, String action, String details) {
        save(userId, action, "AUTH", null, null, null, null, null, details);
    }

    public void save(Long userId, String action, String module, String recordId, String oldValue, String newValue,
                     String ipAddress, String machineName, String description) {
        String sql = """
                INSERT INTO activity_logs (
                    user_id, username, action, module, record_id, old_value, new_value,
                    ip_address, machine_name, description, entity_name, entity_id, details
                )
                VALUES (
                    ?, (SELECT u.username FROM users u WHERE u.id = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableLong(statement, 1, userId);
            setNullableLong(statement, 2, userId);
            statement.setString(3, action);
            setNullableString(statement, 4, module);
            setNullableString(statement, 5, recordId);
            setNullableString(statement, 6, oldValue);
            setNullableString(statement, 7, newValue);
            setNullableString(statement, 8, ipAddress);
            setNullableString(statement, 9, machineName);
            setNullableString(statement, 10, truncate(description, 500));
            setNullableString(statement, 11, module);
            setNullableLong(statement, 12, parseLong(recordId));
            setNullableString(statement, 13, description);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save activity log.", exception);
        }
    }

    public List<ActivityLogDto> searchLogs(ActivityLogSearchCriteria criteria) {
        ActivityLogSearchCriteria safeCriteria = criteria == null ? new ActivityLogSearchCriteria() : criteria;
        StringBuilder sql = new StringBuilder(baseSelect()).append(" WHERE 1 = 1 ");
        List<Object> parameters = new ArrayList<>();

        if (safeCriteria.getDateFrom() != null) {
            sql.append("AND al.created_at >= ? ");
            parameters.add(safeCriteria.getDateFrom().atStartOfDay());
        }
        if (safeCriteria.getDateTo() != null) {
            sql.append("AND al.created_at < ? ");
            parameters.add(safeCriteria.getDateTo().plusDays(1).atStartOfDay());
        }
        if (safeCriteria.getUserId() != null) {
            sql.append("AND al.user_id = ? ");
            parameters.add(safeCriteria.getUserId());
        }
        if (hasText(safeCriteria.getAction())) {
            sql.append("AND al.action = ? ");
            parameters.add(safeCriteria.getAction().strip());
        }
        if (hasText(safeCriteria.getModule())) {
            sql.append("AND al.module = ? ");
            parameters.add(safeCriteria.getModule().strip());
        }
        if (hasText(safeCriteria.getKeyword())) {
            sql.append("""
                    AND (
                        al.action LIKE ? OR al.module LIKE ? OR al.record_id LIKE ?
                        OR al.username LIKE ? OR al.description LIKE ?
                    )
                    """);
            String term = "%" + safeCriteria.getKeyword().strip() + "%";
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
        }

        sql.append("ORDER BY al.created_at DESC LIMIT ?");
        parameters.add(safeCriteria.limitOrDefault(1000));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            setParameters(statement, parameters);
            return mapLogs(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to search activity logs.", exception);
        }
    }

    public Optional<ActivityLogDto> findById(long id) {
        String sql = baseSelect() + " WHERE al.id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return mapLogs(statement).stream().findFirst();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load activity log.", exception);
        }
    }

    public List<String> findDistinctActions() {
        return findDistinctValues("action");
    }

    public List<String> findDistinctModules() {
        return findDistinctValues("module");
    }

    private List<String> findDistinctValues(String columnName) {
        String sql = "SELECT DISTINCT " + columnName + " FROM activity_logs "
                + "WHERE " + columnName + " IS NOT NULL AND " + columnName + " <> '' "
                + "ORDER BY " + columnName;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<String> values = new ArrayList<>();
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
            return values;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load activity log filter values.", exception);
        }
    }

    private String baseSelect() {
        return """
                SELECT al.id, al.user_id, al.username, u.full_name AS user_full_name,
                       al.action, al.module, al.record_id, al.old_value, al.new_value,
                       al.ip_address, al.machine_name, al.description, al.created_at
                FROM activity_logs al
                LEFT JOIN users u ON u.id = al.user_id
                """;
    }

    private List<ActivityLogDto> mapLogs(PreparedStatement statement) throws SQLException {
        List<ActivityLogDto> logs = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ActivityLogDto log = new ActivityLogDto();
                log.setId(resultSet.getLong("id"));
                log.setUserId(nullableLong(resultSet, "user_id"));
                log.setUsername(resultSet.getString("username"));
                log.setUserFullName(resultSet.getString("user_full_name"));
                log.setAction(resultSet.getString("action"));
                log.setModule(resultSet.getString("module"));
                log.setRecordId(resultSet.getString("record_id"));
                log.setOldValue(resultSet.getString("old_value"));
                log.setNewValue(resultSet.getString("new_value"));
                log.setIpAddress(resultSet.getString("ip_address"));
                log.setMachineName(resultSet.getString("machine_name"));
                log.setDescription(resultSet.getString("description"));
                Timestamp createdAt = resultSet.getTimestamp("created_at");
                log.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                logs.add(log);
            }
        }
        return logs;
    }

    private void setParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            Object parameter = parameters.get(index);
            if (parameter instanceof LocalDateTime dateTime) {
                statement.setTimestamp(index + 1, Timestamp.valueOf(dateTime));
            } else {
                statement.setObject(index + 1, parameter);
            }
        }
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
