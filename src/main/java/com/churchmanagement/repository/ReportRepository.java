package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.report.*;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReportRepository {
    private final DataSource dataSource;

    public ReportRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public ReportRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<WeeklyChurchCollectionReportDto> getWeeklyChurchCollectionReport(ReportSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id receipt_id, rg.region_code, rg.region_name, c.church_code, c.church_name,
                       r.week_start_date, r.receipt_no,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OFFERTORY' THEN ri.amount ELSE 0 END), 0) offertory_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'TITHES' THEN ri.amount ELSE 0 END), 0) tithes_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OTHER_DONATIONS' THEN ri.amount ELSE 0 END), 0) other_donations_total,
                       COALESCE(SUM(ri.amount), 0) grand_total
                FROM receipts r
                JOIN regions rg ON rg.id = r.region_id
                JOIN churches c ON c.id = r.church_id
                JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'ACTIVE'
                """);
        List<Object> parameters = new ArrayList<>();
        appendWeekFilter(sql, parameters, criteria);
        appendRegionChurchReceiptFilters(sql, parameters, criteria, "r");
        sql.append("""
                GROUP BY r.id, rg.region_code, rg.region_name, c.church_code, c.church_name, r.week_start_date, r.receipt_no
                ORDER BY rg.region_code, c.church_code, r.receipt_no
                """);
        appendPagination(sql, parameters, criteria);
        return query(sql.toString(), parameters, this::mapWeeklyChurchCollection);
    }

    public List<WeeklyRegionSummaryReportDto> getWeeklyRegionSummaryReport(ReportSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT rg.id region_id, rg.region_code, rg.region_name, ? week_start_date,
                       COUNT(DISTINCT c.id) total_churches,
                       COUNT(DISTINCT r.church_id) submitted_churches,
                       COUNT(DISTINCT c.id) - COUNT(DISTINCT r.church_id) missing_churches,
                       COUNT(DISTINCT CASE WHEN r.is_late_submission = TRUE THEN r.id END) late_submissions,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OFFERTORY' THEN ri.amount ELSE 0 END), 0) offertory_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'TITHES' THEN ri.amount ELSE 0 END), 0) tithes_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OTHER_DONATIONS' THEN ri.amount ELSE 0 END), 0) other_donations_total,
                       COALESCE(SUM(ri.amount), 0) grand_total
                FROM regions rg
                JOIN churches c ON c.region_id = rg.id AND c.status = 'ACTIVE'
                LEFT JOIN receipts r ON r.church_id = c.id AND r.status = 'ACTIVE' AND r.week_start_date = ?
                LEFT JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE rg.status = 'ACTIVE'
                """);
        LocalDate weekStart = criteria.getWeekStartDate();
        List<Object> parameters = new ArrayList<>(List.of(Date.valueOf(weekStart), Date.valueOf(weekStart)));
        appendRegionFilter(sql, parameters, criteria, "rg");
        sql.append("""
                GROUP BY rg.id, rg.region_code, rg.region_name
                ORDER BY rg.region_code
                """);
        appendPagination(sql, parameters, criteria);
        return query(sql.toString(), parameters, this::mapWeeklyRegionSummary);
    }

    public List<SubmissionStatusReportDto> getSubmissionStatusReport(ReportSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id receipt_id, rg.region_name, c.church_code, c.church_name,
                       COALESCE(r.week_start_date, ?) week_start_date,
                       CASE WHEN r.id IS NULL THEN 'MISSING' ELSE 'SUBMITTED' END status,
                       r.receipt_no, r.receipt_datetime submitted_at,
                       COALESCE(r.is_late_submission, FALSE) is_late_submission,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OFFERTORY' THEN ri.amount ELSE 0 END), 0) offertory_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'TITHES' THEN ri.amount ELSE 0 END), 0) tithes_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OTHER_DONATIONS' THEN ri.amount ELSE 0 END), 0) other_donations_total,
                       COALESCE(SUM(ri.amount), 0) grand_total
                FROM churches c
                JOIN regions rg ON rg.id = c.region_id
                LEFT JOIN receipts r ON r.church_id = c.id
                    AND r.week_start_date = ?
                    AND r.status = 'ACTIVE'
                LEFT JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE c.status = 'ACTIVE'
                  AND rg.status = 'ACTIVE'
                """);
        LocalDate weekStart = criteria.getWeekStartDate();
        List<Object> parameters = new ArrayList<>(List.of(Date.valueOf(weekStart), Date.valueOf(weekStart)));
        appendRegionFilter(sql, parameters, criteria, "rg");
        appendChurchFilter(sql, parameters, criteria, "c");
        appendReceiptNoFilter(sql, parameters, criteria, "r");
        appendSubmissionStatusFilter(sql, parameters, criteria);
        sql.append("""
                GROUP BY c.id, r.id, rg.region_name, c.church_code, c.church_name, r.week_start_date, r.receipt_no,
                         r.receipt_datetime, r.is_late_submission
                ORDER BY rg.region_name, c.church_code
                """);
        appendPagination(sql, parameters, criteria);
        return query(sql.toString(), parameters, this::mapSubmissionStatus);
    }

    public List<MissingSubmissionReportDto> getMissingSubmissionReport(ReportSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id church_id, rg.region_code, rg.region_name, c.church_code, c.church_name,
                       ? week_start_date, c.sms_mobile_number
                FROM churches c
                JOIN regions rg ON rg.id = c.region_id
                WHERE c.status = 'ACTIVE'
                  AND rg.status = 'ACTIVE'
                  AND NOT EXISTS (
                      SELECT 1 FROM receipts r
                      WHERE r.church_id = c.id
                        AND r.week_start_date = ?
                        AND r.status = 'ACTIVE'
                  )
                """);
        LocalDate weekStart = criteria.getWeekStartDate();
        List<Object> parameters = new ArrayList<>(List.of(Date.valueOf(weekStart), Date.valueOf(weekStart)));
        appendRegionFilter(sql, parameters, criteria, "rg");
        appendChurchFilter(sql, parameters, criteria, "c");
        sql.append("ORDER BY rg.region_code, c.church_code ");
        appendPagination(sql, parameters, criteria);
        return query(sql.toString(), parameters, this::mapMissingSubmission);
    }

    public List<LateSubmissionReportDto> getLateSubmissionReport(ReportSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id receipt_id, rg.region_name, c.church_code, c.church_name, r.week_start_date,
                       r.receipt_no, r.receipt_datetime submitted_at, r.late_submission_reason reason,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OFFERTORY' THEN ri.amount ELSE 0 END), 0) offertory_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'TITHES' THEN ri.amount ELSE 0 END), 0) tithes_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OTHER_DONATIONS' THEN ri.amount ELSE 0 END), 0) other_donations_total,
                       COALESCE(SUM(ri.amount), 0) grand_total
                FROM receipts r
                JOIN regions rg ON rg.id = r.region_id
                JOIN churches c ON c.id = r.church_id
                LEFT JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'ACTIVE'
                  AND r.is_late_submission = TRUE
                """);
        List<Object> parameters = new ArrayList<>();
        appendWeekFilter(sql, parameters, criteria);
        appendRegionChurchReceiptFilters(sql, parameters, criteria, "r");
        sql.append("""
                GROUP BY r.id, rg.region_name, c.church_code, c.church_name, r.week_start_date,
                         r.receipt_no, r.receipt_datetime, r.late_submission_reason
                ORDER BY r.week_start_date DESC, rg.region_name, c.church_code
                """);
        appendPagination(sql, parameters, criteria);
        return query(sql.toString(), parameters, this::mapLateSubmission);
    }

    public List<CollectionReportDto> getCollectionReport(ReportSearchCriteria criteria, boolean churchWise,
                                                         boolean monthly) {
        StringBuilder sql = new StringBuilder("""
                SELECT YEAR(r.week_start_date) report_year,
                       %s report_month,
                       rg.region_name,
                       %s church_name,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OFFERTORY' THEN ri.amount ELSE 0 END), 0) offertory_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'TITHES' THEN ri.amount ELSE 0 END), 0) tithes_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OTHER_DONATIONS' THEN ri.amount ELSE 0 END), 0) other_donations_total,
                       COALESCE(SUM(ri.amount), 0) grand_total
                FROM receipts r
                JOIN regions rg ON rg.id = r.region_id
                JOIN churches c ON c.id = r.church_id
                JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'ACTIVE'
                """.formatted(monthly ? "MONTH(r.week_start_date)" : "NULL",
                churchWise ? "c.church_name" : "NULL"));
        List<Object> parameters = new ArrayList<>();
        appendDateRangeFilter(sql, parameters, criteria, "r.week_start_date");
        appendRegionChurchReceiptFilters(sql, parameters, criteria, "r");
        sql.append("GROUP BY YEAR(r.week_start_date), rg.region_name");
        if (monthly) {
            sql.append(", MONTH(r.week_start_date)");
        }
        if (churchWise) {
            sql.append(", c.church_name");
        }
        sql.append(" ORDER BY report_year");
        if (monthly) {
            sql.append(", report_month");
        }
        sql.append(", rg.region_name");
        if (churchWise) {
            sql.append(", c.church_name");
        }
        sql.append(' ');
        appendPagination(sql, parameters, criteria);
        return query(sql.toString(), parameters, rs -> mapCollection(rs, churchWise, monthly));
    }

    public List<ChurchProgressReportDto> getChurchProgressReport(ReportSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id church_id, rg.region_name, c.church_code, c.church_name,
                       COUNT(DISTINCT r.week_start_date) submitted_weeks,
                       GREATEST(0, TIMESTAMPDIFF(WEEK, ?, ?) + 1 - COUNT(DISTINCT r.week_start_date)) missing_weeks,
                       COUNT(DISTINCT CASE WHEN r.is_late_submission = TRUE THEN r.id END) late_count,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OFFERTORY' THEN ri.amount ELSE 0 END), 0) offertory_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'TITHES' THEN ri.amount ELSE 0 END), 0) tithes_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OTHER_DONATIONS' THEN ri.amount ELSE 0 END), 0) other_donations_total,
                       COALESCE(SUM(ri.amount), 0) total_collections
                FROM churches c
                JOIN regions rg ON rg.id = c.region_id
                LEFT JOIN receipts r ON r.church_id = c.id AND r.status = 'ACTIVE'
                    AND r.week_start_date >= ? AND r.week_start_date <= ?
                LEFT JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE c.status = 'ACTIVE'
                """);
        List<Object> parameters = rangeParametersForProgress(criteria);
        appendRegionFilter(sql, parameters, criteria, "rg");
        appendChurchFilter(sql, parameters, criteria, "c");
        sql.append("""
                GROUP BY c.id, rg.region_name, c.church_code, c.church_name
                ORDER BY rg.region_name, c.church_code
                """);
        appendPagination(sql, parameters, criteria);
        return query(sql.toString(), parameters, this::mapChurchProgress);
    }

    public List<RegionProgressReportDto> getRegionProgressReport(ReportSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT rg.id region_id, rg.region_code, rg.region_name, COUNT(DISTINCT c.id) total_churches,
                       COUNT(DISTINCT CONCAT(r.church_id, ':', r.week_start_date)) submitted_weeks,
                       GREATEST(0, COUNT(DISTINCT c.id) * (TIMESTAMPDIFF(WEEK, ?, ?) + 1)
                            - COUNT(DISTINCT CONCAT(r.church_id, ':', r.week_start_date))) missing_weeks,
                       COUNT(DISTINCT CASE WHEN r.is_late_submission = TRUE THEN r.id END) late_count,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OFFERTORY' THEN ri.amount ELSE 0 END), 0) offertory_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'TITHES' THEN ri.amount ELSE 0 END), 0) tithes_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OTHER_DONATIONS' THEN ri.amount ELSE 0 END), 0) other_donations_total,
                       COALESCE(SUM(ri.amount), 0) total_collections
                FROM regions rg
                JOIN churches c ON c.region_id = rg.id AND c.status = 'ACTIVE'
                LEFT JOIN receipts r ON r.church_id = c.id AND r.status = 'ACTIVE'
                    AND r.week_start_date >= ? AND r.week_start_date <= ?
                LEFT JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE rg.status = 'ACTIVE'
                """);
        List<Object> parameters = rangeParametersForProgress(criteria);
        appendRegionFilter(sql, parameters, criteria, "rg");
        sql.append("""
                GROUP BY rg.id, rg.region_code, rg.region_name
                ORDER BY rg.region_code
                """);
        appendPagination(sql, parameters, criteria);
        return query(sql.toString(), parameters, this::mapRegionProgress);
    }

    public List<CancelledReceiptReportDto> getCancelledReceiptReport(ReportSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id receipt_id, r.receipt_no, rg.region_name, c.church_name,
                       COALESCE(SUM(ri.amount), 0) grand_total,
                       u.full_name cancelled_by, rc.cancelled_at, rc.cancel_reason
                FROM receipts r
                JOIN receipt_cancellations rc ON rc.receipt_id = r.id
                JOIN users u ON u.id = rc.cancelled_by_user_id
                JOIN regions rg ON rg.id = r.region_id
                JOIN churches c ON c.id = r.church_id
                LEFT JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'CANCELLED'
                """);
        List<Object> parameters = new ArrayList<>();
        appendDateRangeFilter(sql, parameters, criteria, "DATE(rc.cancelled_at)");
        appendRegionChurchReceiptFilters(sql, parameters, criteria, "r");
        sql.append("""
                GROUP BY r.id, r.receipt_no, rg.region_name, c.church_name, u.full_name, rc.cancelled_at, rc.cancel_reason
                ORDER BY rc.cancelled_at DESC
                """);
        appendPagination(sql, parameters, criteria);
        return query(sql.toString(), parameters, this::mapCancelledReceipt);
    }

    public List<ReceiptPrintStatusReportDto> getReceiptPrintStatusReport(ReportSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id receipt_id, r.receipt_no, c.church_name, r.original_printed,
                       u.full_name printed_by, r.original_printed_at printed_at, r.print_attempt_count
                FROM receipts r
                JOIN churches c ON c.id = r.church_id
                LEFT JOIN users u ON u.id = r.original_printed_by_user_id
                WHERE r.status = 'ACTIVE'
                """);
        List<Object> parameters = new ArrayList<>();
        appendDateRangeFilter(sql, parameters, criteria, "r.week_start_date");
        appendRegionChurchReceiptFilters(sql, parameters, criteria, "r");
        appendStatusTextFilter(sql, parameters, criteria, "PRINTED", "UNPRINTED", "r.original_printed");
        sql.append("ORDER BY r.week_start_date DESC, r.receipt_no ");
        appendPagination(sql, parameters, criteria);
        return query(sql.toString(), parameters, this::mapReceiptPrintStatus);
    }

    public List<SmsDeliveryReportDto> getSmsDeliveryReport(ReportSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT sl.id sms_log_id, r.receipt_no, c.church_name, sl.mobile_number, sl.status send_status,
                       sl.delivery_status, sl.attempt_count retry_count, sl.modem_message_reference modem_reference,
                       sl.created_at
                FROM sms_logs sl
                LEFT JOIN receipts r ON r.id = sl.receipt_id
                LEFT JOIN churches c ON c.id = sl.church_id
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        appendDateRangeFilter(sql, parameters, criteria, "DATE(sl.created_at)");
        appendChurchFilter(sql, parameters, criteria, "c");
        appendSmsStatusFilter(sql, parameters, criteria);
        appendReceiptNoFilter(sql, parameters, criteria, "r");
        sql.append("ORDER BY sl.created_at DESC ");
        appendPagination(sql, parameters, criteria);
        return query(sql.toString(), parameters, this::mapSmsDelivery);
    }

    public List<UserActivityReportDto> getUserActivityReport(ReportSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT al.id, COALESCE(al.username, u.username) username, u.full_name,
                       al.action, al.module, al.entity_name, al.entity_id,
                       COALESCE(al.details, al.description) details, al.created_at activity_at
                FROM activity_logs al
                LEFT JOIN users u ON u.id = al.user_id
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        appendDateRangeFilter(sql, parameters, criteria, "DATE(al.created_at)");
        if (criteria.getUserId() != null) {
            sql.append("AND al.user_id = ? ");
            parameters.add(criteria.getUserId());
        }
        sql.append("ORDER BY al.created_at DESC, al.id DESC ");
        appendPagination(sql, parameters, criteria);
        return query(sql.toString(), parameters, this::mapUserActivity);
    }

    public List<BackupRestoreHistoryReportDto> getBackupRestoreHistoryReport(ReportSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, action_type, file_name, status, user_full_name, action_at, error_message
                FROM (
                    SELECT bl.id, CONCAT('BACKUP_', bl.backup_type) action_type, bl.file_name, bl.status,
                           u.full_name user_full_name, bl.created_at action_at, bl.error_message
                    FROM backup_logs bl
                    LEFT JOIN users u ON u.id = bl.created_by_user_id
                    UNION ALL
                    SELECT rl.id, 'RESTORE' action_type, rl.backup_file_name file_name, rl.status,
                           u.full_name user_full_name, rl.restored_at action_at, rl.error_message
                    FROM restore_logs rl
                    LEFT JOIN users u ON u.id = rl.restored_by_user_id
                ) history
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        appendDateRangeFilter(sql, parameters, criteria, "DATE(action_at)");
        appendStatusFilter(sql, parameters, criteria, "status");
        sql.append("ORDER BY action_at DESC ");
        appendPagination(sql, parameters, criteria);
        return query(sql.toString(), parameters, this::mapBackupRestoreHistory);
    }

    public ReportSummaryTotals getSummaryTotals(ReportSearchCriteria criteria) {
        if (criteria.getReportType() == ReportType.CANCELLED_RECEIPT) {
            return new ReportSummaryTotals();
        }
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(CASE WHEN ri.collection_type = 'OFFERTORY' THEN ri.amount ELSE 0 END), 0) offertory_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'TITHES' THEN ri.amount ELSE 0 END), 0) tithes_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OTHER_DONATIONS' THEN ri.amount ELSE 0 END), 0) other_donations_total,
                       COALESCE(SUM(ri.amount), 0) grand_total
                FROM receipts r
                JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'ACTIVE'
                """);
        List<Object> parameters = new ArrayList<>();
        if (criteria.getWeekStartDate() != null && isWeekly(criteria.getReportType())) {
            appendWeekFilter(sql, parameters, criteria);
        } else {
            appendDateRangeFilter(sql, parameters, criteria, "r.week_start_date");
        }
        appendRegionChurchReceiptFilters(sql, parameters, criteria, "r");
        return queryOne(sql.toString(), parameters, this::mapSummaryTotals);
    }

    private boolean isWeekly(ReportType reportType) {
        return reportType == ReportType.WEEKLY_CHURCH_COLLECTION
                || reportType == ReportType.WEEKLY_REGION_SUMMARY
                || reportType == ReportType.SUBMISSION_STATUS
                || reportType == ReportType.LATE_SUBMISSION;
    }

    private void appendDateRangeFilter(StringBuilder sql, List<Object> parameters, ReportSearchCriteria criteria, String column) {
        if (criteria.getDateFrom() != null) {
            sql.append("AND ").append(column).append(" >= ? ");
            parameters.add(Date.valueOf(criteria.getDateFrom()));
        }
        if (criteria.getDateTo() != null) {
            sql.append("AND ").append(column).append(" <= ? ");
            parameters.add(Date.valueOf(criteria.getDateTo()));
        }
    }

    private void appendWeekFilter(StringBuilder sql, List<Object> parameters, ReportSearchCriteria criteria) {
        if (criteria.getWeekStartDate() != null) {
            sql.append("AND r.week_start_date = ? ");
            parameters.add(Date.valueOf(criteria.getWeekStartDate()));
        }
    }

    private void appendRegionChurchReceiptFilters(StringBuilder sql, List<Object> parameters, ReportSearchCriteria criteria, String receiptAlias) {
        appendRegionFilter(sql, parameters, criteria, receiptAlias);
        appendChurchFilter(sql, parameters, criteria, receiptAlias);
        appendReceiptNoFilter(sql, parameters, criteria, receiptAlias);
    }

    private void appendRegionFilter(StringBuilder sql, List<Object> parameters, ReportSearchCriteria criteria, String alias) {
        if (criteria.getRegionId() != null) {
            String column = "rg".equals(alias) ? "id" : "region_id";
            sql.append("AND ").append(alias).append('.').append(column).append(" = ? ");
            parameters.add(criteria.getRegionId());
        }
    }

    private void appendChurchFilter(StringBuilder sql, List<Object> parameters, ReportSearchCriteria criteria, String alias) {
        if (criteria.getChurchId() != null) {
            String column = "c".equals(alias) ? "id" : "church_id";
            sql.append("AND ").append(alias).append('.').append(column).append(" = ? ");
            parameters.add(criteria.getChurchId());
        }
    }

    private void appendReceiptNoFilter(StringBuilder sql, List<Object> parameters, ReportSearchCriteria criteria, String alias) {
        if (criteria.getReceiptNo() != null && !criteria.getReceiptNo().isBlank()) {
            sql.append("AND ").append(alias).append(".receipt_no LIKE ? ");
            parameters.add("%" + criteria.getReceiptNo().strip() + "%");
        }
    }

    private void appendStatusFilter(StringBuilder sql, List<Object> parameters, ReportSearchCriteria criteria, String column) {
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank() && !"ALL".equalsIgnoreCase(criteria.getStatus())) {
            sql.append("AND ").append(column).append(" = ? ");
            parameters.add(criteria.getStatus());
        }
    }

    private void appendSmsStatusFilter(StringBuilder sql, List<Object> parameters, ReportSearchCriteria criteria) {
        if (criteria.getStatus() == null || criteria.getStatus().isBlank()
                || "ALL".equalsIgnoreCase(criteria.getStatus())) {
            return;
        }

        String status = criteria.getStatus().strip().toUpperCase(java.util.Locale.ROOT);
        switch (status) {
            case "SUCCESS" -> {
                sql.append("AND sl.status = 'SENT' AND sl.delivery_status = 'DELIVERED' ");
            }
            case "DELIVERED" -> {
                sql.append("AND sl.delivery_status = 'DELIVERED' ");
            }
            case "DELIVERY_UNKNOWN" -> {
                sql.append("AND sl.delivery_status = 'UNKNOWN' ");
            }
            case "DELIVERY_FAILED" -> {
                sql.append("AND sl.delivery_status = 'FAILED' ");
            }
            default -> {
                sql.append("AND sl.status = ? ");
                parameters.add(status);
            }
        }
    }

    private void appendStatusTextFilter(StringBuilder sql, List<Object> parameters, ReportSearchCriteria criteria, String expected) {
        if (criteria.getStatus() != null && !"ALL".equalsIgnoreCase(criteria.getStatus())
                && !expected.equalsIgnoreCase(criteria.getStatus())) {
            sql.append("AND 1 = 0 ");
        }
    }

    private void appendSubmissionStatusFilter(StringBuilder sql, List<Object> parameters, ReportSearchCriteria criteria) {
        if (criteria.getStatus() == null || criteria.getStatus().isBlank()
                || "ALL".equalsIgnoreCase(criteria.getStatus())) {
            return;
        }
        if ("SUBMITTED".equalsIgnoreCase(criteria.getStatus())) {
            sql.append("AND r.id IS NOT NULL ");
        } else if ("MISSING".equalsIgnoreCase(criteria.getStatus())) {
            sql.append("AND r.id IS NULL ");
        } else if ("LATE".equalsIgnoreCase(criteria.getStatus())
                || "LATE_SUBMISSION".equalsIgnoreCase(criteria.getStatus())) {
            sql.append("AND r.id IS NOT NULL AND r.is_late_submission = TRUE ");
        } else if ("ON_TIME".equalsIgnoreCase(criteria.getStatus())) {
            sql.append("AND r.id IS NOT NULL AND COALESCE(r.is_late_submission, FALSE) = FALSE ");
        } else {
            sql.append("AND 1 = 0 ");
        }
    }

    private void appendStatusTextFilter(StringBuilder sql, List<Object> parameters, ReportSearchCriteria criteria,
                                        String printed, String unprinted, String column) {
        if (criteria.getStatus() == null || criteria.getStatus().isBlank() || "ALL".equalsIgnoreCase(criteria.getStatus())) {
            return;
        }
        if (printed.equalsIgnoreCase(criteria.getStatus())) {
            sql.append("AND ").append(column).append(" = TRUE ");
        } else if (unprinted.equalsIgnoreCase(criteria.getStatus())) {
            sql.append("AND ").append(column).append(" = FALSE ");
        } else {
            sql.append("AND 1 = 0 ");
        }
    }

    private void appendPagination(StringBuilder sql, List<Object> parameters, ReportSearchCriteria criteria) {
        sql.append("LIMIT ? OFFSET ? ");
        parameters.add(criteria.getLimit());
        parameters.add(criteria.getOffset());
    }

    private List<Object> rangeParametersForProgress(ReportSearchCriteria criteria) {
        LocalDate from = criteria.getDateFrom();
        LocalDate to = criteria.getDateTo();
        return new ArrayList<>(List.of(Date.valueOf(from), Date.valueOf(to), Date.valueOf(from), Date.valueOf(to)));
    }

    private <T> List<T> query(String sql, List<Object> parameters, RowMapper<T> mapper) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            List<T> rows = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(mapper.map(resultSet));
                }
            }
            return rows;
        } catch (SQLException exception) {
            throw new DatabaseException(reportQueryErrorMessage(sql, exception), exception);
        }
    }

    private <T> T queryOne(String sql, List<Object> parameters, RowMapper<T> mapper) {
        return query(sql, parameters, mapper).stream().findFirst().orElseThrow();
    }

    private void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index));
        }
    }

    private String reportQueryErrorMessage(String sql, SQLException exception) {
        return "Unable to load report data. SQLState=" + exception.getSQLState()
                + ", ErrorCode=" + exception.getErrorCode()
                + ", Detail=" + exception.getMessage()
                + ", SQL=" + compactSql(sql);
    }

    private String compactSql(String sql) {
        return sql == null ? "" : sql.replaceAll("\\s+", " ").strip();
    }

    private WeeklyChurchCollectionReportDto mapWeeklyChurchCollection(ResultSet rs) throws SQLException {
        WeeklyChurchCollectionReportDto dto = new WeeklyChurchCollectionReportDto();
        dto.setReceiptId(rs.getLong("receipt_id"));
        dto.setRegionCode(rs.getString("region_code"));
        dto.setRegionName(rs.getString("region_name"));
        dto.setChurchCode(rs.getString("church_code"));
        dto.setChurchName(rs.getString("church_name"));
        dto.setWeekStartDate(localDate(rs, "week_start_date"));
        dto.setReceiptNo(rs.getString("receipt_no"));
        dto.setOffertoryTotal(rs.getBigDecimal("offertory_total"));
        dto.setTithesTotal(rs.getBigDecimal("tithes_total"));
        dto.setOtherDonationsTotal(rs.getBigDecimal("other_donations_total"));
        dto.setGrandTotal(rs.getBigDecimal("grand_total"));
        return dto;
    }

    private WeeklyRegionSummaryReportDto mapWeeklyRegionSummary(ResultSet rs) throws SQLException {
        WeeklyRegionSummaryReportDto dto = new WeeklyRegionSummaryReportDto();
        dto.setRegionId(rs.getLong("region_id"));
        dto.setRegionCode(rs.getString("region_code"));
        dto.setRegionName(rs.getString("region_name"));
        dto.setWeekStartDate(localDate(rs, "week_start_date"));
        dto.setTotalChurches(rs.getLong("total_churches"));
        dto.setSubmittedChurches(rs.getLong("submitted_churches"));
        dto.setMissingChurches(rs.getLong("missing_churches"));
        dto.setLateSubmissions(rs.getLong("late_submissions"));
        dto.setOffertoryTotal(rs.getBigDecimal("offertory_total"));
        dto.setTithesTotal(rs.getBigDecimal("tithes_total"));
        dto.setOtherDonationsTotal(rs.getBigDecimal("other_donations_total"));
        dto.setGrandTotal(rs.getBigDecimal("grand_total"));
        return dto;
    }

    private SubmissionStatusReportDto mapSubmissionStatus(ResultSet rs) throws SQLException {
        SubmissionStatusReportDto dto = new SubmissionStatusReportDto();
        dto.setReceiptId(nullableLong(rs, "receipt_id"));
        dto.setRegionName(rs.getString("region_name"));
        dto.setChurchCode(rs.getString("church_code"));
        dto.setChurchName(rs.getString("church_name"));
        dto.setWeekStartDate(localDate(rs, "week_start_date"));
        dto.setStatus(rs.getString("status"));
        dto.setReceiptNo(rs.getString("receipt_no"));
        dto.setSubmittedAt(localDateTime(rs, "submitted_at"));
        dto.setLateSubmission(rs.getBoolean("is_late_submission"));
        dto.setOffertoryTotal(rs.getBigDecimal("offertory_total"));
        dto.setTithesTotal(rs.getBigDecimal("tithes_total"));
        dto.setOtherDonationsTotal(rs.getBigDecimal("other_donations_total"));
        dto.setGrandTotal(rs.getBigDecimal("grand_total"));
        return dto;
    }

    private MissingSubmissionReportDto mapMissingSubmission(ResultSet rs) throws SQLException {
        MissingSubmissionReportDto dto = new MissingSubmissionReportDto();
        dto.setChurchId(rs.getLong("church_id"));
        dto.setRegionCode(rs.getString("region_code"));
        dto.setRegionName(rs.getString("region_name"));
        dto.setChurchCode(rs.getString("church_code"));
        dto.setChurchName(rs.getString("church_name"));
        dto.setWeekStartDate(localDate(rs, "week_start_date"));
        dto.setSmsMobileNumber(rs.getString("sms_mobile_number"));
        return dto;
    }

    private LateSubmissionReportDto mapLateSubmission(ResultSet rs) throws SQLException {
        LateSubmissionReportDto dto = new LateSubmissionReportDto();
        dto.setReceiptId(rs.getLong("receipt_id"));
        dto.setRegionName(rs.getString("region_name"));
        dto.setChurchCode(rs.getString("church_code"));
        dto.setChurchName(rs.getString("church_name"));
        dto.setWeekStartDate(localDate(rs, "week_start_date"));
        dto.setReceiptNo(rs.getString("receipt_no"));
        dto.setSubmittedAt(localDateTime(rs, "submitted_at"));
        dto.setReason(rs.getString("reason"));
        dto.setOffertoryTotal(rs.getBigDecimal("offertory_total"));
        dto.setTithesTotal(rs.getBigDecimal("tithes_total"));
        dto.setOtherDonationsTotal(rs.getBigDecimal("other_donations_total"));
        dto.setGrandTotal(rs.getBigDecimal("grand_total"));
        return dto;
    }

    private CollectionReportDto mapCollection(ResultSet rs, boolean churchWise, boolean monthly) throws SQLException {
        CollectionReportDto dto = new CollectionReportDto();
        dto.setChurchWise(churchWise);
        dto.setMonthly(monthly);
        dto.setYear(rs.getInt("report_year"));
        dto.setMonth(monthly ? rs.getInt("report_month") : null);
        dto.setRegionName(rs.getString("region_name"));
        dto.setChurchName(rs.getString("church_name"));
        dto.setOffertoryTotal(rs.getBigDecimal("offertory_total"));
        dto.setTithesTotal(rs.getBigDecimal("tithes_total"));
        dto.setOtherDonationsTotal(rs.getBigDecimal("other_donations_total"));
        dto.setGrandTotal(rs.getBigDecimal("grand_total"));
        return dto;
    }

    private ChurchProgressReportDto mapChurchProgress(ResultSet rs) throws SQLException {
        ChurchProgressReportDto dto = new ChurchProgressReportDto();
        dto.setChurchId(rs.getLong("church_id"));
        dto.setRegionName(rs.getString("region_name"));
        dto.setChurchCode(rs.getString("church_code"));
        dto.setChurchName(rs.getString("church_name"));
        dto.setSubmittedWeeks(rs.getLong("submitted_weeks"));
        dto.setMissingWeeks(rs.getLong("missing_weeks"));
        dto.setLateCount(rs.getLong("late_count"));
        dto.setOffertoryTotal(rs.getBigDecimal("offertory_total"));
        dto.setTithesTotal(rs.getBigDecimal("tithes_total"));
        dto.setOtherDonationsTotal(rs.getBigDecimal("other_donations_total"));
        dto.setTotalCollections(rs.getBigDecimal("total_collections"));
        return dto;
    }

    private RegionProgressReportDto mapRegionProgress(ResultSet rs) throws SQLException {
        RegionProgressReportDto dto = new RegionProgressReportDto();
        dto.setRegionId(rs.getLong("region_id"));
        dto.setRegionCode(rs.getString("region_code"));
        dto.setRegionName(rs.getString("region_name"));
        dto.setTotalChurches(rs.getLong("total_churches"));
        dto.setSubmittedWeeks(rs.getLong("submitted_weeks"));
        dto.setMissingWeeks(rs.getLong("missing_weeks"));
        dto.setLateCount(rs.getLong("late_count"));
        dto.setOffertoryTotal(rs.getBigDecimal("offertory_total"));
        dto.setTithesTotal(rs.getBigDecimal("tithes_total"));
        dto.setOtherDonationsTotal(rs.getBigDecimal("other_donations_total"));
        dto.setTotalCollections(rs.getBigDecimal("total_collections"));
        return dto;
    }

    private CancelledReceiptReportDto mapCancelledReceipt(ResultSet rs) throws SQLException {
        CancelledReceiptReportDto dto = new CancelledReceiptReportDto();
        dto.setReceiptId(rs.getLong("receipt_id"));
        dto.setReceiptNo(rs.getString("receipt_no"));
        dto.setRegionName(rs.getString("region_name"));
        dto.setChurchName(rs.getString("church_name"));
        dto.setGrandTotal(rs.getBigDecimal("grand_total"));
        dto.setCancelledBy(rs.getString("cancelled_by"));
        dto.setCancelledAt(localDateTime(rs, "cancelled_at"));
        dto.setCancelReason(rs.getString("cancel_reason"));
        return dto;
    }

    private ReceiptPrintStatusReportDto mapReceiptPrintStatus(ResultSet rs) throws SQLException {
        ReceiptPrintStatusReportDto dto = new ReceiptPrintStatusReportDto();
        dto.setReceiptId(rs.getLong("receipt_id"));
        dto.setReceiptNo(rs.getString("receipt_no"));
        dto.setChurchName(rs.getString("church_name"));
        dto.setOriginalPrinted(rs.getBoolean("original_printed"));
        dto.setPrintedBy(rs.getString("printed_by"));
        dto.setPrintedAt(localDateTime(rs, "printed_at"));
        dto.setPrintAttempts(rs.getInt("print_attempt_count"));
        return dto;
    }

    private SmsDeliveryReportDto mapSmsDelivery(ResultSet rs) throws SQLException {
        SmsDeliveryReportDto dto = new SmsDeliveryReportDto();
        dto.setSmsLogId(rs.getLong("sms_log_id"));
        dto.setReceiptNo(rs.getString("receipt_no"));
        dto.setChurchName(rs.getString("church_name"));
        dto.setMobileNumber(rs.getString("mobile_number"));
        dto.setSendStatus(rs.getString("send_status"));
        dto.setDeliveryStatus(rs.getString("delivery_status"));
        dto.setRetryCount(rs.getInt("retry_count"));
        dto.setModemReference(rs.getString("modem_reference"));
        dto.setCreatedAt(localDateTime(rs, "created_at"));
        return dto;
    }

    private UserActivityReportDto mapUserActivity(ResultSet rs) throws SQLException {
        UserActivityReportDto dto = new UserActivityReportDto();
        dto.setId(rs.getLong("id"));
        dto.setUsername(rs.getString("username"));
        dto.setFullName(rs.getString("full_name"));
        dto.setAction(rs.getString("action"));
        dto.setModule(rs.getString("module"));
        dto.setEntityName(rs.getString("entity_name"));
        dto.setEntityId(nullableLong(rs, "entity_id"));
        dto.setDetails(rs.getString("details"));
        dto.setActivityAt(localDateTime(rs, "activity_at"));
        return dto;
    }

    private BackupRestoreHistoryReportDto mapBackupRestoreHistory(ResultSet rs) throws SQLException {
        BackupRestoreHistoryReportDto dto = new BackupRestoreHistoryReportDto();
        dto.setId(rs.getLong("id"));
        dto.setActionType(rs.getString("action_type"));
        dto.setFileName(rs.getString("file_name"));
        dto.setStatus(rs.getString("status"));
        dto.setUserFullName(rs.getString("user_full_name"));
        dto.setActionAt(localDateTime(rs, "action_at"));
        dto.setErrorMessage(rs.getString("error_message"));
        return dto;
    }

    private ReportSummaryTotals mapSummaryTotals(ResultSet rs) throws SQLException {
        ReportSummaryTotals totals = new ReportSummaryTotals();
        totals.setOffertoryTotal(rs.getBigDecimal("offertory_total"));
        totals.setTithesTotal(rs.getBigDecimal("tithes_total"));
        totals.setOtherDonationsTotal(rs.getBigDecimal("other_donations_total"));
        totals.setGrandTotal(rs.getBigDecimal("grand_total"));
        return totals;
    }

    private LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }
}
