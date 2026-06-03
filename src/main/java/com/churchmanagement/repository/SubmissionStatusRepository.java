package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.SubmissionDetailsDto;
import com.churchmanagement.dto.SubmissionStatusDto;
import com.churchmanagement.dto.SubmissionSummaryDto;
import com.churchmanagement.dto.SubmissionTotalsDto;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubmissionStatusRepository {
    private final DataSource dataSource;

    public SubmissionStatusRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public SubmissionStatusRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<SubmissionStatusDto> getWeeklySubmissionStatus(LocalDate weekStartDate, Long regionId, Long churchId,
                                                               String status) {
        StringBuilder sql = new StringBuilder(baseStatusSelect());
        List<Object> parameters = new ArrayList<>();
        parameters.add(Date.valueOf(weekStartDate));
        parameters.add(regionId);
        parameters.add(regionId);
        parameters.add(churchId);
        parameters.add(churchId);

        if (status != null && !"ALL".equals(status)) {
            sql.append(" HAVING submission_status = ? ");
            parameters.add(status);
        }
        sql.append(" ORDER BY rg.region_name, c.church_code");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            setParameters(statement, parameters);
            return mapStatusRows(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load weekly submission status.", exception);
        }
    }

    public SubmissionSummaryDto getWeeklySummary(LocalDate weekStartDate, Long regionId) {
        return getWeeklySummary(weekStartDate, regionId, null);
    }

    public SubmissionSummaryDto getWeeklySummary(LocalDate weekStartDate, Long regionId, Long churchId) {
        List<SubmissionStatusDto> rows = getWeeklySubmissionStatus(weekStartDate, regionId, churchId, "ALL");
        SubmissionSummaryDto summary = new SubmissionSummaryDto();
        summary.setTotalChurches(rows.size());
        summary.setSubmittedChurches(rows.stream().filter(SubmissionStatusDto::isSubmitted).count());
        summary.setPendingChurches(rows.stream().filter(SubmissionStatusDto::isPending).count());
        summary.setCancelledReceipts(rows.stream().filter(SubmissionStatusDto::isCancelled).count());
        summary.setLateSubmissions(rows.stream()
                .filter(SubmissionStatusDto::isSubmitted)
                .filter(SubmissionStatusDto::isLateSubmission)
                .count());
        summary.setTotalCollectionAmount(rows.stream()
                .filter(SubmissionStatusDto::isSubmitted)
                .map(SubmissionStatusDto::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.setSmsFailedCount(countSmsFailures(weekStartDate, regionId, churchId));
        summary.setUnprintedReceiptsCount(countUnprintedReceipts(weekStartDate, regionId, churchId));
        return summary;
    }

    public SubmissionTotalsDto getSubmissionTotals(LocalDate weekStartDate, Long regionId) {
        return getSubmissionTotals(weekStartDate, regionId, null);
    }

    public SubmissionTotalsDto getSubmissionTotals(LocalDate weekStartDate, Long regionId, Long churchId) {
        String sql = """
                SELECT
                    COALESCE(SUM(CASE WHEN ri.collection_type = 'OFFERTORY' THEN ri.amount ELSE 0 END), 0) AS total_offertory,
                    COALESCE(SUM(CASE WHEN ri.collection_type = 'TITHES' THEN ri.amount ELSE 0 END), 0) AS total_tithes,
                    COALESCE(SUM(CASE WHEN ri.collection_type = 'OTHER_DONATIONS' THEN ri.amount ELSE 0 END), 0) AS total_other_donations,
                    COALESCE(SUM(ri.amount), 0) AS grand_total
                FROM receipts r
                JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.week_start_date = ?
                  AND r.status = 'ACTIVE'
                  AND (? IS NULL OR r.region_id = ?)
                  AND (? IS NULL OR r.church_id = ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(weekStartDate));
            setNullableLong(statement, 2, regionId);
            setNullableLong(statement, 3, regionId);
            setNullableLong(statement, 4, churchId);
            setNullableLong(statement, 5, churchId);
            try (ResultSet resultSet = statement.executeQuery()) {
                SubmissionTotalsDto totals = new SubmissionTotalsDto();
                if (resultSet.next()) {
                    totals.setTotalOffertory(resultSet.getBigDecimal("total_offertory"));
                    totals.setTotalTithes(resultSet.getBigDecimal("total_tithes"));
                    totals.setTotalOtherDonations(resultSet.getBigDecimal("total_other_donations"));
                    totals.setGrandTotal(resultSet.getBigDecimal("grand_total"));
                }
                return totals;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load submission totals.", exception);
        }
    }

    public Optional<SubmissionDetailsDto> getSubmissionDetails(long receiptId) {
        String sql = """
                SELECT r.id, r.receipt_no, c.church_code, c.church_name,
                       r.week_start_date, r.week_end_date, r.submitted_by_name,
                       u.full_name AS submitted_by, r.receipt_datetime, r.status,
                       r.is_late_submission, r.original_printed,
                       latest_sms.status AS sms_status,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OFFERTORY' THEN ri.amount ELSE 0 END), 0) AS offertory_amount,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'TITHES' THEN ri.amount ELSE 0 END), 0) AS tithes_amount,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OTHER_DONATIONS' THEN ri.amount ELSE 0 END), 0) AS other_donations_amount,
                       COALESCE(SUM(ri.amount), 0) AS total_amount
                FROM receipts r
                JOIN churches c ON c.id = r.church_id
                JOIN users u ON u.id = r.issued_by_user_id
                LEFT JOIN receipt_items ri ON ri.receipt_id = r.id
                LEFT JOIN sms_logs latest_sms ON latest_sms.id = (
                    SELECT sl.id
                    FROM sms_logs sl
                    WHERE sl.receipt_id = r.id
                    ORDER BY sl.created_at DESC, sl.id DESC
                    LIMIT 1
                )
                WHERE r.id = ?
                GROUP BY r.id, r.receipt_no, c.church_code, c.church_name,
                         r.week_start_date, r.week_end_date, r.submitted_by_name,
                         u.full_name, r.receipt_datetime, r.status,
                         r.is_late_submission, r.original_printed, latest_sms.status
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                SubmissionDetailsDto details = new SubmissionDetailsDto();
                details.setReceiptId(resultSet.getLong("id"));
                details.setReceiptNo(resultSet.getString("receipt_no"));
                details.setChurchCode(resultSet.getString("church_code"));
                details.setChurchName(resultSet.getString("church_name"));
                details.setWeekStartDate(resultSet.getDate("week_start_date").toLocalDate());
                details.setWeekEndDate(resultSet.getDate("week_end_date").toLocalDate());
                details.setBearerName(resultSet.getString("submitted_by_name"));
                details.setSubmittedBy(resultSet.getString("submitted_by"));
                details.setSubmittedDate(resultSet.getTimestamp("receipt_datetime").toLocalDateTime());
                details.setReceiptStatus(ReceiptStatus.valueOf(resultSet.getString("status")));
                details.setLateSubmission(resultSet.getBoolean("is_late_submission"));
                details.setOriginalPrinted(resultSet.getBoolean("original_printed"));
                details.setSmsStatus(resultSet.getString("sms_status"));
                details.setOffertoryAmount(resultSet.getBigDecimal("offertory_amount"));
                details.setTithesAmount(resultSet.getBigDecimal("tithes_amount"));
                details.setOtherDonationsAmount(resultSet.getBigDecimal("other_donations_amount"));
                details.setTotalAmount(resultSet.getBigDecimal("total_amount"));
                details.setAuditHistory(findAuditHistory(connection, receiptId, details.getReceiptNo()));
                return Optional.of(details);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load submission details.", exception);
        }
    }

    private String baseStatusSelect() {
        return """
                SELECT c.id AS church_id, c.church_code, c.church_name, rg.region_name,
                       latest.id AS receipt_id, latest.receipt_no, latest.receipt_datetime,
                       CASE
                           WHEN latest.id IS NULL THEN 'PENDING'
                           WHEN latest.status = 'ACTIVE' THEN 'SUBMITTED'
                           ELSE 'CANCELLED'
                       END AS submission_status,
                       COALESCE(latest.is_late_submission, FALSE) AS is_late_submission,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OFFERTORY' THEN ri.amount ELSE 0 END), 0) AS offertory_amount,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'TITHES' THEN ri.amount ELSE 0 END), 0) AS tithes_amount,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OTHER_DONATIONS' THEN ri.amount ELSE 0 END), 0) AS other_donations_amount,
                       COALESCE(SUM(ri.amount), 0) AS total_amount
                FROM churches c
                JOIN regions rg ON rg.id = c.region_id
                LEFT JOIN receipts latest ON latest.church_id = c.id
                    AND latest.week_start_date = ?
                    AND NOT EXISTS (
                        SELECT 1
                        FROM receipts newer
                        WHERE newer.church_id = latest.church_id
                          AND newer.week_start_date = latest.week_start_date
                          AND (
                              newer.receipt_datetime > latest.receipt_datetime
                              OR (newer.receipt_datetime = latest.receipt_datetime AND newer.id > latest.id)
                          )
                    )
                LEFT JOIN receipt_items ri ON ri.receipt_id = latest.id
                WHERE (? IS NULL OR c.region_id = ?)
                  AND (? IS NULL OR c.id = ?)
                GROUP BY c.id, c.church_code, c.church_name, rg.region_name,
                         latest.id, latest.receipt_no, latest.receipt_datetime,
                         latest.status, latest.is_late_submission
                """;
    }

    private long countSmsFailures(LocalDate weekStartDate, Long regionId, Long churchId) {
        return countActiveReceipts(weekStartDate, regionId, churchId, """
                AND latest_sms.status = 'FAILED'
                """);
    }

    private long countUnprintedReceipts(LocalDate weekStartDate, Long regionId, Long churchId) {
        return countActiveReceipts(weekStartDate, regionId, churchId, """
                AND r.original_printed = FALSE
                """);
    }

    private long countActiveReceipts(LocalDate weekStartDate, Long regionId, Long churchId, String extraCondition) {
        String sql = """
                SELECT COUNT(DISTINCT r.id) AS total
                FROM receipts r
                LEFT JOIN sms_logs latest_sms ON latest_sms.id = (
                    SELECT sl.id
                    FROM sms_logs sl
                    WHERE sl.receipt_id = r.id
                    ORDER BY sl.created_at DESC, sl.id DESC
                    LIMIT 1
                )
                WHERE r.week_start_date = ?
                  AND r.status = 'ACTIVE'
                  AND (? IS NULL OR r.region_id = ?)
                  AND (? IS NULL OR r.church_id = ?)
                """ + extraCondition;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(weekStartDate));
            setNullableLong(statement, 2, regionId);
            setNullableLong(statement, 3, regionId);
            setNullableLong(statement, 4, churchId);
            setNullableLong(statement, 5, churchId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("total") : 0L;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load submission KPI.", exception);
        }
    }

    private List<String> findAuditHistory(Connection connection, long receiptId, String receiptNo) throws SQLException {
        String sql = """
                SELECT action, COALESCE(description, details) AS audit_description, created_at
                FROM activity_logs
                WHERE action IN (
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
                      record_id = ?
                      OR COALESCE(description, details) LIKE ?
                      OR COALESCE(description, details) LIKE ?
                  )
                ORDER BY created_at DESC
                LIMIT 20
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, String.valueOf(receiptId));
            statement.setString(2, "%" + receiptNo + "%");
            statement.setString(3, "%receipt_id: " + receiptId + "%");
            List<String> auditHistory = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Timestamp createdAt = resultSet.getTimestamp("created_at");
                    String timestamp = createdAt == null ? "" : createdAt.toLocalDateTime().toString() + " - ";
                    auditHistory.add(timestamp + resultSet.getString("action") + ": "
                            + resultSet.getString("audit_description"));
                }
            }
            return auditHistory;
        }
    }

    private List<SubmissionStatusDto> mapStatusRows(PreparedStatement statement) throws SQLException {
        List<SubmissionStatusDto> rows = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                SubmissionStatusDto dto = new SubmissionStatusDto();
                dto.setChurchId(resultSet.getLong("church_id"));
                dto.setChurchCode(resultSet.getString("church_code"));
                dto.setChurchName(resultSet.getString("church_name"));
                dto.setRegionName(resultSet.getString("region_name"));
                dto.setReceiptId(nullableLong(resultSet, "receipt_id"));
                dto.setReceiptNo(resultSet.getString("receipt_no"));
                Timestamp submittedDate = resultSet.getTimestamp("receipt_datetime");
                dto.setSubmittedDate(submittedDate == null ? null : submittedDate.toLocalDateTime());
                dto.setStatus(resultSet.getString("submission_status"));
                dto.setLateSubmission(resultSet.getBoolean("is_late_submission"));
                dto.setOffertoryAmount(resultSet.getBigDecimal("offertory_amount"));
                dto.setTithesAmount(resultSet.getBigDecimal("tithes_amount"));
                dto.setOtherDonationsAmount(resultSet.getBigDecimal("other_donations_amount"));
                dto.setTotalAmount(resultSet.getBigDecimal("total_amount"));
                rows.add(dto);
            }
        }
        return rows;
    }

    private void setParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        int index = 1;
        for (Object parameter : parameters) {
            if (parameter instanceof Date date) {
                statement.setDate(index, date);
            } else if (parameter instanceof Long value) {
                statement.setLong(index, value);
            } else if (parameter == null) {
                statement.setNull(index, java.sql.Types.BIGINT);
            } else {
                statement.setObject(index, parameter);
            }
            index++;
        }
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
