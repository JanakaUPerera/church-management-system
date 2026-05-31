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
    private final SmsLogRepository smsLogRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public SmsLogService() {
        this(new SmsLogRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public SmsLogService(SmsLogRepository smsLogRepository, ActivityLogService activityLogService) {
        this(smsLogRepository, activityLogService, Clock.systemDefaultZone());
    }

    public SmsLogService(SmsLogRepository smsLogRepository, ActivityLogService activityLogService, Clock clock) {
        this.smsLogRepository = smsLogRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
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
        for (SmsLogDto log : logs) {
            log.setCanResend(hasPermission
                    && log.getCreatedAt() != null
                    && !now.isAfter(log.getCreatedAt().plusDays(7)));
        }
    }

    private AuthenticatedUser requirePermission() {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new SmsLogException("You do not have permission to view SMS logs."));
        try {
            new PermissionGuard(currentUser).require("sms.logs.view");
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
