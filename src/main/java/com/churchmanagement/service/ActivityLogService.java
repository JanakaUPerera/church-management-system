package com.churchmanagement.service;

import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.ActivityLogRepository;

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
}
