package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.SmsLogDto;
import com.churchmanagement.dto.SmsLogSearchCriteria;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                    sms_log_uuid, receipt_id, church_id, mobile_number, message, provider, status,
                    error_message, sent_at, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            setNullableLong(statement, 2, receiptId);
            setNullableLong(statement, 3, churchId);
            statement.setString(4, mobileNumber);
            statement.setString(5, message);
            setNullableString(statement, 6, provider);
            statement.setString(7, status.name());
            setNullableString(statement, 8, truncate(errorMessage, 500));
            setNullableTimestamp(statement, 9, sentAt);
            statement.setTimestamp(10, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to insert SMS log.", exception);
        }
    }

    public Optional<SmsLogDto> findByIdForResend(long smsLogId) {
        return findById(smsLogId);
    }

    public long insertResendSmsLog(SmsLogDto newLog, long originalSmsLogId, long resentByUserId,
                                   String resendReason) {
        String sql = """
                INSERT INTO sms_logs (
                    sms_log_uuid, receipt_id, church_id, mobile_number, message, provider, status,
                    error_message, sent_at, created_at, resend_of_sms_log_id,
                    resent_by_user_id, resend_reason
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, UUID.randomUUID().toString());
            setNullableLong(statement, 2, newLog.getReceiptId());
            setNullableLong(statement, 3, newLog.getChurchId());
            statement.setString(4, newLog.getMobileNumber());
            statement.setString(5, newLog.getMessage());
            setNullableString(statement, 6, newLog.getProvider());
            statement.setString(7, newLog.getStatus());
            setNullableString(statement, 8, truncate(newLog.getErrorMessage(), 500));
            setNullableTimestamp(statement, 9, newLog.getSentAt());
            statement.setTimestamp(10, Timestamp.valueOf(newLog.getCreatedAt()));
            statement.setLong(11, originalSmsLogId);
            statement.setLong(12, resentByUserId);
            setNullableString(statement, 13, truncate(resendReason, 255));
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
            throw new DatabaseException("SMS resend log id was not generated.");
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to insert SMS resend log.", exception);
        }
    }

    public Optional<SmsLogDto> findByUuid(String smsLogUuid) {
        if (smsLogUuid == null || smsLogUuid.isBlank()) {
            return Optional.empty();
        }
        String sql = baseSelect() + " WHERE sl.sms_log_uuid = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, smsLogUuid.strip());
            return mapLogs(statement).stream().findFirst();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load SMS log.", exception);
        }
    }

    public List<SmsLogDto> findByReceiptId(long receiptId) {
        String sql = baseSelect() + " WHERE sl.receipt_id = ? ORDER BY sl.created_at DESC";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            return mapLogs(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load SMS logs by receipt.", exception);
        }
    }

    public Optional<SmsLogDto> findById(long id) {
        String sql = baseSelect() + " WHERE sl.id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return mapLogs(statement).stream().findFirst();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load SMS log.", exception);
        }
    }

    public List<SmsLogDto> searchSmsLogs(SmsLogSearchCriteria criteria) {
        SmsLogSearchCriteria safeCriteria = criteria == null ? new SmsLogSearchCriteria() : criteria;
        StringBuilder sql = new StringBuilder(baseSelect()).append(" WHERE 1 = 1 ");
        List<Object> parameters = new ArrayList<>();

        if (safeCriteria.getDateFrom() != null) {
            sql.append("AND sl.created_at >= ? ");
            parameters.add(safeCriteria.getDateFrom().atStartOfDay());
        }
        if (safeCriteria.getDateTo() != null) {
            sql.append("AND sl.created_at < ? ");
            parameters.add(safeCriteria.getDateTo().plusDays(1).atStartOfDay());
        }
        if (safeCriteria.getChurchId() != null) {
            sql.append("AND sl.church_id = ? ");
            parameters.add(safeCriteria.getChurchId());
        }
        if (safeCriteria.getStatus() != null) {
            sql.append("AND sl.status = ? ");
            parameters.add(safeCriteria.getStatus().name());
        }
        if (safeCriteria.getMobileNumber() != null && !safeCriteria.getMobileNumber().isBlank()) {
            sql.append("AND sl.mobile_number LIKE ? ");
            parameters.add("%" + safeCriteria.getMobileNumber().strip() + "%");
        }
        if (safeCriteria.getReceiptNo() != null && !safeCriteria.getReceiptNo().isBlank()) {
            sql.append("AND r.receipt_no LIKE ? ");
            parameters.add("%" + safeCriteria.getReceiptNo().strip() + "%");
        }
        sql.append("ORDER BY sl.created_at DESC LIMIT ?");
        parameters.add(safeCriteria.limitOrDefault(500));

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            setParameters(statement, parameters);
            return mapLogs(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to search SMS logs.", exception);
        }
    }

    public List<SmsLogDto> searchSmsLogs(String searchText, SmsStatus status) {
        StringBuilder sql = new StringBuilder(baseSelect()).append(" WHERE 1 = 1 ");
        List<Object> parameters = new ArrayList<>();

        if (searchText != null && !searchText.isBlank()) {
            sql.append("AND (sl.mobile_number LIKE ? OR sl.message LIKE ? OR sl.provider LIKE ?) ");
            String term = "%" + searchText.strip() + "%";
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
        }
        if (status != null) {
            sql.append("AND sl.status = ? ");
            parameters.add(status.name());
        }
        sql.append("ORDER BY sl.created_at DESC");

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
                SELECT sl.id, sl.sms_log_uuid, sl.receipt_id, sl.church_id, r.receipt_no,
                       c.church_code, c.church_name,
                       sl.mobile_number, sl.message, sl.provider, sl.status,
                       sl.error_message, sl.sent_at, sl.created_at,
                       sl.resend_of_sms_log_id, resent_by.full_name AS resent_by_user_full_name,
                       original_sms.sms_log_uuid AS resend_of_sms_log_uuid,
                       sl.resend_reason
                FROM sms_logs sl
                LEFT JOIN receipts r ON r.id = sl.receipt_id
                LEFT JOIN churches c ON c.id = sl.church_id
                LEFT JOIN users resent_by ON resent_by.id = sl.resent_by_user_id
                LEFT JOIN sms_logs original_sms ON original_sms.id = sl.resend_of_sms_log_id
                """;
    }

    private List<SmsLogDto> mapLogs(PreparedStatement statement) throws SQLException {
        List<SmsLogDto> logs = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                SmsLogDto log = new SmsLogDto();
                log.setId(resultSet.getLong("id"));
                log.setSmsLogUuid(resultSet.getString("sms_log_uuid"));
                log.setReceiptId(nullableLong(resultSet, "receipt_id"));
                log.setChurchId(nullableLong(resultSet, "church_id"));
                log.setReceiptNo(resultSet.getString("receipt_no"));
                log.setChurchCode(resultSet.getString("church_code"));
                log.setChurchName(resultSet.getString("church_name"));
                log.setMobileNumber(resultSet.getString("mobile_number"));
                log.setMessage(resultSet.getString("message"));
                log.setProvider(resultSet.getString("provider"));
                log.setStatus(resultSet.getString("status"));
                log.setErrorMessage(resultSet.getString("error_message"));
                Timestamp sentAt = resultSet.getTimestamp("sent_at");
                log.setSentAt(sentAt == null ? null : sentAt.toLocalDateTime());
                Timestamp createdAt = resultSet.getTimestamp("created_at");
                log.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                log.setResendOfSmsLogId(nullableLong(resultSet, "resend_of_sms_log_id"));
                log.setResendOfSmsLogUuid(resultSet.getString("resend_of_sms_log_uuid"));
                log.setResentByUserFullName(resultSet.getString("resent_by_user_full_name"));
                log.setResendReason(resultSet.getString("resend_reason"));
                logs.add(log);
            }
        }
        return logs;
    }

    private void setParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            Object parameter = parameters.get(index);
            if (parameter instanceof LocalDate date) {
                statement.setDate(index + 1, java.sql.Date.valueOf(date));
            } else if (parameter instanceof LocalDateTime dateTime) {
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
        FAILED,
        SKIPPED
    }
}
