package com.churchmanagement.service;

import com.churchmanagement.dto.ActivityLogDto;
import com.churchmanagement.dto.ActivityLogSearchCriteria;
import com.churchmanagement.repository.ActivityLogRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

import java.util.List;

public class ActivityLogQueryService {
    private final ActivityLogRepository activityLogRepository;
    private final ActivityLogService activityLogService;
    private boolean initialViewLogged;

    public ActivityLogQueryService() {
        this(new ActivityLogRepository(), new ActivityLogService());
    }

    public ActivityLogQueryService(ActivityLogRepository activityLogRepository, ActivityLogService activityLogService) {
        this.activityLogRepository = activityLogRepository;
        this.activityLogService = activityLogService;
    }

    public List<ActivityLogDto> searchLogs(ActivityLogSearchCriteria criteria) {
        AuthenticatedUser currentUser = requirePermission();
        validateDateRange(criteria);
        List<ActivityLogDto> logs = activityLogRepository.searchLogs(criteria);
        boolean initialLoad = criteria != null
                && criteria.getLimit() != null
                && criteria.getLimit() == 100
                && criteria.getDateFrom() == null
                && criteria.getDateTo() == null
                && criteria.getUserId() == null
                && isBlank(criteria.getAction())
                && isBlank(criteria.getModule())
                && isBlank(criteria.getKeyword());
        if (initialLoad && !initialViewLogged) {
            activityLogService.logActivityLogsViewed(currentUser.getUserId(), logs.size());
            initialViewLogged = true;
        } else if (!initialLoad) {
            activityLogService.logActivityLogsSearched(currentUser.getUserId(), logs.size());
        }
        return logs;
    }

    public ActivityLogDto getLogDetails(long id) {
        AuthenticatedUser currentUser = requirePermission();
        ActivityLogDto log = activityLogRepository.findById(id)
                .orElseThrow(() -> new ActivityLogException("Activity log not found."));
        activityLogService.logActivityLogDetailsViewed(currentUser.getUserId(), id);
        return log;
    }

    public List<String> getActions() {
        requirePermission();
        return activityLogRepository.findDistinctActions();
    }

    public List<String> getModules() {
        requirePermission();
        return activityLogRepository.findDistinctModules();
    }

    private AuthenticatedUser requirePermission() {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new ActivityLogException("You do not have permission to view activity logs."));
        try {
            new PermissionGuard(currentUser).require("activity.menu.view");
            return currentUser;
        } catch (SecurityException exception) {
            throw new ActivityLogException("You do not have permission to view activity logs.", exception);
        }
    }

    private void validateDateRange(ActivityLogSearchCriteria criteria) {
        if (criteria == null || criteria.getDateFrom() == null || criteria.getDateTo() == null) {
            return;
        }
        if (criteria.getDateFrom().isAfter(criteria.getDateTo())) {
            throw new ActivityLogException("Invalid date range.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static class ActivityLogException extends RuntimeException {
        public ActivityLogException(String message) {
            super(message);
        }

        public ActivityLogException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
