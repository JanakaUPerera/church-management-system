package com.churchmanagement.service;

import com.churchmanagement.dto.dashboard.ChurchCollectionTrendDto;
import com.churchmanagement.dto.dashboard.TrendingDashboardDto;
import com.churchmanagement.dto.dashboard.WeeklyDashboardDto;
import com.churchmanagement.repository.DashboardRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.util.WeekUtil;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

public class DashboardService {
    private final DashboardRepository dashboardRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public DashboardService() {
        this(new DashboardRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public DashboardService(DashboardRepository dashboardRepository, ActivityLogService activityLogService, Clock clock) {
        this.dashboardRepository = dashboardRepository;
        this.activityLogService = activityLogService;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public WeeklyDashboardDto loadWeeklyDashboard(LocalDate weekStartDate, LocalDate weekEndDate, Long regionId) {
        AuthenticatedUser user = currentUser();
        PermissionGuard guard = new PermissionGuard(user);
        LocalDate safeWeekStart = defaultWeeklyStart(weekStartDate);
        LocalDate safeWeekEnd = weekEndDate == null ? WeekUtil.getSundayForMonday(safeWeekStart) : weekEndDate;
        validateWeek(safeWeekStart, safeWeekEnd);

        WeeklyDashboardDto weekly = dashboardRepository.getWeeklySummary(safeWeekStart, safeWeekEnd, regionId);
        weekly.setWeekStartDate(safeWeekStart);
        weekly.setWeekEndDate(safeWeekEnd);
        applyWeeklyPermissions(weekly, guard);

        if (guard.can("report.menu.view")) {
            weekly.setSubmittedVsPendingChart(dashboardRepository.getSubmittedVsPendingChart(
                    safeWeekStart, safeWeekEnd, regionId));
            weekly.setRegionSubmissionProgress(dashboardRepository.getRegionWiseSubmissionProgress(
                    safeWeekStart, safeWeekEnd, regionId));
            weekly.setRegionWiseWeeklyCollection(dashboardRepository.getRegionWiseWeeklyCollection(
                    safeWeekStart, safeWeekEnd, regionId));
            weekly.setCollectionTypeWeekReceiptTotals(dashboardRepository.getCollectionTypeWeekReceiptTotals(
                    safeWeekStart, safeWeekEnd, regionId));
            weekly.setCollectionTypeWeeklyTotals(dashboardRepository.getCollectionTypeWeeklyTotals(
                    safeWeekStart, safeWeekEnd, regionId));
            weekly.setTopWeeklyChurchCollections(dashboardRepository.getTopWeeklyChurchCollections(
                    safeWeekStart, safeWeekEnd, null));
            weekly.setTopWeeklyRegionCollections(dashboardRepository.getTopWeeklyRegionCollections(
                    safeWeekStart, safeWeekEnd, null));
        }

        activityLogService.logDashboardWeeklyViewed(user.getUserId());
        return weekly;
    }

    public TrendingDashboardDto loadTrendingDashboard(LocalDate dateFrom, LocalDate dateTo, Long regionId) {
        return loadTrendingDashboard(dateFrom, dateTo, regionId, List.of());
    }

    public TrendingDashboardDto loadTrendingDashboard(LocalDate dateFrom, LocalDate dateTo, Long regionId,
                                                      List<Long> churchIds) {
        AuthenticatedUser user = currentUser();
        PermissionGuard guard = new PermissionGuard(user);
        DateRange range = defaultTrendingRange(dateFrom, dateTo);
        validateDateRange(range.dateFrom(), range.dateTo());

        TrendingDashboardDto trending = new TrendingDashboardDto();
        trending.setDateFrom(range.dateFrom());
        trending.setDateTo(range.dateTo());
        trending.setGroupingMode(groupingMode(range.dateFrom(), range.dateTo()));
        trending.setChartsVisible(guard.can("report.menu.view"));
        if (guard.can("report.menu.view")) {
            trending.setTotalCollectionTrend(dashboardRepository.getTotalCollectionTrend(
                    range.dateFrom(), range.dateTo(), regionId));
            trending.setCollectionTypeWiseTrend(dashboardRepository.getCollectionTypeWiseTrend(
                    range.dateFrom(), range.dateTo(), regionId));
            trending.setChurchWiseCollectionTrend(dashboardRepository.getChurchWiseCollectionTrend(
                    range.dateFrom(), range.dateTo(), regionId, churchIds));
            trending.setRegionWiseCollectionTrend(dashboardRepository.getRegionWiseCollectionTrend(
                    range.dateFrom(), range.dateTo()));
        }

        activityLogService.logDashboardTrendingViewed(user.getUserId());
        return trending;
    }

    public List<ChurchCollectionTrendDto> loadChurchCollectionTrend(LocalDate dateFrom, LocalDate dateTo,
                                                                    Long regionId, List<Long> churchIds) {
        AuthenticatedUser user = currentUser();
        PermissionGuard guard = new PermissionGuard(user);
        DateRange range = defaultTrendingRange(dateFrom, dateTo);
        validateDateRange(range.dateFrom(), range.dateTo());
        if (!guard.can("report.menu.view")) {
            return List.of();
        }
        return dashboardRepository.getChurchWiseCollectionTrend(range.dateFrom(), range.dateTo(), regionId, churchIds);
    }

    public DateRange defaultWeeklyRange() {
        LocalDate weekStart = WeekUtil.getCurrentWeekMonday(LocalDate.now(clock));
        return new DateRange(weekStart, WeekUtil.getSundayForMonday(weekStart));
    }

    public DateRange defaultTrendingRange() {
        LocalDate today = LocalDate.now(clock);
        return new DateRange(today.withDayOfMonth(1), today);
    }

    public DateRange quickMonthRange() {
        return defaultTrendingRange();
    }

    public DateRange quickQuarterRange() {
        LocalDate today = LocalDate.now(clock);
        int quarterStartMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
        return new DateRange(LocalDate.of(today.getYear(), quarterStartMonth, 1), today);
    }

    public DateRange quickHalfYearRange() {
        LocalDate today = LocalDate.now(clock);
        return new DateRange(today.minusMonths(6), today);
    }

    public DateRange quickYearRange() {
        LocalDate today = LocalDate.now(clock);
        return new DateRange(LocalDate.of(today.getYear(), 1, 1), today);
    }

    public void logWeeklyFilterChanged(LocalDate weekStartDate, LocalDate weekEndDate, String regionName) {
        AuthContext.getCurrentUser().ifPresent(user -> activityLogService.logDashboardWeeklyFilterChanged(
                user.getUserId(),
                weekStartDate == null ? null : weekStartDate.toString(),
                weekEndDate == null ? null : weekEndDate.toString(),
                regionName));
    }

    public void logTrendingFilterChanged(LocalDate dateFrom, LocalDate dateTo, String regionName) {
        AuthContext.getCurrentUser().ifPresent(user -> activityLogService.logDashboardTrendingFilterChanged(
                user.getUserId(),
                dateFrom == null ? null : dateFrom.toString(),
                dateTo == null ? null : dateTo.toString(),
                regionName));
    }

    private DateRange defaultTrendingRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo == null) {
            return defaultTrendingRange();
        }
        validateDateRange(dateFrom, dateTo);
        DateRange defaults = defaultTrendingRange();
        return new DateRange(dateFrom == null ? defaults.dateFrom() : dateFrom,
                dateTo == null ? defaults.dateTo() : dateTo);
    }

    private LocalDate defaultWeeklyStart(LocalDate weekStartDate) {
        return weekStartDate == null ? defaultWeeklyRange().dateFrom() : weekStartDate;
    }

    private void validateWeek(LocalDate weekStartDate, LocalDate weekEndDate) {
        if (!WeekUtil.isWeekStartMonday(weekStartDate)) {
            throw new DashboardException("Week start date must be a Monday.");
        }
        if (!WeekUtil.isWeekEndSunday(weekEndDate)) {
            throw new DashboardException("Week end date must be a Sunday.");
        }
        if (!weekEndDate.equals(weekStartDate.plusDays(6))) {
            throw new DashboardException("Week end date must be 6 days after week start date.");
        }
    }

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null) {
            throw new DashboardException("Date From is required.");
        }
        if (dateTo == null) {
            throw new DashboardException("Date To is required.");
        }
        if (dateFrom.isAfter(dateTo)) {
            throw new DashboardException("Date From must be before or equal to Date To.");
        }
    }

    private String groupingMode(LocalDate dateFrom, LocalDate dateTo) {
        return "WEEKLY";
    }

    private void applyWeeklyPermissions(WeeklyDashboardDto weekly, PermissionGuard guard) {
        boolean receiptView = guard.can("receipt.menu.view");
        weekly.setReceiptSummaryVisible(receiptView);
        weekly.setChartsVisible(guard.can("report.menu.view"));
        weekly.setSmsFailedVisible(guard.can("sms.menu.view"));
        weekly.setBackupStatusVisible(guard.can("backup.view"));
        weekly.setTodaysReceiptsTotalVisible(receiptView);
        weekly.setLateSubmissionsVisible(receiptView
                && WeekUtil.isBackWeek(weekly.getWeekStartDate(), LocalDate.now(clock)));
        weekly.setWeekCollectionVisible(receiptView
                && !isCurrentCalendarWeekRange(weekly.getWeekStartDate(), weekly.getWeekEndDate()));
        if (!receiptView) {
            weekly.setSubmittedChurches(0);
            weekly.setPendingChurches(0);
            weekly.setLateSubmissions(0);
            weekly.setTodaysReceiptsTotal(null);
        }
        if (!guard.can("sms.menu.view")) {
            weekly.setSmsFailedCount(0);
        }
        if (!guard.can("backup.view")) {
            weekly.setLastBackupStatus("");
        }
    }

    private boolean isCurrentCalendarWeekRange(LocalDate weekStartDate, LocalDate weekEndDate) {
        LocalDate today = LocalDate.now(clock);
        LocalDate currentWeekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        return weekStartDate != null && weekEndDate != null
                && weekStartDate.equals(currentWeekStart)
                && weekEndDate.equals(WeekUtil.getSundayForMonday(currentWeekStart));
    }

    private AuthenticatedUser currentUser() {
        return AuthContext.getCurrentUser()
                .orElseThrow(() -> new DashboardException("Please sign in to view the dashboard."));
    }

    public record DateRange(LocalDate dateFrom, LocalDate dateTo) {
    }

    public static class DashboardException extends RuntimeException {
        public DashboardException(String message) {
            super(message);
        }
    }
}
