package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.ReceiptItemDto;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.entity.Receipt;
import com.churchmanagement.entity.ReceiptItem;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.enums.ReceiptLanguage;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
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

public class ReceiptRepository {
    private final DataSource dataSource;

    public ReceiptRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public ReceiptRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Note: this is an exact-match {@code week_start_date = ?} guard against
     * the currently configured week boundary (see
     * {@code receipt.week.identifier.day} and
     * {@link com.churchmanagement.util.WeekUtil}), so it cannot see an
     * active receipt filed under a boundary that was in effect before a
     * setting change — an accepted, intentional limitation, narrow in
     * practice since it only matters for the single week straddling a
     * cutover (see the "Accepted consequence" note in
     * {@code docs/superpowers/specs/2026-09-01-receipt-week-boundary-config-design.md}).
     */
    public boolean existsActiveReceiptForChurchAndWeek(long churchId, LocalDate weekStartDate) {
        String sql = """
                SELECT 1
                FROM receipts
                WHERE church_id = ? AND week_start_date = ? AND status = 'ACTIVE'
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, churchId);
            statement.setDate(2, Date.valueOf(weekStartDate));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check receipt existence.", exception);
        }
    }

    public Optional<Receipt> findActiveReceiptForChurchAndWeekForUpdate(long churchId, LocalDate weekStartDate,
                                                                        Connection connection) {
        String sql = """
                SELECT id, receipt_no, church_id, region_id, week_start_date, week_end_date,
                       church_service_date,
                       receipt_datetime, submitted_by_name, issued_by_user_id, status,
                       is_late_submission, late_submission_reason, corrected_from_receipt_id,
                       pdf_file_path, original_printed, original_printed_at,
                       original_printed_by_user_id, print_attempt_count, created_at, updated_at
                FROM receipts
                WHERE church_id = ? AND week_start_date = ? AND status = 'ACTIVE'
                FOR UPDATE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, churchId);
            statement.setDate(2, Date.valueOf(weekStartDate));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapReceipt(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to lock active receipt.", exception);
        }
    }

    public Optional<Receipt> findReceiptByIdForUpdate(long receiptId, Connection connection) {
        String sql = """
                SELECT id, receipt_no, church_id, region_id, week_start_date, week_end_date,
                       church_service_date,
                       receipt_datetime, submitted_by_name, issued_by_user_id, status,
                       is_late_submission, late_submission_reason, corrected_from_receipt_id,
                       pdf_file_path, original_printed, original_printed_at,
                       original_printed_by_user_id, print_attempt_count, created_at, updated_at
                FROM receipts
                WHERE id = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapReceipt(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to lock receipt.", exception);
        }
    }

    public Optional<Receipt> findReceiptById(long receiptId) {
        String sql = """
                SELECT id, receipt_no, church_id, region_id, week_start_date, week_end_date,
                       church_service_date,
                       receipt_datetime, submitted_by_name, issued_by_user_id, status,
                       is_late_submission, late_submission_reason, corrected_from_receipt_id,
                       pdf_file_path, original_printed, original_printed_at,
                       original_printed_by_user_id, print_attempt_count, created_at, updated_at
                FROM receipts
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapReceipt(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load receipt.", exception);
        }
    }

    public void updateReceiptStatus(long receiptId, ReceiptStatus status, LocalDateTime updatedAt,
                                    Connection connection) {
        String sql = """
                UPDATE receipts
                SET status = ?, updated_at = ?
                WHERE id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setTimestamp(2, Timestamp.valueOf(updatedAt));
            statement.setLong(3, receiptId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update receipt status.", exception);
        }
    }

    public long insertReceipt(Receipt receipt, Connection connection) {
        String sql = """
                INSERT INTO receipts (
                    receipt_no, church_id, region_id, week_start_date, week_end_date, church_service_date,
                    receipt_datetime, submitted_by_name, issued_by_user_id, status,
                    is_late_submission, late_submission_reason, corrected_from_receipt_id, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, receipt.getReceiptNo());
            statement.setLong(2, receipt.getChurchId());
            statement.setLong(3, receipt.getRegionId());
            statement.setDate(4, Date.valueOf(receipt.getWeekStartDate()));
            statement.setDate(5, Date.valueOf(receipt.getWeekEndDate()));
            statement.setDate(6, Date.valueOf(receipt.getChurchServiceDate()));
            statement.setTimestamp(7, Timestamp.valueOf(receipt.getReceiptDateTime()));
            statement.setString(8, receipt.getSubmittedByName());
            statement.setLong(9, receipt.getIssuedByUserId());
            statement.setString(10, receipt.getStatus().name());
            statement.setBoolean(11, receipt.isLateSubmission());
            setNullableString(statement, 12, receipt.getLateSubmissionReason());
            setNullableLong(statement, 13, receipt.getCorrectedFromReceiptId());
            statement.setTimestamp(14, Timestamp.valueOf(receipt.getCreatedAt()));
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
            throw new DatabaseException("Receipt id was not generated.");
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to insert receipt.", exception);
        }
    }

    public void insertReceiptItem(long receiptId, ReceiptItem item, Connection connection) {
        String sql = """
                INSERT INTO receipt_items (receipt_id, collection_type, amount, note, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            statement.setString(2, item.getCollectionType().name());
            statement.setBigDecimal(3, item.getAmount());
            setNullableString(statement, 4, item.getNote());
            statement.setTimestamp(5, Timestamp.valueOf(item.getCreatedAt()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to insert receipt item.", exception);
        }
    }

    public List<ReceiptResponseDto> searchReceipts(String searchText, LocalDate fromDate, LocalDate toDate) {
        StringBuilder sql = new StringBuilder(baseReceiptDetailsSelect()).append(" WHERE 1 = 1 ");
        List<Object> parameters = new ArrayList<>();

        if (searchText != null && !searchText.isBlank()) {
            sql.append("AND (r.receipt_no LIKE ? OR c.church_code LIKE ? OR c.church_name LIKE ?) ");
            String term = "%" + searchText.strip() + "%";
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
        }
        if (fromDate != null) {
            sql.append("AND r.week_start_date >= ? ");
            parameters.add(Date.valueOf(fromDate));
        }
        if (toDate != null) {
            sql.append("AND r.week_start_date <= ? ");
            parameters.add(Date.valueOf(toDate));
        }
        sql.append(receiptDetailsGroupBy()).append(" ORDER BY r.receipt_datetime DESC");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            setParameters(statement, parameters);
            return mapReceiptResponses(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to search receipts.", exception);
        }
    }

    public List<ReceiptResponseDto> searchReceipts(Long churchId, Long regionId, LocalDate weekStartDate,
                                                   String receiptNo, ReceiptStatus status) {
        return searchReceipts(churchId, regionId, weekStartDate, receiptNo, status, null, null, null);
    }

    public List<ReceiptResponseDto> searchReceipts(Long churchId, Long regionId, LocalDate weekStartDate,
                                                   String searchText, ReceiptStatus status,
                                                   Boolean lateSubmission, Boolean recreatedReceipt) {
        return searchReceipts(churchId, regionId, weekStartDate, searchText, status, lateSubmission,
                recreatedReceipt, null);
    }

    /**
     * Note on the {@code weekStartDate} filter: it's an exact-match
     * {@code week_start_date = ?} lookup, so a week selection only ever
     * matches receipts filed under the currently configured week boundary
     * (see {@code receipt.week.identifier.day} and
     * {@link com.churchmanagement.util.WeekUtil}) — receipts filed before
     * that setting's cutover carry a {@code week_start_date} under the
     * boundary in effect at the time and will not surface here. This is an
     * accepted, intentional limitation (see the "Accepted consequence" note
     * in
     * {@code docs/superpowers/specs/2026-09-01-receipt-week-boundary-config-design.md}),
     * not a bug.
     */
    public List<ReceiptResponseDto> searchReceipts(Long churchId, Long regionId, LocalDate weekStartDate,
                                                   String searchText, ReceiptStatus status,
                                                   Boolean lateSubmission, Boolean recreatedReceipt,
                                                   Boolean originalPrinted) {
        StringBuilder sql = new StringBuilder(baseReceiptDetailsSelect()).append(" WHERE 1 = 1 ");
        List<Object> parameters = new ArrayList<>();

        if (churchId != null) {
            sql.append("AND r.church_id = ? ");
            parameters.add(churchId);
        }
        if (regionId != null) {
            sql.append("AND r.region_id = ? ");
            parameters.add(regionId);
        }
        if (weekStartDate != null) {
            sql.append("AND r.week_start_date = ? ");
            parameters.add(Date.valueOf(weekStartDate));
        }
        if (searchText != null && !searchText.isBlank()) {
            sql.append("""
                    AND (
                        r.receipt_no LIKE ?
                        OR c.church_code LIKE ?
                        OR c.church_name LIKE ?
                        OR rg.region_code LIKE ?
                        OR rg.region_name LIKE ?
                        OR r.submitted_by_name LIKE ?
                    )
                    """);
            String term = "%" + searchText.strip() + "%";
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
            parameters.add(term);
        }
        if (status != null) {
            sql.append("AND r.status = ? ");
            parameters.add(status.name());
        }
        if (lateSubmission != null) {
            sql.append("AND r.is_late_submission = ? ");
            parameters.add(lateSubmission);
        }
        if (recreatedReceipt != null) {
            sql.append(recreatedReceipt
                    ? "AND r.corrected_from_receipt_id IS NOT NULL "
                    : "AND r.corrected_from_receipt_id IS NULL ");
        }
        if (originalPrinted != null) {
            sql.append("AND r.original_printed = ? ");
            parameters.add(originalPrinted);
        }

        sql.append(receiptDetailsGroupBy()).append(" ORDER BY r.receipt_datetime DESC");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            setParameters(statement, parameters);
            return mapReceiptResponses(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to search receipts.", exception);
        }
    }

    public List<ReceiptResponseDto> findCancelledReceiptsForCorrection(long churchId, LocalDate weekStartDate) {
        String sql = baseReceiptDetailsSelect()
                + """
                 WHERE r.church_id = ? AND r.week_start_date = ? AND r.status = 'CANCELLED'
                """
                + receiptDetailsGroupBy()
                + " ORDER BY r.receipt_datetime DESC";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, churchId);
            statement.setDate(2, Date.valueOf(weekStartDate));
            return mapReceiptResponses(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load cancelled receipts.", exception);
        }
    }

    public Optional<ReceiptResponseDto> findReceiptDetailsById(long receiptId) {
        String sql = baseReceiptDetailsSelect() + " WHERE r.id = ?" + receiptDetailsGroupBy();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            Optional<ReceiptResponseDto> response = mapReceiptResponses(statement).stream().findFirst();
            if (response.isPresent()) {
                response.get().setItems(findItemsByReceiptId(connection, receiptId));
            }
            return response;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load receipt details.", exception);
        }
    }

    public Optional<ReceiptResponseDto> findReceiptDetailsById(long receiptId, Connection connection) {
        String sql = baseReceiptDetailsSelect() + " WHERE r.id = ?" + receiptDetailsGroupBy();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            Optional<ReceiptResponseDto> response = mapReceiptResponses(statement).stream().findFirst();
            if (response.isPresent()) {
                response.get().setItems(findItemsByReceiptId(connection, receiptId));
            }
            return response;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load receipt details.", exception);
        }
    }

    private List<ReceiptItemDto> findItemsByReceiptId(Connection connection, long receiptId) throws SQLException {
        String sql = """
                SELECT collection_type, amount, note
                FROM receipt_items
                WHERE receipt_id = ?
                ORDER BY FIELD(collection_type, 'OFFERTORY', 'TITHES', 'OTHER_DONATIONS')
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            List<ReceiptItemDto> items = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    items.add(new ReceiptItemDto(
                            CollectionType.valueOf(resultSet.getString("collection_type")),
                            resultSet.getBigDecimal("amount"),
                            resultSet.getString("note")
                    ));
                }
            }
            return items;
        }
    }

    private List<ReceiptResponseDto> mapReceiptResponses(PreparedStatement statement) throws SQLException {
        List<ReceiptResponseDto> receipts = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ReceiptResponseDto dto = new ReceiptResponseDto();
                dto.setId(resultSet.getLong("id"));
                dto.setReceiptNo(resultSet.getString("receipt_no"));
                dto.setChurchCode(resultSet.getString("church_code"));
                dto.setChurchName(resultSet.getString("church_name"));
                dto.setReceiptLanguage(ReceiptLanguage.valueOf(resultSet.getString("receipt_language")));
                dto.setRegionCode(resultSet.getString("region_code"));
                dto.setRegionName(resultSet.getString("region_name"));
                dto.setWeekStartDate(resultSet.getDate("week_start_date").toLocalDate());
                dto.setWeekEndDate(resultSet.getDate("week_end_date").toLocalDate());
                Date churchServiceDate = resultSet.getDate("church_service_date");
                dto.setChurchServiceDate(churchServiceDate == null ? null : churchServiceDate.toLocalDate());
                dto.setReceiptDateTime(resultSet.getTimestamp("receipt_datetime").toLocalDateTime());
                dto.setSubmittedByName(resultSet.getString("submitted_by_name"));
                dto.setIssuedByFullName(resultSet.getString("issued_by_full_name"));
                dto.setStatus(ReceiptStatus.valueOf(resultSet.getString("status")));
                dto.setLateSubmission(resultSet.getBoolean("is_late_submission"));
                dto.setLateSubmissionReason(resultSet.getString("late_submission_reason"));
                dto.setCorrectedFromReceiptId(nullableLong(resultSet, "corrected_from_receipt_id"));
                dto.setCorrectedFromReceiptNo(resultSet.getString("corrected_from_receipt_no"));
                dto.setCorrectionReceiptNo(resultSet.getString("correction_receipt_no"));
                dto.setCancelReason(resultSet.getString("cancel_reason"));
                dto.setCancelledByFullName(resultSet.getString("cancelled_by_full_name"));
                Timestamp cancelledAt = resultSet.getTimestamp("cancelled_at");
                dto.setCancelledAt(cancelledAt == null ? null : cancelledAt.toLocalDateTime());
                dto.setPdfFilePath(resultSet.getString("pdf_file_path"));
                dto.setOriginalPrinted(resultSet.getBoolean("original_printed"));
                Timestamp originalPrintedAt = resultSet.getTimestamp("original_printed_at");
                dto.setOriginalPrintedAt(originalPrintedAt == null ? null : originalPrintedAt.toLocalDateTime());
                dto.setOriginalPrintedByFullName(resultSet.getString("original_printed_by_full_name"));
                dto.setPrintAttemptCount(resultSet.getInt("print_attempt_count"));
                dto.setSmsStatus(resultSet.getString("sms_status"));
                dto.setSmsErrorMessage(resultSet.getString("sms_error_message"));
                dto.setSmsProvider(resultSet.getString("sms_provider"));
                Timestamp smsSentAt = resultSet.getTimestamp("sms_sent_at");
                dto.setSmsSentAt(smsSentAt == null ? null : smsSentAt.toLocalDateTime());
                dto.setTotalAmount(resultSet.getBigDecimal("total_amount"));
                receipts.add(dto);
            }
        }
        return receipts;
    }

    private Receipt mapReceipt(ResultSet resultSet) throws SQLException {
        Receipt receipt = new Receipt();
        receipt.setId(resultSet.getLong("id"));
        receipt.setReceiptNo(resultSet.getString("receipt_no"));
        receipt.setChurchId(resultSet.getLong("church_id"));
        receipt.setRegionId(resultSet.getLong("region_id"));
        receipt.setWeekStartDate(resultSet.getDate("week_start_date").toLocalDate());
        receipt.setWeekEndDate(resultSet.getDate("week_end_date").toLocalDate());
        Date churchServiceDate = resultSet.getDate("church_service_date");
        receipt.setChurchServiceDate(churchServiceDate == null ? null : churchServiceDate.toLocalDate());
        receipt.setReceiptDateTime(resultSet.getTimestamp("receipt_datetime").toLocalDateTime());
        receipt.setSubmittedByName(resultSet.getString("submitted_by_name"));
        receipt.setIssuedByUserId(resultSet.getLong("issued_by_user_id"));
        receipt.setStatus(ReceiptStatus.valueOf(resultSet.getString("status")));
        receipt.setLateSubmission(resultSet.getBoolean("is_late_submission"));
        receipt.setLateSubmissionReason(resultSet.getString("late_submission_reason"));
        receipt.setCorrectedFromReceiptId(nullableLong(resultSet, "corrected_from_receipt_id"));
        receipt.setPdfFilePath(resultSet.getString("pdf_file_path"));
        receipt.setOriginalPrinted(resultSet.getBoolean("original_printed"));
        Timestamp originalPrintedAt = resultSet.getTimestamp("original_printed_at");
        receipt.setOriginalPrintedAt(originalPrintedAt == null ? null : originalPrintedAt.toLocalDateTime());
        receipt.setOriginalPrintedByUserId(nullableLong(resultSet, "original_printed_by_user_id"));
        receipt.setPrintAttemptCount(resultSet.getInt("print_attempt_count"));
        receipt.setCreatedAt(resultSet.getTimestamp("created_at").toLocalDateTime());
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        receipt.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        return receipt;
    }

    private String baseReceiptDetailsSelect() {
        return """
                SELECT r.id, r.receipt_no, c.church_code, c.church_name, c.receipt_language,
                       rg.region_code, rg.region_name,
                       r.week_start_date, r.week_end_date, r.church_service_date,
                       r.receipt_datetime, r.submitted_by_name,
                       u.full_name AS issued_by_full_name, r.status, r.is_late_submission,
                       r.late_submission_reason, r.corrected_from_receipt_id,
                       r.pdf_file_path, r.original_printed, r.original_printed_at,
                       r.print_attempt_count, printed_by.full_name AS original_printed_by_full_name,
                       corrected.receipt_no AS corrected_from_receipt_no,
                       correction.receipt_no AS correction_receipt_no,
                       rc.cancel_reason, cancelled_by.full_name AS cancelled_by_full_name,
                       rc.cancelled_at, latest_sms.status AS sms_status,
                       latest_sms.error_message AS sms_error_message,
                       latest_sms.provider AS sms_provider,
                       latest_sms.sent_at AS sms_sent_at,
                       COALESCE(SUM(ri.amount), 0) AS total_amount
                FROM receipts r
                JOIN churches c ON c.id = r.church_id
                JOIN regions rg ON rg.id = r.region_id
                JOIN users u ON u.id = r.issued_by_user_id
                LEFT JOIN receipt_items ri ON ri.receipt_id = r.id
                LEFT JOIN receipts corrected ON corrected.id = r.corrected_from_receipt_id
                LEFT JOIN receipts correction ON correction.corrected_from_receipt_id = r.id
                LEFT JOIN receipt_cancellations rc ON rc.receipt_id = r.id
                LEFT JOIN users cancelled_by ON cancelled_by.id = rc.cancelled_by_user_id
                LEFT JOIN users printed_by ON printed_by.id = r.original_printed_by_user_id
                LEFT JOIN sms_logs latest_sms ON latest_sms.id = (
                    SELECT sl.id
                    FROM sms_logs sl
                    WHERE sl.receipt_id = r.id
                    ORDER BY sl.created_at DESC, sl.id DESC
                    LIMIT 1
                )
                """;
    }

    private String receiptDetailsGroupBy() {
        return """
                 GROUP BY r.id, r.receipt_no, c.church_code, c.church_name, c.receipt_language,
                          rg.region_code, rg.region_name,
                          r.week_start_date, r.week_end_date, r.church_service_date,
                          r.receipt_datetime, r.submitted_by_name,
                          u.full_name, r.status, r.is_late_submission, r.late_submission_reason,
                          r.corrected_from_receipt_id, r.pdf_file_path, r.original_printed,
                          r.original_printed_at, r.print_attempt_count, printed_by.full_name,
                          corrected.receipt_no, correction.receipt_no,
                          rc.cancel_reason, cancelled_by.full_name, rc.cancelled_at,
                          latest_sms.status, latest_sms.error_message, latest_sms.provider,
                          latest_sms.sent_at
                """;
    }

    private void setParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            Object parameter = parameters.get(index);
            if (parameter instanceof Date date) {
                statement.setDate(index + 1, date);
            } else {
                statement.setObject(index + 1, parameter);
            }
        }
    }

    private void setNullableString(PreparedStatement statement, int parameterIndex, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(parameterIndex, Types.VARCHAR);
        } else {
            statement.setString(parameterIndex, value.strip());
        }
    }

    private void setNullableLong(PreparedStatement statement, int parameterIndex, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(parameterIndex, Types.BIGINT);
        } else {
            statement.setLong(parameterIndex, value);
        }
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
