package com.churchmanagement.repository;

import com.churchmanagement.entity.Receipt;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.exception.DatabaseException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.Optional;

public class ReceiptPrintRepository {
    public Optional<Receipt> lockReceiptForPrint(long receiptId, Connection connection) {
        String sql = """
                SELECT id, receipt_no, church_id, region_id, week_start_date, week_end_date,
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
            throw new DatabaseException("Unable to lock receipt for printing.", exception);
        }
    }

    public void updatePdfFilePath(long receiptId, String path, Connection connection) {
        String sql = "UPDATE receipts SET pdf_file_path = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, path);
            statement.setLong(2, receiptId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update receipt PDF path.", exception);
        }
    }

    public void markOriginalPrinted(long receiptId, long userId, Connection connection) {
        String sql = """
                UPDATE receipts
                SET original_printed = TRUE,
                    original_printed_at = CURRENT_TIMESTAMP,
                    original_printed_by_user_id = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, receiptId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to mark original receipt printed.", exception);
        }
    }

    public void incrementPrintAttempt(long receiptId, Connection connection) {
        String sql = "UPDATE receipts SET print_attempt_count = print_attempt_count + 1 WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update print attempt count.", exception);
        }
    }

    public void insertPrintLog(long receiptId, long printedByUserId, PrintType printType, PrintStatus status,
                               String errorMessage, LocalDateTime printedAt, Connection connection) {
        String sql = """
                INSERT INTO receipt_print_logs (
                    receipt_id, printed_by_user_id, print_type, status, error_message, printed_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            statement.setLong(2, printedByUserId);
            statement.setString(3, printType.name());
            statement.setString(4, status.name());
            if (errorMessage == null || errorMessage.isBlank()) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage);
            }
            statement.setTimestamp(6, Timestamp.valueOf(printedAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to insert receipt print log.", exception);
        }
    }

    private Receipt mapReceipt(ResultSet resultSet) throws SQLException {
        Receipt receipt = new Receipt();
        receipt.setId(resultSet.getLong("id"));
        receipt.setReceiptNo(resultSet.getString("receipt_no"));
        receipt.setChurchId(resultSet.getLong("church_id"));
        receipt.setRegionId(resultSet.getLong("region_id"));
        Date weekStart = resultSet.getDate("week_start_date");
        Date weekEnd = resultSet.getDate("week_end_date");
        receipt.setWeekStartDate(weekStart == null ? null : weekStart.toLocalDate());
        receipt.setWeekEndDate(weekEnd == null ? null : weekEnd.toLocalDate());
        Timestamp receiptDateTime = resultSet.getTimestamp("receipt_datetime");
        receipt.setReceiptDateTime(receiptDateTime == null ? null : receiptDateTime.toLocalDateTime());
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
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        receipt.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        receipt.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        return receipt;
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    public enum PrintType {
        ORIGINAL,
        INTERNAL_VIEW
    }

    public enum PrintStatus {
        SUCCESS,
        FAILED
    }
}
