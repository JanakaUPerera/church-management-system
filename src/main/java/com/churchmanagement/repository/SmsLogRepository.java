package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.SmsLogDto;
import com.churchmanagement.dto.SmsLogSearchCriteria;
import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.enums.SmsSendStatus;
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
        SmsSendStatus sendStatus = toSendStatus(status);
        SmsDeliveryStatus deliveryStatus = sendStatus == SmsSendStatus.FAILED
                ? SmsDeliveryStatus.FAILED
                : toDeliveryStatus(status);
        insertSmsLog(receiptId, churchId, mobileNumber, message, provider, sendStatus, deliveryStatus, null, null,
                null, null, errorMessage, 1, createdAt, sentAt, createdAt);
    }

    public void insertSmsLog(Long receiptId, Long churchId, String mobileNumber, String message, String provider,
                             SmsSendStatus sendStatus, SmsDeliveryStatus deliveryStatus, String modemMessageReference,
                             String modemRawResponse, String deliveryReportRaw, String errorCode, String errorMessage,
                             int attemptCount, LocalDateTime lastAttemptAt, LocalDateTime sentAt,
                             LocalDateTime createdAt) {
        String sql = """
                INSERT INTO sms_logs (
                    sms_log_uuid, receipt_id, church_id, mobile_number, message, provider,
                    modem_message_reference, modem_raw_response, status, delivery_status, delivery_report_raw,
                    error_message, error_code, attempt_count, last_attempt_at, sent_at, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            setNullableLong(statement, 2, receiptId);
            setNullableLong(statement, 3, churchId);
            statement.setString(4, mobileNumber);
            statement.setString(5, message);
            setNullableString(statement, 6, provider);
            setNullableString(statement, 7, modemMessageReference);
            setNullableText(statement, 8, modemRawResponse);
            statement.setString(9, defaultSendStatus(sendStatus).name());
            statement.setString(10, defaultDeliveryStatus(deliveryStatus).name());
            setNullableText(statement, 11, deliveryReportRaw);
            setNullableString(statement, 12, truncate(errorMessage, 500));
            setNullableString(statement, 13, errorCode);
            statement.setInt(14, Math.max(1, attemptCount));
            setNullableTimestamp(statement, 15, lastAttemptAt);
            setNullableTimestamp(statement, 16, sentAt);
            statement.setTimestamp(17, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to insert SMS log.", exception);
        }
    }

    public Optional<SmsLogDto> findByIdForResend(long smsLogId) {
        return findById(smsLogId);
    }

    public boolean hasResend(long smsLogId) {
        String sql = "SELECT 1 FROM sms_logs WHERE resend_of_sms_log_id = ? LIMIT 1";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, smsLogId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check SMS resend status.", exception);
        }
    }

    public long insertResendSmsLog(SmsLogDto newLog, long originalSmsLogId, long resentByUserId,
                                   String resendReason) {
        String sql = """
                INSERT INTO sms_logs (
                    sms_log_uuid, receipt_id, church_id, mobile_number, message, provider,
                    modem_message_reference, modem_raw_response, status, delivery_status, delivery_report_raw,
                    error_message, error_code, attempt_count, last_attempt_at, sent_at, created_at, resend_of_sms_log_id,
                    resent_by_user_id, resend_reason
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, UUID.randomUUID().toString());
            setNullableLong(statement, 2, newLog.getReceiptId());
            setNullableLong(statement, 3, newLog.getChurchId());
            statement.setString(4, newLog.getMobileNumber());
            statement.setString(5, newLog.getMessage());
            setNullableString(statement, 6, newLog.getProvider());
            setNullableString(statement, 7, newLog.getModemMessageReference());
            setNullableText(statement, 8, newLog.getModemRawResponse());
            statement.setString(9, nullToDefault(newLog.getSendStatus(), SmsSendStatus.SENT.name()));
            statement.setString(10, nullToDefault(newLog.getDeliveryStatus(), SmsDeliveryStatus.UNKNOWN.name()));
            setNullableText(statement, 11, newLog.getDeliveryReportRaw());
            setNullableString(statement, 12, truncate(newLog.getErrorMessage(), 500));
            setNullableString(statement, 13, newLog.getErrorCode());
            statement.setInt(14, Math.max(1, newLog.getAttemptCount()));
            setNullableTimestamp(statement, 15, newLog.getLastAttemptAt());
            setNullableTimestamp(statement, 16, newLog.getSentAt());
            statement.setTimestamp(17, Timestamp.valueOf(newLog.getCreatedAt()));
            statement.setLong(18, originalSmsLogId);
            statement.setLong(19, resentByUserId);
            setNullableString(statement, 20, truncate(resendReason, 255));
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
        if (safeCriteria.getDeliveryStatus() != null) {
            sql.append("AND sl.delivery_status = ? ");
            parameters.add(safeCriteria.getDeliveryStatus().name());
        }
        if (safeCriteria.getMobileNumber() != null && !safeCriteria.getMobileNumber().isBlank()) {
            sql.append("AND sl.mobile_number LIKE ? ");
            parameters.add("%" + safeCriteria.getMobileNumber().strip() + "%");
        }
        if (safeCriteria.getReceiptNo() != null && !safeCriteria.getReceiptNo().isBlank()) {
            sql.append("AND r.receipt_no LIKE ? ");
            parameters.add("%" + safeCriteria.getReceiptNo().strip() + "%");
        }
        if (safeCriteria.getSearchText() != null && !safeCriteria.getSearchText().isBlank()) {
            sql.append("AND (r.receipt_no LIKE ? OR sl.mobile_number LIKE ?) ");
            String term = "%" + safeCriteria.getSearchText().strip() + "%";
            parameters.add(term);
            parameters.add(term);
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
            parameters.add(status == SmsStatus.SUCCESS ? SmsSendStatus.SENT.name() : status.name());
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
                       sl.mobile_number, sl.message, sl.provider, sl.modem_message_reference, sl.modem_raw_response,
                       sl.status, sl.delivery_status, sl.delivery_report_raw, sl.error_message, sl.error_code,
                       sl.attempt_count, sl.last_attempt_at, sl.sent_at, sl.created_at,
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
                log.setModemMessageReference(resultSet.getString("modem_message_reference"));
                log.setModemRawResponse(resultSet.getString("modem_raw_response"));
                log.setSendStatus(resultSet.getString("status"));
                log.setDeliveryStatus(resultSet.getString("delivery_status"));
                log.setDeliveryReportRaw(resultSet.getString("delivery_report_raw"));
                log.setErrorMessage(resultSet.getString("error_message"));
                log.setErrorCode(resultSet.getString("error_code"));
                log.setAttemptCount(resultSet.getInt("attempt_count"));
                Timestamp lastAttemptAt = resultSet.getTimestamp("last_attempt_at");
                log.setLastAttemptAt(lastAttemptAt == null ? null : lastAttemptAt.toLocalDateTime());
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

    private void setNullableText(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.LONGVARCHAR);
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

    private SmsSendStatus defaultSendStatus(SmsSendStatus status) {
        return status == null ? SmsSendStatus.SENT : status;
    }

    private SmsDeliveryStatus defaultDeliveryStatus(SmsDeliveryStatus status) {
        return status == null ? SmsDeliveryStatus.UNKNOWN : status;
    }

    private SmsSendStatus toSendStatus(SmsStatus status) {
        if (status == null || status == SmsStatus.SUCCESS
                || status == SmsStatus.DELIVERED
                || status == SmsStatus.DELIVERY_UNKNOWN
                || status == SmsStatus.DELIVERY_FAILED) {
            return SmsSendStatus.SENT;
        }
        return SmsSendStatus.valueOf(status.name());
    }

    private SmsDeliveryStatus toDeliveryStatus(SmsStatus status) {
        if (status == SmsStatus.DELIVERED) {
            return SmsDeliveryStatus.DELIVERED;
        }
        if (status == SmsStatus.DELIVERY_FAILED) {
            return SmsDeliveryStatus.FAILED;
        }
        if (status == SmsStatus.SKIPPED) {
            return SmsDeliveryStatus.NOT_SUPPORTED;
        }
        return SmsDeliveryStatus.UNKNOWN;
    }

    private String nullToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public enum SmsStatus {
        SUCCESS,
        QUEUED,
        SENDING,
        SENT,
        FAILED,
        SKIPPED,
        DELIVERY_UNKNOWN,
        DELIVERED,
        DELIVERY_FAILED
    }
}
