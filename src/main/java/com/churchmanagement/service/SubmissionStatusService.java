package com.churchmanagement.service;

import com.churchmanagement.dto.SubmissionDetailsDto;
import com.churchmanagement.dto.SubmissionStatusDto;
import com.churchmanagement.dto.SubmissionSummaryDto;
import com.churchmanagement.dto.SubmissionTotalsDto;
import com.churchmanagement.repository.SubmissionStatusRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.util.WeekUtil;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

public class SubmissionStatusService {
    public static final String STATUS_ALL = "ALL";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private final SubmissionStatusRepository submissionStatusRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public SubmissionStatusService() {
        this(new SubmissionStatusRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public SubmissionStatusService(SubmissionStatusRepository submissionStatusRepository,
                                   ActivityLogService activityLogService, Clock clock) {
        this.submissionStatusRepository = submissionStatusRepository;
        this.activityLogService = activityLogService;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public List<SubmissionStatusDto> loadWeeklyStatus(LocalDate weekStartDate, Long regionId, String status) {
        return loadWeeklyStatus(weekStartDate, regionId, null, status);
    }

    public List<SubmissionStatusDto> loadWeeklyStatus(LocalDate weekStartDate, Long regionId, Long churchId,
                                                      String status) {
        AuthenticatedUser user = requireReceiptView();
        LocalDate safeWeekStart = safeWeekStart(weekStartDate);
        String safeStatus = safeStatus(status);
        List<SubmissionStatusDto> rows = submissionStatusRepository.getWeeklySubmissionStatus(
                safeWeekStart, regionId, churchId, safeStatus);
        activityLogService.logSubmissionStatusViewed(user.getUserId(), safeWeekStart.toString(), regionId, churchId,
                safeStatus, rows.size());
        return rows;
    }

    public SubmissionSummaryDto loadWeeklySummary(LocalDate weekStartDate, Long regionId) {
        return loadWeeklySummary(weekStartDate, regionId, null);
    }

    public SubmissionSummaryDto loadWeeklySummary(LocalDate weekStartDate, Long regionId, Long churchId) {
        requireReceiptView();
        return submissionStatusRepository.getWeeklySummary(safeWeekStart(weekStartDate), regionId, churchId);
    }

    public SubmissionTotalsDto loadSubmissionTotals(LocalDate weekStartDate, Long regionId) {
        return loadSubmissionTotals(weekStartDate, regionId, null);
    }

    public SubmissionTotalsDto loadSubmissionTotals(LocalDate weekStartDate, Long regionId, Long churchId) {
        requireReceiptView();
        return submissionStatusRepository.getSubmissionTotals(safeWeekStart(weekStartDate), regionId, churchId);
    }

    public SubmissionDetailsDto loadSubmissionDetails(long receiptId) {
        AuthenticatedUser user = requireReceiptView();
        SubmissionDetailsDto details = submissionStatusRepository.getSubmissionDetails(receiptId)
                .orElseThrow(() -> new SubmissionStatusException("Submission details not found."));
        activityLogService.logSubmissionDetailsViewed(user.getUserId(), receiptId, details.getReceiptNo());
        return details;
    }

    public LocalDate defaultWeekStart() {
        return WeekUtil.getCurrentWeekMonday(LocalDate.now(clock));
    }

    public void logFilterChanged(LocalDate weekStartDate, Long regionId, String regionName, String status) {
        AuthContext.getCurrentUser().ifPresent(user -> activityLogService.logSubmissionStatusFilterChanged(
                user.getUserId(),
                weekStartDate == null ? null : weekStartDate.toString(),
                regionId,
                regionName,
                safeStatus(status)));
    }

    private LocalDate safeWeekStart(LocalDate weekStartDate) {
        LocalDate safeWeekStart = weekStartDate == null ? defaultWeekStart() : weekStartDate;
        if (!WeekUtil.isWeekStartMonday(safeWeekStart)) {
            throw new SubmissionStatusException("Week Start Date must be a Monday.");
        }
        return safeWeekStart;
    }

    private String safeStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_ALL;
        }
        String normalized = status.strip().toUpperCase();
        if (!List.of(STATUS_ALL, STATUS_SUBMITTED, STATUS_PENDING, STATUS_CANCELLED).contains(normalized)) {
            throw new SubmissionStatusException("Unknown submission status: " + status);
        }
        return normalized;
    }

    private AuthenticatedUser requireReceiptView() {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new SubmissionStatusException("Please sign in to view submission status."));
        try {
            new PermissionGuard(currentUser).require("receipt.menu.view");
        } catch (SecurityException exception) {
            throw new SubmissionStatusException("You do not have permission to view submission status.", exception);
        }
        return currentUser;
    }

    public static class SubmissionStatusException extends RuntimeException {
        public SubmissionStatusException(String message) {
            super(message);
        }

        public SubmissionStatusException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
