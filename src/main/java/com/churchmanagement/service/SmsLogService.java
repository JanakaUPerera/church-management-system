package com.churchmanagement.service;

import com.churchmanagement.dto.SmsLogDto;
import com.churchmanagement.dto.SmsLogSearchCriteria;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

import java.util.List;
import java.time.Clock;
import java.time.LocalDateTime;

public class SmsLogService {
    private static final int RESEND_WINDOW_DAYS = 7;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final SmsLogRepository smsLogRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;
    private final SystemConfigurationCache configurationCache;

    public SmsLogService() {
        this(new SmsLogRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public SmsLogService(SmsLogRepository smsLogRepository, ActivityLogService activityLogService) {
        this(smsLogRepository, activityLogService, Clock.systemDefaultZone());
    }

    public SmsLogService(SmsLogRepository smsLogRepository, ActivityLogService activityLogService, Clock clock) {
        this(smsLogRepository, activityLogService, clock, SystemConfigurationCache.getInstance());
    }

    public SmsLogService(SmsLogRepository smsLogRepository, ActivityLogService activityLogService, Clock clock,
                         SystemConfigurationCache configurationCache) {
        this.smsLogRepository = smsLogRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
        this.configurationCache = configurationCache;
    }

    public List<SmsLogDto> latestLogs(int limit) {
        AuthenticatedUser currentUser = requirePermission();
        SmsLogSearchCriteria criteria = new SmsLogSearchCriteria();
        criteria.setLimit(limit);
        List<SmsLogDto> logs = smsLogRepository.searchSmsLogs(criteria);
        applyCanResend(logs, currentUser);
        activityLogService.logSmsLogsViewed(currentUser.getUserId(), logs.size());
        return logs;
    }

    public List<SmsLogDto> searchSmsLogs(SmsLogSearchCriteria criteria) {
        AuthenticatedUser currentUser = requirePermission();
        validateDateRange(criteria);
        List<SmsLogDto> logs = smsLogRepository.searchSmsLogs(criteria);
        applyCanResend(logs, currentUser);
        activityLogService.logSmsLogsSearched(currentUser.getUserId(), logs.size());
        return logs;
    }

    private void applyCanResend(List<SmsLogDto> logs, AuthenticatedUser currentUser) {
        boolean hasPermission = new PermissionGuard(currentUser).can("sms.resend");
        LocalDateTime now = LocalDateTime.now(clock);
        int maxAttempts = configuredMaxAttempts();
        for (SmsLogDto log : logs) {
            if (!hasPermission) {
                disableResend(log, "You do not have permission to resend SMS.");
            } else if (log.getCreatedAt() == null
                    || now.isAfter(log.getCreatedAt().plusDays(RESEND_WINDOW_DAYS))) {
                disableResend(log, "SMS resend period has expired. Resend is allowed only within 7 days.");
            } else if (hasResend(log)) {
                disableResend(log, "A newer resend already exists for this SMS.");
            } else if (Math.max(1, log.getAttemptCount()) >= maxAttempts) {
                disableResend(log, "SMS resend attempt limit has been reached.");
            } else {
                log.setCanResend(true);
                log.setResendDisabledReason(null);
            }
        }
    }

    private void disableResend(SmsLogDto log, String reason) {
        log.setCanResend(false);
        log.setResendDisabledReason(reason);
    }

    private boolean hasResend(SmsLogDto log) {
        return log.getId() != null && smsLogRepository.hasResend(log.getId());
    }

    private int configuredMaxAttempts() {
        String value = configurationCache.getString("sms.retry.max.attempts");
        if (value == null || value.isBlank()) {
            return DEFAULT_MAX_ATTEMPTS;
        }
        try {
            return Math.max(1, Integer.parseInt(value.strip()));
        } catch (NumberFormatException exception) {
            return DEFAULT_MAX_ATTEMPTS;
        }
    }

    private AuthenticatedUser requirePermission() {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new SmsLogException("You do not have permission to view SMS logs."));
        try {
            new PermissionGuard(currentUser).require("sms.menu.view");
            return currentUser;
        } catch (SecurityException exception) {
            throw new SmsLogException("You do not have permission to view SMS logs.", exception);
        }
    }

    private void validateDateRange(SmsLogSearchCriteria criteria) {
        if (criteria == null || criteria.getDateFrom() == null || criteria.getDateTo() == null) {
            return;
        }
        if (criteria.getDateFrom().isAfter(criteria.getDateTo())) {
            throw new SmsLogException("Invalid date range.");
        }
    }

    public static class SmsLogException extends RuntimeException {
        public SmsLogException(String message) {
            super(message);
        }

        public SmsLogException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
