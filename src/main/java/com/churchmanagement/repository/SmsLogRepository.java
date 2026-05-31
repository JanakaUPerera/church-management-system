package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.SmsLogDto;
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

public class SmsLogRepository {
    private final DataSource dataSource;

    public SmsLogRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public SmsLogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insertSmsLog(Long receiptId, Long churchId, String mobileNumber, String message, String provider,
                             SmsStatus status, String errorMessage, LocalDateTime sentAt, LocalDateTime createdAt) {
        String sql = """
                INSERT INTO sms_logs (
                    receipt_id, church_id, mobile_number, message, provider, status,
                    error_message, sent_at, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableLong(statement, 1, receiptId);
            setNullableLong(statement, 2, churchId);
            statement.setString(3, mobileNumber);
            statement.setString(4, message);
            setNullableString(statement, 5, provider);
            statement.setString(6, status.name());
            setNullableString(statement, 7, truncate(errorMessage, 500));
            setNullableTimestamp(statement, 8, sentAt);
            statement.setTimestamp(9, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to insert SMS log.", exception);
        }
    }

    public List<SmsLogDto> findByReceiptId(long receiptId) {
        String sql = baseSelect() + " WHERE receipt_id = ? ORDER BY created_at DESC";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            return mapLogs(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load SMS logs by receipt.", exception);
        }
    }

    public List<SmsLogDto> searchSmsLogs(String searchText, SmsStatus status) {
        StringBuilder sql = new StringBuilder(baseSelect()).append(" WHERE 1 = 1 ");
        List<Object> parameters = new ArrayList<>();

        if (searchText != null && !searchText.isBlank()) {
            sql.append("AND (mobile_number LIKE ? OR message LIKE ? OR provider LIKE ?) ");
            String term = "%" + searchText.strip() + "%";
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
        }
        if (status != null) {
            sql.append("AND status = ? ");
            parameters.add(status.name());
        }
        sql.append("ORDER BY created_at DESC");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            return mapLogs(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to search SMS logs.", exception);
        }
    }

    private String baseSelect() {
        return """
                SELECT id, receipt_id, church_id, mobile_number, message, provider, status,
                       error_message, sent_at, created_at
                FROM sms_logs
                """;
    }

    private List<SmsLogDto> mapLogs(PreparedStatement statement) throws SQLException {
        List<SmsLogDto> logs = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                SmsLogDto log = new SmsLogDto();
                log.setId(resultSet.getLong("id"));
                log.setReceiptId(nullableLong(resultSet, "receipt_id"));
                log.setChurchId(nullableLong(resultSet, "church_id"));
                log.setMobileNumber(resultSet.getString("mobile_number"));
                log.setMessage(resultSet.getString("message"));
                log.setProvider(resultSet.getString("provider"));
                log.setStatus(resultSet.getString("status"));
                log.setErrorMessage(resultSet.getString("error_message"));
                Timestamp sentAt = resultSet.getTimestamp("sent_at");
                log.setSentAt(sentAt == null ? null : sentAt.toLocalDateTime());
                Timestamp createdAt = resultSet.getTimestamp("created_at");
                log.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                logs.add(log);
            }
        }
        return logs;
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

    private void setNullableTimestamp(PreparedStatement statement, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public enum SmsStatus {
        SUCCESS,
        FAILED
    }
}
