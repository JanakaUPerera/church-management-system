package com.churchmanagement.service;

import com.churchmanagement.dto.CreateReceiptRequest;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.entity.Church;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.ActivityLogRepository;

import java.math.BigDecimal;

public class ActivityLogService {
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String NAVIGATE_REGIONS = "NAVIGATE_REGIONS";
    public static final String NAVIGATE_CHURCHES = "NAVIGATE_CHURCHES";
    public static final String NAVIGATE_RECEIPTS = "NAVIGATE_RECEIPTS";
    public static final String NAVIGATE_REPORTS = "NAVIGATE_REPORTS";
    public static final String NAVIGATE_USERS = "NAVIGATE_USERS";
    public static final String NAVIGATE_BACKUP_RESTORE = "NAVIGATE_BACKUP_RESTORE";
    public static final String NAVIGATE_ACTIVITY_LOGS = "NAVIGATE_ACTIVITY_LOGS";
    public static final String REGION_CREATED = "REGION_CREATED";
    public static final String REGION_UPDATED = "REGION_UPDATED";
    public static final String REGION_ACTIVATED = "REGION_ACTIVATED";
    public static final String REGION_DEACTIVATED = "REGION_DEACTIVATED";
    public static final String CHURCH_CREATED = "CHURCH_CREATED";
    public static final String CHURCH_UPDATED = "CHURCH_UPDATED";
    public static final String CHURCH_ACTIVATED = "CHURCH_ACTIVATED";
    public static final String CHURCH_DEACTIVATED = "CHURCH_DEACTIVATED";
    public static final String RECEIPT_NUMBER_GENERATED = "RECEIPT_NUMBER_GENERATED";
    public static final String RECEIPT_CREATED = "RECEIPT_CREATED";
    public static final String RECEIPT_CREATE_FAILED = "RECEIPT_CREATE_FAILED";
    public static final String RECEIPT_CANCELLED = "RECEIPT_CANCELLED";
    public static final String CORRECTED_RECEIPT_CREATED = "CORRECTED_RECEIPT_CREATED";
    public static final String RECEIPT_PDF_GENERATED = "RECEIPT_PDF_GENERATED";
    public static final String RECEIPT_ORIGINAL_PRINTED = "RECEIPT_ORIGINAL_PRINTED";
    public static final String RECEIPT_PRINT_FAILED = "RECEIPT_PRINT_FAILED";
    public static final String RECEIPT_PRINT_BLOCKED_ALREADY_PRINTED = "RECEIPT_PRINT_BLOCKED_ALREADY_PRINTED";
    public static final String RECEIPT_PRINT_BLOCKED_CANCELLED = "RECEIPT_PRINT_BLOCKED_CANCELLED";

    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService() {
        this(new ActivityLogRepository());
    }

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    public void logLoginSuccess(long userId, String username) {
        log(userId, LOGIN_SUCCESS, "Successful login for username: " + username);
    }

    public void logLoginFailed(String username, String reason) {
        log(null, LOGIN_FAILED, "Failed login for username: " + sanitizeUsername(username) + ". Reason: " + reason);
    }

    public void logLogout(long userId, String username) {
        log(userId, LOGOUT, "Logout for username: " + username);
    }

    public void logNavigation(long userId, String action, String moduleName) {
        log(userId, action, "Navigated to module: " + moduleName);
    }

    public void logRegionAction(long userId, String action, String regionCode) {
        log(userId, action, "Region code: " + regionCode);
    }

    public void logChurchAction(long userId, String action, String churchCode) {
        log(userId, action, "Church code: " + churchCode);
    }

    public void logReceiptNumberGenerated(Long userId, int year, long sequence, String receiptNumber) {
        log(userId, RECEIPT_NUMBER_GENERATED,
                "Year: " + year + ", Sequence: " + sequence + ", Generated Receipt Number: " + receiptNumber);
    }

    public void logReceiptCreated(Long userId, ReceiptResponseDto receipt, Long churchId, BigDecimal totalAmount) {
        String action = receipt.getCorrectedFromReceiptId() == null ? RECEIPT_CREATED : CORRECTED_RECEIPT_CREATED;
        log(userId, action,
                "receipt_no: " + receipt.getReceiptNo()
                        + ", church_id: " + churchId
                        + ", church_code: " + receipt.getChurchCode()
                        + ", week_start_date: " + receipt.getWeekStartDate()
                        + ", week_end_date: " + receipt.getWeekEndDate()
                        + ", total_amount: " + totalAmount
                        + ", is_late_submission: " + receipt.isLateSubmission()
                        + ", corrected_from_receipt_id: " + nullToBlank(receipt.getCorrectedFromReceiptId())
                        + ", corrected_from_receipt_no: " + nullToBlank(receipt.getCorrectedFromReceiptNo()));
    }

    public void logReceiptCancelled(Long userId, ReceiptResponseDto receipt, String cancelReason) {
        log(userId, RECEIPT_CANCELLED,
                "receipt_no: " + receipt.getReceiptNo()
                        + ", receipt_id: " + receipt.getId()
                        + ", church_code: " + receipt.getChurchCode()
                        + ", week_start_date: " + receipt.getWeekStartDate()
                        + ", week_end_date: " + receipt.getWeekEndDate()
                        + ", reason: " + cancelReason);
    }

    public void logReceiptCreateFailed(Long userId, CreateReceiptRequest request, Church church,
                                       BigDecimal totalAmount, boolean lateSubmission, String reason) {
        log(userId, RECEIPT_CREATE_FAILED,
                "receipt_no: <not generated>"
                        + ", church_id: " + (request == null ? "" : request.getChurchId())
                        + ", church_code: " + (church == null ? "" : church.getChurchCode())
                        + ", week_start_date: " + (request == null ? "" : request.getWeekStartDate())
                        + ", week_end_date: " + (request == null ? "" : request.getWeekEndDate())
                        + ", total_amount: " + totalAmount
                        + ", is_late_submission: " + lateSubmission
                        + ", reason: " + reason);
    }

    public void logReceiptPdfGenerated(Long userId, long receiptId, String pdfPath) {
        log(userId, RECEIPT_PDF_GENERATED, "receipt_id: " + receiptId + ", pdf_file_path: " + pdfPath);
    }

    public void logReceiptOriginalPrinted(Long userId, long receiptId) {
        log(userId, RECEIPT_ORIGINAL_PRINTED, "receipt_id: " + receiptId);
    }

    public void logReceiptPrintFailed(Long userId, long receiptId, String reason) {
        log(userId, RECEIPT_PRINT_FAILED, "receipt_id: " + receiptId + ", reason: " + reason);
    }

    public void logReceiptPrintBlockedAlreadyPrinted(Long userId, long receiptId) {
        log(userId, RECEIPT_PRINT_BLOCKED_ALREADY_PRINTED, "receipt_id: " + receiptId);
    }

    public void logReceiptPrintBlockedCancelled(Long userId, long receiptId) {
        log(userId, RECEIPT_PRINT_BLOCKED_CANCELLED, "receipt_id: " + receiptId);
    }

    private void log(Long userId, String action, String details) {
        try {
            activityLogRepository.save(userId, action, details);
        } catch (DatabaseException exception) {
            System.err.println("Activity logging failed: " + exception.getMessage());
        }
    }

    private String sanitizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return "<blank>";
        }

        return username.strip();
    }

    private String nullToBlank(Object value) {
        return value == null ? "" : value.toString();
    }

}
