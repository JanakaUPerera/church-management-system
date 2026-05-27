package com.churchmanagement.service;

import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.ActivityLogRepository;

public class ActivityLogService {
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";

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
