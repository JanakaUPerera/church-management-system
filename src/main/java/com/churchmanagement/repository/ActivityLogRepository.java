package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.ActivityLogDto;
import com.churchmanagement.dto.ActivityLogSearchCriteria;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.util.SystemDateTimeFormatter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ActivityLogRepository {
    private final DataSource dataSource;
    private final SystemDateTimeFormatter dateTimeFormatter = new SystemDateTimeFormatter();

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

    public List<String> findReceiptAuditHistory(long receiptId, String receiptNo) {
        String sql = """
                SELECT al.action, COALESCE(al.description, al.details) AS audit_description,
                       al.created_at, COALESCE(u.full_name, al.username) AS actor_name
                FROM activity_logs al
                LEFT JOIN users u ON u.id = al.user_id
                WHERE al.action IN (
                    'RECEIPT_CREATED',
                    'CORRECTED_RECEIPT_CREATED',
                    'RECEIPT_CANCELLED',
                    'RECEIPT_PDF_GENERATED',
                    'RECEIPT_ORIGINAL_PRINTED',
                    'RECEIPT_PRINT_FAILED',
                    'RECEIPT_PRINT_BLOCKED_ALREADY_PRINTED',
                    'RECEIPT_PRINT_BLOCKED_CANCELLED',
                    'SMS_SENT',
                    'SMS_FAILED',
                    'SMS_SKIPPED',
                    'SMS_RESENT_SUCCESS',
                    'SMS_RESENT_FAILED'
                )
                  AND (
                      al.record_id = ?
                      OR COALESCE(al.description, al.details) LIKE ?
                      OR COALESCE(al.description, al.details) LIKE ?
                  )
                ORDER BY al.created_at DESC
                LIMIT 20
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, String.valueOf(receiptId));
            statement.setString(2, "%" + receiptNo + "%");
            statement.setString(3, "%receipt_id: " + receiptId + "%");
            List<String> auditHistory = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Timestamp createdAt = resultSet.getTimestamp("created_at");
                    LocalDateTime auditDateTime = createdAt == null ? null : createdAt.toLocalDateTime();
                    String actorName = resultSet.getString("actor_name");
                    auditHistory.add(formatAuditDateTime(auditDateTime) + "\t" + formatReceiptAuditDescription(
                            resultSet.getString("action"),
                            resultSet.getString("audit_description")) + "\t" + nullToDash(actorName));
                }
            }
            return auditHistory;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load receipt audit history.", exception);
        }
    }

    private String formatAuditDateTime(LocalDateTime createdAt) {
        return dateTimeFormatter.formatDateTime(createdAt);
    }

    private String formatReceiptAuditDescription(String action, String description) {
        String actionText = switch (action == null ? "" : action) {
            case "RECEIPT_CREATED" -> "Receipt created";
            case "CORRECTED_RECEIPT_CREATED" -> "Corrected receipt created";
            case "RECEIPT_CANCELLED" -> "Receipt cancelled";
            case "RECEIPT_PDF_GENERATED" -> "Receipt PDF generated";
            case "RECEIPT_ORIGINAL_PRINTED" -> "Original receipt printed";
            case "RECEIPT_PRINT_FAILED" -> "Receipt print failed";
            case "RECEIPT_PRINT_BLOCKED_ALREADY_PRINTED" -> "Print blocked because original was already printed";
            case "RECEIPT_PRINT_BLOCKED_CANCELLED" -> "Print blocked because receipt was cancelled";
            case "SMS_SENT" -> "SMS sent";
            case "SMS_FAILED" -> "SMS failed";
            case "SMS_SKIPPED" -> "SMS skipped";
            case "SMS_RESENT_SUCCESS" -> "SMS resent successfully";
            case "SMS_RESENT_FAILED" -> "SMS resend failed";
            default -> humanizeKey(action);
        };
        String details = humanizeDetails(description);
        return actionText + (details.isBlank() ? "" : " (" + details + ")");
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String humanizeDetails(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        Map<String, String> values = parseDetails(description);
        if (values.isEmpty()) {
            return description;
        }
        return values.entrySet().stream()
                .filter(entry -> shouldShowAuditDetail(entry.getKey(), values))
                .filter(entry -> !entry.getValue().isBlank())
                .map(entry -> humanizeKey(entry.getKey()) + ": " + humanizeValue(entry.getKey(), entry.getValue()))
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private boolean shouldShowAuditDetail(String key, Map<String, String> values) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.strip().toLowerCase();
        if ("receipt_id".equals(normalized) && hasValue(values, "receipt_no")) {
            return false;
        }
        if ("church_id".equals(normalized) && hasValue(values, "church_code")) {
            return false;
        }
        if ("corrected_from_receipt_id".equals(normalized) && hasValue(values, "corrected_from_receipt_no")) {
            return false;
        }
        return !normalized.equals("id")
                && !normalized.endsWith("_id")
                && !normalized.endsWith(" id");
    }

    private boolean hasValue(Map<String, String> values, String key) {
        String value = values.get(key);
        return value != null && !value.isBlank();
    }

    private Map<String, String> parseDetails(String description) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String part : description.split(",\\s*")) {
            int separatorIndex = part.indexOf(':');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = part.substring(0, separatorIndex).strip();
            String value = part.substring(separatorIndex + 1).strip();
            if (!key.isBlank()) {
                values.put(key, value);
            }
        }
        return values;
    }

    private String humanizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String normalized = key.strip().toLowerCase().replace('_', ' ');
        StringBuilder builder = new StringBuilder();
        for (String word : normalized.split("\\s+")) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private String humanizeValue(String key, String value) {
        if ("true".equalsIgnoreCase(value)) {
            return "Yes";
        }
        if ("false".equalsIgnoreCase(value)) {
            return "No";
        }
        if ("<not generated>".equalsIgnoreCase(value)) {
            return "Not generated";
        }
        if (isDateTimeKey(key)) {
            String formattedDateTime = tryFormatDateTime(value);
            if (formattedDateTime != null) {
                return formattedDateTime;
            }
            String formattedDate = tryFormatDate(value);
            if (formattedDate != null) {
                return formattedDate;
            }
        }
        return value;
    }

    private boolean isDateTimeKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.strip().toLowerCase();
        return normalized.contains("date")
                || normalized.contains("time")
                || normalized.endsWith("_at")
                || normalized.endsWith(" at");
    }

    private String tryFormatDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return dateTimeFormatter.formatDateTime(LocalDateTime.parse(value.strip()));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String tryFormatDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return dateTimeFormatter.formatDate(LocalDate.parse(value.strip()));
        } catch (DateTimeParseException exception) {
            return null;
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
