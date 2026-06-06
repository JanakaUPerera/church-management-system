package com.churchmanagement.service;

import com.churchmanagement.dto.CreateReceiptRequest;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Region;
import com.churchmanagement.enums.ReceiptLanguage;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.ActivityLogRepository;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

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
    public static final String NAVIGATE_SMS_LOGS = "NAVIGATE_SMS_LOGS";
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
    public static final String SMS_SENT = "SMS_SENT";
    public static final String SMS_FAILED = "SMS_FAILED";
    public static final String SMS_SKIPPED = "SMS_SKIPPED";
    public static final String SMS_SETTINGS_UPDATED = "SMS_SETTINGS_UPDATED";
    public static final String SMS_TEST_SENT = "SMS_TEST_SENT";
    public static final String SMS_TEST_FAILED = "SMS_TEST_FAILED";
    public static final String SMS_COM_PORT_DETECTED = "SMS_COM_PORT_DETECTED";
    public static final String SMS_MODEM_TEST_SUCCESS = "SMS_MODEM_TEST_SUCCESS";
    public static final String SMS_MODEM_TEST_FAILED = "SMS_MODEM_TEST_FAILED";
    public static final String SMS_LOGS_VIEWED = "SMS_LOGS_VIEWED";
    public static final String SMS_LOGS_SEARCHED = "SMS_LOGS_SEARCHED";
    public static final String SMS_RESENT_SUCCESS = "SMS_RESENT_SUCCESS";
    public static final String SMS_RESENT_FAILED = "SMS_RESENT_FAILED";
    public static final String SMS_RESEND_BLOCKED_EXPIRED = "SMS_RESEND_BLOCKED_EXPIRED";
    public static final String SMS_RESEND_BLOCKED_PERMISSION = "SMS_RESEND_BLOCKED_PERMISSION";
    public static final String BACKUP_CREATED = "BACKUP_CREATED";
    public static final String BACKUP_FAILED = "BACKUP_FAILED";
    public static final String AUTO_BACKUP_CREATED = "AUTO_BACKUP_CREATED";
    public static final String AUTO_BACKUP_FAILED = "AUTO_BACKUP_FAILED";
    public static final String PRE_RESTORE_BACKUP_CREATED = "PRE_RESTORE_BACKUP_CREATED";
    public static final String PRE_RESTORE_BACKUP_FAILED = "PRE_RESTORE_BACKUP_FAILED";
    public static final String RESTORE_STARTED = "RESTORE_STARTED";
    public static final String RESTORE_SUCCESS = "RESTORE_SUCCESS";
    public static final String RESTORE_FAILED = "RESTORE_FAILED";
    public static final String BACKUP_SETTINGS_UPDATED = "BACKUP_SETTINGS_UPDATED";
    public static final String BACKUP_RETENTION_CLEANUP = "BACKUP_RETENTION_CLEANUP";
    public static final String AUTO_BACKUP_SCRIPT_GENERATED = "AUTO_BACKUP_SCRIPT_GENERATED";
    public static final String ACTIVITY_LOGS_VIEWED = "ACTIVITY_LOGS_VIEWED";
    public static final String ACTIVITY_LOGS_SEARCHED = "ACTIVITY_LOGS_SEARCHED";
    public static final String ACTIVITY_LOG_DETAILS_VIEWED = "ACTIVITY_LOG_DETAILS_VIEWED";
    public static final String DASHBOARD_VIEWED = "DASHBOARD_VIEWED";
    public static final String DASHBOARD_FILTER_CHANGED = "DASHBOARD_FILTER_CHANGED";
    public static final String DASHBOARD_WEEKLY_VIEWED = "DASHBOARD_WEEKLY_VIEWED";
    public static final String DASHBOARD_TRENDING_VIEWED = "DASHBOARD_TRENDING_VIEWED";
    public static final String DASHBOARD_WEEKLY_FILTER_CHANGED = "DASHBOARD_WEEKLY_FILTER_CHANGED";
    public static final String DASHBOARD_TRENDING_FILTER_CHANGED = "DASHBOARD_TRENDING_FILTER_CHANGED";
    public static final String SUBMISSION_STATUS_VIEWED = "SUBMISSION_STATUS_VIEWED";
    public static final String SUBMISSION_STATUS_FILTER_CHANGED = "SUBMISSION_STATUS_FILTER_CHANGED";
    public static final String SUBMISSION_DETAILS_VIEWED = "SUBMISSION_DETAILS_VIEWED";
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_ACTIVATED = "USER_ACTIVATED";
    public static final String USER_DEACTIVATED = "USER_DEACTIVATED";
    public static final String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    public static final String FORCE_PASSWORD_CHANGE_REQUIRED = "FORCE_PASSWORD_CHANGE_REQUIRED";
    public static final String PASSWORD_CHANGED_FORCE = "PASSWORD_CHANGED_FORCE";
    public static final String FORCE_PASSWORD_CHANGE_LOGOUT = "FORCE_PASSWORD_CHANGE_LOGOUT";
    public static final String PASSWORD_CHANGE_FAILED = "PASSWORD_CHANGE_FAILED";
    public static final String PROFILE_UPDATED = "PROFILE_UPDATED";
    public static final String PROFILE_PICTURE_UPDATED = "PROFILE_PICTURE_UPDATED";
    public static final String OWN_PASSWORD_CHANGED = "OWN_PASSWORD_CHANGED";
    public static final String OWN_PASSWORD_CHANGE_FAILED = "OWN_PASSWORD_CHANGE_FAILED";
    public static final String ROLE_CREATED = "ROLE_CREATED";
    public static final String ROLE_UPDATED = "ROLE_UPDATED";
    public static final String ROLE_ACTIVATED = "ROLE_ACTIVATED";
    public static final String ROLE_DEACTIVATED = "ROLE_DEACTIVATED";
    public static final String ROLE_PERMISSIONS_UPDATED = "ROLE_PERMISSIONS_UPDATED";

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

    public void logForcePasswordChangeRequired(long userId, String username) {
        log(userId, FORCE_PASSWORD_CHANGE_REQUIRED, "Force password change required for username: " + username);
    }

    public void logForcedPasswordChanged(long userId, String username) {
        log(userId, PASSWORD_CHANGED_FORCE, "Forced password change completed for username: " + username);
    }

    public void logForcePasswordChangeLogout(long userId, String username) {
        log(userId, FORCE_PASSWORD_CHANGE_LOGOUT, "Logout during forced password change for username: " + username);
    }

    public void logPasswordChangeFailed(long userId, String username, String reason) {
        log(userId, PASSWORD_CHANGE_FAILED, "Forced password change failed for username: " + username
                + ". Reason: " + nullToBlank(reason));
    }

    public void logNavigation(long userId, String action, String moduleName) {
        log(userId, action, "Navigated to module: " + moduleName);
    }

    public void logRegionAction(long userId, String action, String regionCode) {
        log(userId, action, "Region code: " + regionCode);
    }

    public void logRegionUpdated(long userId, Region oldRegion, Region newRegion) {
        String regionCode = newRegion == null ? "" : newRegion.getRegionCode();
        log(userId, REGION_UPDATED, "Region", regionCode,
                regionSummary(oldRegion), regionSummary(newRegion), "Region code: " + regionCode);
    }

    public void logChurchAction(long userId, String action, String churchCode) {
        log(userId, action, "Church code: " + churchCode);
    }

    public void logChurchUpdated(long userId, Church oldChurch, Church newChurch) {
        String churchCode = newChurch == null ? "" : newChurch.getChurchCode();
        log(userId, CHURCH_UPDATED, "Church", churchCode,
                churchSummary(oldChurch), churchSummary(newChurch), churchUpdateDescription(oldChurch, newChurch));
    }

    public void logChurchUpdated(long userId, String churchCode, ReceiptLanguage oldLanguage,
                                 ReceiptLanguage newLanguage) {
        String details = "Church code: " + churchCode;
        if (oldLanguage != newLanguage) {
            details += ", receipt_language: " + receiptLanguageName(oldLanguage)
                    + " -> " + receiptLanguageName(newLanguage);
        }
        log(userId, CHURCH_UPDATED, "Church", churchCode,
                "receipt_language: " + receiptLanguageName(oldLanguage),
                "receipt_language: " + receiptLanguageName(newLanguage), details);
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

    public void logSmsSent(Long userId, long receiptId, Long churchId, String mobileNumber, String provider) {
        log(userId, SMS_SENT,
                "receipt_id: " + receiptId
                        + ", church_id: " + nullToBlank(churchId)
                        + ", mobile_number: " + nullToBlank(mobileNumber)
                        + ", provider: " + nullToBlank(provider));
    }

    public void logSmsFailed(Long userId, Long receiptId, Long churchId, String mobileNumber, String reason) {
        log(userId, SMS_FAILED,
                "receipt_id: " + nullToBlank(receiptId)
                        + ", church_id: " + nullToBlank(churchId)
                        + ", mobile_number: " + nullToBlank(mobileNumber)
                        + ", reason: " + nullToBlank(reason));
    }

    public void logSmsSkipped(Long userId, Long receiptId, Long churchId, String reason) {
        log(userId, SMS_SKIPPED,
                "receipt_id: " + nullToBlank(receiptId)
                        + ", church_id: " + nullToBlank(churchId)
                        + ", reason: " + nullToBlank(reason));
    }

    public void logSmsSettingsUpdated(Long userId, boolean enabled, String gatewayType) {
        log(userId, SMS_SETTINGS_UPDATED, "sms_enabled: " + enabled + ", gateway_type: " + gatewayType);
    }

    public void logSmsTestSent(Long userId, String mobileNumber, String provider) {
        log(userId, SMS_TEST_SENT,
                "mobile_number: " + nullToBlank(mobileNumber) + ", provider: " + nullToBlank(provider));
    }

    public void logSmsTestFailed(Long userId, String mobileNumber, String reason) {
        log(userId, SMS_TEST_FAILED,
                "mobile_number: " + nullToBlank(mobileNumber) + ", reason: " + nullToBlank(reason));
    }

    public void logSmsComPortsDetected(Long userId, int portCount) {
        log(userId, SMS_COM_PORT_DETECTED, "port_count: " + portCount);
    }

    public void logSmsModemTestSuccess(Long userId, String comPort, int baudRate) {
        log(userId, SMS_MODEM_TEST_SUCCESS,
                "com_port: " + nullToBlank(comPort) + ", baud_rate: " + baudRate);
    }

    public void logSmsModemTestFailed(Long userId, String comPort, int baudRate, String reason) {
        log(userId, SMS_MODEM_TEST_FAILED,
                "com_port: " + nullToBlank(comPort)
                        + ", baud_rate: " + baudRate
                        + ", reason: " + nullToBlank(reason));
    }

    public void logSmsLogsViewed(Long userId, int resultCount) {
        log(userId, SMS_LOGS_VIEWED, "result_count: " + resultCount);
    }

    public void logSmsLogsSearched(Long userId, int resultCount) {
        log(userId, SMS_LOGS_SEARCHED, "result_count: " + resultCount);
    }

    public void logSmsResendSuccess(Long userId, Long originalSmsLogId, Long newSmsLogId, Long receiptId,
                                    Long churchId, String mobileNumber) {
        logSmsResend(userId, SMS_RESENT_SUCCESS, originalSmsLogId, newSmsLogId, receiptId, churchId,
                mobileNumber, "SUCCESS");
    }

    public void logSmsResendFailed(Long userId, Long originalSmsLogId, Long newSmsLogId, Long receiptId,
                                   Long churchId, String mobileNumber) {
        logSmsResend(userId, SMS_RESENT_FAILED, originalSmsLogId, newSmsLogId, receiptId, churchId,
                mobileNumber, "FAILED");
    }

    public void logSmsResendBlockedExpired(Long userId, Long originalSmsLogId) {
        log(userId, SMS_RESEND_BLOCKED_EXPIRED, "original_sms_log_id: " + nullToBlank(originalSmsLogId));
    }

    public void logSmsResendBlockedPermission(Long userId, Long originalSmsLogId) {
        log(userId, SMS_RESEND_BLOCKED_PERMISSION, "original_sms_log_id: " + nullToBlank(originalSmsLogId));
    }

    private void logSmsResend(Long userId, String action, Long originalSmsLogId, Long newSmsLogId, Long receiptId,
                              Long churchId, String mobileNumber, String resendStatus) {
        log(userId, action,
                "original_sms_log_id: " + nullToBlank(originalSmsLogId)
                        + ", new_sms_log_id: " + nullToBlank(newSmsLogId)
                        + ", receipt_id: " + nullToBlank(receiptId)
                        + ", church_id: " + nullToBlank(churchId)
                        + ", mobile_number: " + nullToBlank(mobileNumber)
                        + ", resent_by_user_id: " + nullToBlank(userId)
                        + ", resend_status: " + resendStatus);
    }

    public void logBackupCreated(Long userId, String action, String filePath, Long fileSizeBytes) {
        log(userId, action, "backup_file_path: " + nullToBlank(filePath)
                + ", file_size_bytes: " + nullToBlank(fileSizeBytes));
    }

    public void logBackupFailed(Long userId, String action, String filePath, String reason) {
        log(userId, action, "backup_file_path: " + nullToBlank(filePath)
                + ", reason: " + nullToBlank(reason));
    }

    public void logRestoreStarted(Long userId, String backupFilePath, Long preRestoreBackupLogId) {
        log(userId, RESTORE_STARTED, "backup_file_path: " + nullToBlank(backupFilePath)
                + ", pre_restore_backup_log_id: " + nullToBlank(preRestoreBackupLogId));
    }

    public void logRestoreSuccess(Long userId, String backupFilePath, Long preRestoreBackupLogId) {
        log(userId, RESTORE_SUCCESS, "backup_file_path: " + nullToBlank(backupFilePath)
                + ", pre_restore_backup_log_id: " + nullToBlank(preRestoreBackupLogId));
    }

    public void logRestoreFailed(Long userId, String backupFilePath, String reason) {
        log(userId, RESTORE_FAILED, "backup_file_path: " + nullToBlank(backupFilePath)
                + ", reason: " + nullToBlank(reason));
    }

    public void logBackupSettingsUpdated(Long userId, String backupFolder) {
        log(userId, BACKUP_SETTINGS_UPDATED, "backup_folder: " + nullToBlank(backupFolder));
    }

    public void logBackupRetentionCleanup(Long userId, String backupFolder, int deletedCount) {
        log(userId, BACKUP_RETENTION_CLEANUP, "backup_folder: " + nullToBlank(backupFolder)
                + ", deleted_count: " + deletedCount);
    }

    public void logAutoBackupScriptGenerated(Long userId, String scriptPath) {
        log(userId, AUTO_BACKUP_SCRIPT_GENERATED, "script_path: " + nullToBlank(scriptPath));
    }

    public void logActivityLogsViewed(Long userId, int resultCount) {
        log(userId, ACTIVITY_LOGS_VIEWED, "Activity Logs", null, "result_count: " + resultCount);
    }

    public void logActivityLogsSearched(Long userId, int resultCount) {
        log(userId, ACTIVITY_LOGS_SEARCHED, "Activity Logs", null, "result_count: " + resultCount);
    }

    public void logActivityLogDetailsViewed(Long userId, long activityLogId) {
        log(userId, ACTIVITY_LOG_DETAILS_VIEWED, "Activity Logs", String.valueOf(activityLogId),
                "activity_log_id: " + activityLogId);
    }

    public void logDashboardViewed(Long userId) {
        log(userId, DASHBOARD_VIEWED, "Dashboard", null, "Dashboard viewed");
    }

    public void logDashboardFilterChanged(Long userId, String weekStartDate, String month, String region) {
        log(userId, DASHBOARD_FILTER_CHANGED, "Dashboard", null,
                "week_start_date: " + nullToBlank(weekStartDate)
                        + ", month: " + nullToBlank(month)
                        + ", region: " + nullToBlank(region));
    }

    public void logDashboardWeeklyViewed(Long userId) {
        log(userId, DASHBOARD_WEEKLY_VIEWED, "Dashboard", null, "Weekly dashboard viewed");
    }

    public void logDashboardTrendingViewed(Long userId) {
        log(userId, DASHBOARD_TRENDING_VIEWED, "Dashboard", null, "Trending dashboard viewed");
    }

    public void logDashboardWeeklyFilterChanged(Long userId, String weekStartDate, String weekEndDate, String region) {
        log(userId, DASHBOARD_WEEKLY_FILTER_CHANGED, "Dashboard", null,
                "week_start_date: " + nullToBlank(weekStartDate)
                        + ", week_end_date: " + nullToBlank(weekEndDate)
                        + ", region: " + nullToBlank(region));
    }

    public void logDashboardTrendingFilterChanged(Long userId, String dateFrom, String dateTo, String region) {
        log(userId, DASHBOARD_TRENDING_FILTER_CHANGED, "Dashboard", null,
                "date_from: " + nullToBlank(dateFrom)
                        + ", date_to: " + nullToBlank(dateTo)
                        + ", region: " + nullToBlank(region));
    }

    public void logSubmissionStatusViewed(Long userId, String weekStartDate, Long regionId, Long churchId,
                                          String status, int resultCount) {
        log(userId, SUBMISSION_STATUS_VIEWED, "Submission Status", null,
                "week_start_date: " + nullToBlank(weekStartDate)
                        + ", region_id: " + nullToBlank(regionId)
                        + ", church_id: " + nullToBlank(churchId)
                        + ", status: " + nullToBlank(status)
                        + ", result_count: " + resultCount);
    }

    public void logSubmissionStatusFilterChanged(Long userId, String weekStartDate, Long regionId, String regionName,
                                                 String status) {
        log(userId, SUBMISSION_STATUS_FILTER_CHANGED, "Submission Status", null,
                "week_start_date: " + nullToBlank(weekStartDate)
                        + ", region_id: " + nullToBlank(regionId)
                        + ", region: " + nullToBlank(regionName)
                        + ", status: " + nullToBlank(status));
    }

    public void logSubmissionDetailsViewed(Long userId, long receiptId, String receiptNo) {
        log(userId, SUBMISSION_DETAILS_VIEWED, "Submission Status", String.valueOf(receiptId),
                "receipt_id: " + receiptId + ", receipt_no: " + nullToBlank(receiptNo));
    }

    public void logUserAction(long userId, String action, String username) {
        log(userId, action, "Users", username, "username: " + nullToBlank(username));
    }

    public void logRoleAction(long userId, String action, String roleName) {
        log(userId, action, "Roles & Permissions", roleName, "role_name: " + nullToBlank(roleName));
    }

    public void logProfileUpdated(long userId, String username) {
        log(userId, PROFILE_UPDATED, "My Profile", String.valueOf(userId),
                "Profile updated for username: " + nullToBlank(username));
    }

    public void logProfilePictureUpdated(long userId, String username, String profilePicturePath) {
        log(userId, PROFILE_PICTURE_UPDATED, "My Profile", String.valueOf(userId),
                "Profile picture updated for username: " + nullToBlank(username)
                        + ", profile_picture_path: " + nullToBlank(profilePicturePath));
    }

    public void logOwnPasswordChanged(long userId, String username) {
        log(userId, OWN_PASSWORD_CHANGED, "My Profile", String.valueOf(userId),
                "Own password changed for username: " + nullToBlank(username));
    }

    public void logOwnPasswordChangeFailed(long userId, String username, String reason) {
        log(userId, OWN_PASSWORD_CHANGE_FAILED, "My Profile", String.valueOf(userId),
                "Own password change failed for username: " + nullToBlank(username)
                        + ". Reason: " + nullToBlank(reason));
    }

    private void log(Long userId, String action, String details) {
        log(userId, action, "AUTH", null, null, null, details);
    }

    private void log(Long userId, String action, String module, String recordId, String details) {
        log(userId, action, module, recordId, null, null, details);
    }

    private void log(Long userId, String action, String module, String recordId, String oldValue, String newValue,
                     String details) {
        try {
            activityLogRepository.save(userId, action, module, recordId, oldValue, newValue, null, machineName(), details);
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

    private String receiptLanguageName(ReceiptLanguage language) {
        return language == null ? "" : language.getDisplayName();
    }

    private String regionSummary(Region region) {
        if (region == null) {
            return "";
        }
        return "region_code: " + nullToBlank(region.getRegionCode())
                + ", region_name: " + nullToBlank(region.getRegionName())
                + ", status: " + nullToBlank(region.getStatus());
    }

    private String churchSummary(Church church) {
        if (church == null) {
            return "";
        }
        return "church_code: " + nullToBlank(church.getChurchCode())
                + ", church_name: " + nullToBlank(church.getChurchName())
                + ", region_id: " + nullToBlank(church.getRegionId())
                + ", status: " + nullToBlank(church.getStatus())
                + ", authorized_person_name: " + nullToBlank(church.getAuthorizedPersonName())
                + ", authorized_person_position: " + nullToBlank(church.getAuthorizedPersonPosition())
                + ", authorized_person_position_other: " + nullToBlank(church.getAuthorizedPersonPositionOther())
                + ", sms_mobile_number: " + nullToBlank(church.getSmsMobileNumber())
                + ", receipt_language: " + receiptLanguageName(church.getReceiptLanguage());
    }

    private String churchUpdateDescription(Church oldChurch, Church newChurch) {
        String churchCode = newChurch == null ? "" : newChurch.getChurchCode();
        String details = "Church code: " + churchCode;
        if (oldChurch == null || newChurch == null) {
            return details;
        }
        if (!Objects.equals(oldChurch.getReceiptLanguage(), newChurch.getReceiptLanguage())) {
            details += ", receipt_language: " + receiptLanguageName(oldChurch.getReceiptLanguage())
                    + " -> " + receiptLanguageName(newChurch.getReceiptLanguage());
        }
        return details;
    }

    private String machineName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return System.getenv("COMPUTERNAME");
        }
    }

}
