package com.churchmanagement.service;

import com.churchmanagement.dto.dashboard.ChartDataPointDto;
import com.churchmanagement.dto.dashboard.ChurchCollectionTrendDto;
import com.churchmanagement.dto.dashboard.CollectionTrendDto;
import com.churchmanagement.dto.dashboard.RegionCollectionTrendDto;
import com.churchmanagement.dto.dashboard.RegionCollectionTypeTotalDto;
import com.churchmanagement.dto.dashboard.RegionSubmissionProgressDto;
import com.churchmanagement.dto.dashboard.TrendingDashboardDto;
import com.churchmanagement.dto.dashboard.WeeklyDashboardDto;
import com.churchmanagement.repository.DashboardRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardServiceTest {
    private FakeDashboardRepository dashboardRepository;
    private FakeActivityLogService activityLogService;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardRepository = new FakeDashboardRepository();
        activityLogService = new FakeActivityLogService();
        dashboardService = new DashboardService(dashboardRepository, activityLogService, fixedClock());
        AuthContext.setCurrentUser(new AuthenticatedUser(7L, "admin", "System Administrator", 1L,
                "Admin", List.of("receipt.view", "report.view", "sms.logs.view", "backup.view")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void defaultWeeklyDateIsCurrentWeek() {
        DashboardService.DateRange range = dashboardService.defaultWeeklyRange();
        WeeklyDashboardDto weekly = dashboardService.loadWeeklyDashboard(null, null, null);

        assertEquals(LocalDate.of(2026, 5, 26), range.dateFrom());
        assertEquals(LocalDate.of(2026, 6, 1), range.dateTo());
        assertEquals(range.dateFrom(), weekly.getWeekStartDate());
        assertEquals(range.dateTo(), weekly.getWeekEndDate());
        assertTrue(weekly.isTodaysReceiptsTotalVisible());
        assertFalse(weekly.isWeekCollectionVisible());
        assertEquals(ActivityLogService.DASHBOARD_WEEKLY_VIEWED, activityLogService.lastAction);
    }

    @Test
    void defaultTrendingDateIsCurrentMonthStartToToday() {
        DashboardService.DateRange range = dashboardService.defaultTrendingRange();
        TrendingDashboardDto trending = dashboardService.loadTrendingDashboard(null, null, null);

        assertEquals(LocalDate.of(2026, 6, 1), range.dateFrom());
        assertEquals(LocalDate.of(2026, 6, 2), range.dateTo());
        assertEquals(range.dateFrom(), trending.getDateFrom());
        assertEquals(range.dateTo(), trending.getDateTo());
        assertEquals(ActivityLogService.DASHBOARD_TRENDING_VIEWED, activityLogService.lastAction);
    }

    @Test
    void quickRangesAreCalculatedFromToday() {
        assertEquals(new DashboardService.DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2)),
                dashboardService.quickMonthRange());
        assertEquals(new DashboardService.DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 2)),
                dashboardService.quickQuarterRange());
        assertEquals(new DashboardService.DateRange(LocalDate.of(2025, 12, 2), LocalDate.of(2026, 6, 2)),
                dashboardService.quickHalfYearRange());
        assertEquals(new DashboardService.DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 2)),
                dashboardService.quickYearRange());
    }

    @Test
    void cancelledReceiptsExcludedFromAllTotals() {
        WeeklyDashboardDto weekly = dashboardService.loadWeeklyDashboard(LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 5, 25), null);
        TrendingDashboardDto trending = dashboardService.loadTrendingDashboard(LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 6, 2), null);

        assertEquals(1, weekly.getCompletedRegions());
        assertEquals(2, weekly.getTotalRegions());
        assertEquals(new BigDecimal("1.0"), BigDecimal.valueOf(weekly.getRegionSubmissionProgress().getFirst().getProgress()));
        assertEquals(new BigDecimal("500.00"), weekly.getTodaysReceiptsTotal());
        assertEquals(BigDecimal.ZERO, pointValue(weekly.getCollectionTypeWeekReceiptTotals(), "OFFERTORY"));
        assertEquals(new BigDecimal("1500.00"), pointValue(weekly.getCollectionTypeWeeklyTotals(), "OFFERTORY"));
        assertEquals(BigDecimal.ZERO, pointValue(weekly.getCollectionTypeWeeklyTotals(), "OTHER_DONATIONS"));
        assertEquals(new BigDecimal("1500.00"), regionCollectionValue(
                weekly.getRegionWiseWeeklyCollection(), "North", "OFFERTORY"));
        assertEquals(new BigDecimal("500.00"), regionCollectionValue(
                weekly.getRegionWiseWeeklyCollection(), "South", "TITHES"));
        assertEquals(new BigDecimal("2000.00"), pointValue(trending.getTotalCollectionTrend(), "2026-05-19"));

        WeeklyDashboardDto filteredWeekly = dashboardService.loadWeeklyDashboard(LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 5, 25), 2L);
        assertEquals(new BigDecimal("1500.00"), pointValue(filteredWeekly.getTopWeeklyRegionCollections(), "North"));
        assertEquals(new BigDecimal("500.00"), pointValue(filteredWeekly.getTopWeeklyRegionCollections(), "South"));
    }

    @Test
    void pendingChurchesCalculatedFromActiveReceiptsOnly() {
        WeeklyDashboardDto weekly = dashboardService.loadWeeklyDashboard(LocalDate.of(2026, 5, 26),
                LocalDate.of(2026, 6, 1), null);

        assertEquals(0, weekly.getSubmittedChurches());
        assertEquals(4, weekly.getPendingChurches());
        assertTrue(weekly.isTodaysReceiptsTotalVisible());
        assertFalse(weekly.isWeekCollectionVisible());
        assertEquals(new BigDecimal("1000.00"), pointValue(weekly.getCollectionTypeWeekReceiptTotals(), "OFFERTORY"));
        assertEquals(new BigDecimal("500.00"), pointValue(weekly.getCollectionTypeWeekReceiptTotals(), "TITHES"));
        assertEquals(BigDecimal.ZERO, pointValue(weekly.getCollectionTypeWeeklyTotals(), "OFFERTORY"));
    }

    @Test
    void trendGroupingIsWeeklyForShortRanges() {
        TrendingDashboardDto trending = dashboardService.loadTrendingDashboard(LocalDate.of(2026, 5, 19),
                LocalDate.of(2026, 6, 2), null);

        assertEquals("WEEKLY", trending.getGroupingMode());
        assertEquals("WEEKLY", dashboardRepository.lastGroupingMode);
        assertEquals("2026-05-19", trending.getTotalCollectionTrend().getFirst().getLabel());
    }

    @Test
    void trendGroupingStaysWeeklyForRangesOverNinetyDays() {
        TrendingDashboardDto trending = dashboardService.loadTrendingDashboard(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 2), null);

        assertEquals("WEEKLY", trending.getGroupingMode());
        assertEquals("WEEKLY", dashboardRepository.lastGroupingMode);
        assertTrue(trending.getTotalCollectionTrend().stream().anyMatch(point -> "2026-05-19".equals(point.getLabel())));
    }

    @Test
    void invalidDateRangesAreRejected() {
        assertThrows(DashboardService.DashboardException.class,
                () -> dashboardService.loadTrendingDashboard(null, LocalDate.of(2026, 6, 2), null));
        assertThrows(DashboardService.DashboardException.class,
                () -> dashboardService.loadTrendingDashboard(LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 2), null));
        assertThrows(DashboardService.DashboardException.class,
                () -> dashboardService.loadWeeklyDashboard(LocalDate.of(2026, 5, 26), LocalDate.of(2026, 5, 31), null));
    }

    private BigDecimal pointValue(List<ChartDataPointDto> points, String label) {
        return points.stream()
                .filter(point -> label.equals(point.getLabel()))
                .map(ChartDataPointDto::getValue)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal regionCollectionValue(List<RegionCollectionTypeTotalDto> points, String regionName,
                                             String collectionType) {
        return points.stream()
                .filter(point -> regionName.equals(point.getRegionName()))
                .filter(point -> collectionType.equals(point.getCollectionType()))
                .map(RegionCollectionTypeTotalDto::getAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-06-02T04:30:00Z"), ZoneId.of("Asia/Colombo"));
    }

    private static class FakeDashboardRepository extends DashboardRepository {
        private final List<ChurchRow> churches = List.of(
                new ChurchRow(1L, 1L, "North", "Central", true),
                new ChurchRow(2L, 1L, "North", "Hill", true),
                new ChurchRow(3L, 2L, "South", "Lake", true),
                new ChurchRow(4L, 2L, "South", "Field", true)
        );
        private final List<ReceiptRow> receipts = List.of(
                new ReceiptRow(1L, 1L, 1L, LocalDate.of(2026, 5, 19), LocalDate.of(2026, 5, 27),
                        true, false, "OFFERTORY", new BigDecimal("1000.00")),
                new ReceiptRow(2L, 2L, 1L, LocalDate.of(2026, 5, 19), LocalDate.of(2026, 6, 2),
                        true, true, "OFFERTORY", new BigDecimal("500.00")),
                new ReceiptRow(3L, 3L, 2L, LocalDate.of(2026, 5, 19), LocalDate.of(2026, 5, 26),
                        true, false, "TITHES", new BigDecimal("500.00")),
                new ReceiptRow(4L, 4L, 2L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                        false, false, "OTHER_DONATIONS", new BigDecimal("900.00")),
                new ReceiptRow(5L, 1L, 1L, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 5),
                        true, false, "TITHES", new BigDecimal("300.00"))
        );
        private String lastGroupingMode;

        private FakeDashboardRepository() {
            super((DataSource) null);
        }

        @Override
        public WeeklyDashboardDto getWeeklySummary(LocalDate weekStartDate, LocalDate weekEndDate, Long regionId) {
            WeeklyDashboardDto dto = new WeeklyDashboardDto();
            dto.setTotalRegions(regionId == null ? 2 : 1);
            dto.setTotalChurches(activeChurches(regionId).size());
            List<Long> submittedChurches = activeReceipts(regionId).stream()
                    .filter(receipt -> inRange(receipt.weekStart(), weekStartDate, weekEndDate))
                    .map(ReceiptRow::churchId)
                    .distinct()
                    .toList();
            dto.setCompletedRegions(completedRegions(weekStartDate, weekEndDate, regionId));
            dto.setSubmittedChurches(submittedChurches.size());
            dto.setPendingChurches(activeChurches(regionId).stream()
                    .filter(church -> !submittedChurches.contains(church.id()))
                    .count());
            dto.setLateSubmissions(activeReceipts(regionId).stream()
                    .filter(ReceiptRow::late)
                    .filter(receipt -> inRange(receipt.weekStart(), weekStartDate, weekEndDate))
                    .count());
            dto.setTodaysReceiptsTotal(activeReceipts(regionId).stream()
                    .filter(receipt -> receipt.receiptDate().equals(LocalDate.of(2026, 6, 2)))
                    .map(ReceiptRow::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            dto.setSmsFailedCount(2);
            dto.setLastBackupStatus("SUCCESS");
            return dto;
        }

        private long completedRegions(LocalDate weekStartDate, LocalDate weekEndDate, Long regionId) {
            return activeChurches(regionId).stream()
                    .map(ChurchRow::regionId)
                    .distinct()
                    .filter(currentRegionId -> {
                        List<Long> churchIds = activeChurches(currentRegionId).stream()
                                .map(ChurchRow::id)
                                .toList();
                        List<Long> submittedChurchIds = activeReceipts(currentRegionId).stream()
                                .filter(receipt -> inRange(receipt.weekStart(), weekStartDate, weekEndDate))
                                .map(ReceiptRow::churchId)
                                .distinct()
                                .toList();
                        return !churchIds.isEmpty() && submittedChurchIds.containsAll(churchIds);
                    })
                    .count();
        }

        @Override
        public List<ChartDataPointDto> getSubmittedVsPendingChart(LocalDate weekStartDate, LocalDate weekEndDate,
                                                                  Long regionId) {
            WeeklyDashboardDto summary = getWeeklySummary(weekStartDate, weekEndDate, regionId);
            return List.of(ChartDataPointDto.of("Submitted", summary.getSubmittedChurches()),
                    ChartDataPointDto.of("Pending", summary.getPendingChurches()));
        }

        @Override
        public List<RegionSubmissionProgressDto> getRegionWiseSubmissionProgress(LocalDate weekStartDate,
                                                                                 LocalDate weekEndDate,
                                                                                 Long regionId) {
            return activeChurches(regionId).stream()
                    .map(ChurchRow::regionId)
                    .distinct()
                    .map(currentRegionId -> {
                        List<Long> churchIds = activeChurches(currentRegionId).stream()
                                .map(ChurchRow::id)
                                .toList();
                        long submitted = activeReceipts(currentRegionId).stream()
                                .filter(receipt -> inRange(receipt.weekStart(), weekStartDate, weekEndDate))
                                .map(ReceiptRow::churchId)
                                .distinct()
                                .count();
                        return new RegionSubmissionProgressDto(church(currentRegionId, churchIds.getFirst()).regionName(),
                                submitted, churchIds.size());
                    })
                    .toList();
        }

        @Override
        public List<RegionCollectionTypeTotalDto> getRegionWiseWeeklyCollection(LocalDate weekStartDate, LocalDate weekEndDate,
                                                                                Long regionId) {
            return activeReceipts(regionId).stream()
                    .filter(receipt -> inRange(receipt.weekStart(), weekStartDate, weekEndDate))
                    .collect(java.util.stream.Collectors.groupingBy(
                            receipt -> church(receipt.churchId()).regionName() + "|" + receipt.collectionType(),
                            java.util.LinkedHashMap::new,
                            java.util.stream.Collectors.reducing(BigDecimal.ZERO, ReceiptRow::amount, BigDecimal::add)))
                    .entrySet()
                    .stream()
                    .map(entry -> {
                        String[] key = entry.getKey().split("\\|", 2);
                        return new RegionCollectionTypeTotalDto(key[0], key[1], entry.getValue());
                    })
                    .toList();
        }

        @Override
        public List<ChartDataPointDto> getCollectionTypeWeeklyTotals(LocalDate weekStartDate, LocalDate weekEndDate,
                                                                     Long regionId) {
            return groupReceipts(activeReceipts(regionId).stream()
                    .filter(receipt -> inRange(receipt.weekStart(), weekStartDate, weekEndDate))
                    .toList(), "type", "WEEKLY");
        }

        @Override
        public List<ChartDataPointDto> getCollectionTypeWeekReceiptTotals(LocalDate weekStartDate, LocalDate weekEndDate,
                                                                         Long regionId) {
            return groupReceipts(activeReceipts(regionId).stream()
                    .filter(receipt -> inRange(receipt.receiptDate(), weekStartDate, weekEndDate))
                    .toList(), "type", "WEEKLY");
        }

        @Override
        public List<ChartDataPointDto> getTopWeeklyChurchCollections(LocalDate weekStartDate, LocalDate weekEndDate,
                                                                     Long regionId) {
            return groupReceipts(activeReceipts(regionId).stream()
                    .filter(receipt -> inRange(receipt.weekStart(), weekStartDate, weekEndDate))
                    .toList(), "church", "WEEKLY").stream()
                    .sorted((left, right) -> right.getValue().compareTo(left.getValue()))
                    .limit(3)
                    .toList();
        }

        @Override
        public List<ChartDataPointDto> getTopWeeklyRegionCollections(LocalDate weekStartDate, LocalDate weekEndDate,
                                                                     Long regionId) {
            return groupReceipts(activeReceipts(regionId).stream()
                    .filter(receipt -> inRange(receipt.weekStart(), weekStartDate, weekEndDate))
                    .toList(), "region", "WEEKLY").stream()
                    .sorted((left, right) -> right.getValue().compareTo(left.getValue()))
                    .limit(3)
                    .toList();
        }

        @Override
        public List<ChartDataPointDto> getTotalCollectionTrend(LocalDate dateFrom, LocalDate dateTo, Long regionId) {
            String grouping = grouping(dateFrom, dateTo);
            lastGroupingMode = grouping;
            return groupReceipts(activeReceipts(regionId).stream()
                    .filter(receipt -> inRange(receipt.weekStart(), dateFrom, dateTo))
                    .toList(), "period", grouping);
        }

        @Override
        public List<CollectionTrendDto> getCollectionTypeWiseTrend(LocalDate dateFrom, LocalDate dateTo, Long regionId) {
            String grouping = grouping(dateFrom, dateTo);
            List<String> labels = periodLabels(activeReceipts(regionId).stream()
                    .filter(receipt -> inRange(receipt.weekStart(), dateFrom, dateTo))
                    .toList(), grouping);
            return labels.stream()
                    .map(label -> new CollectionTrendDto(label,
                            sumFor(label, "OFFERTORY", grouping, regionId, dateFrom, dateTo),
                            sumFor(label, "TITHES", grouping, regionId, dateFrom, dateTo),
                            sumFor(label, "OTHER_DONATIONS", grouping, regionId, dateFrom, dateTo)))
                    .toList();
        }

        @Override
        public List<ChurchCollectionTrendDto> getChurchWiseCollectionTrend(LocalDate dateFrom, LocalDate dateTo,
                                                                          Long regionId, List<Long> churchIds) {
            List<Long> selectedChurchIds = churchIds == null || churchIds.isEmpty()
                    ? activeReceipts(regionId).stream()
                    .filter(receipt -> inRange(receipt.weekStart(), dateFrom, dateTo))
                    .map(ReceiptRow::churchId)
                    .distinct()
                    .toList()
                    : churchIds;
            return activeReceipts(regionId).stream()
                    .filter(receipt -> inRange(receipt.weekStart(), dateFrom, dateTo))
                    .filter(receipt -> selectedChurchIds.contains(receipt.churchId()))
                    .map(receipt -> new ChurchCollectionTrendDto(
                            labelFor(receipt, "period", grouping(dateFrom, dateTo)),
                            church(receipt.churchId()).churchName(),
                            receipt.amount()))
                    .toList();
        }

        @Override
        public List<RegionCollectionTrendDto> getRegionWiseCollectionTrend(LocalDate dateFrom, LocalDate dateTo) {
            String grouping = grouping(dateFrom, dateTo);
            return activeReceipts(null).stream()
                    .filter(receipt -> inRange(receipt.weekStart(), dateFrom, dateTo))
                    .map(receipt -> new RegionCollectionTrendDto(
                            labelFor(receipt, "period", grouping),
                            church(receipt.churchId()).regionName(),
                            receipt.amount()))
                    .toList();
        }

        private List<ChurchRow> activeChurches(Long regionId) {
            return churches.stream()
                    .filter(ChurchRow::active)
                    .filter(church -> regionId == null || church.regionId().equals(regionId))
                    .toList();
        }

        private List<ReceiptRow> activeReceipts(Long regionId) {
            return receipts.stream()
                    .filter(ReceiptRow::active)
                    .filter(receipt -> regionId == null || receipt.regionId().equals(regionId))
                    .toList();
        }

        private List<ChartDataPointDto> groupReceipts(List<ReceiptRow> rows, String group, String groupingMode) {
            List<String> labels = switch (group) {
                case "period" -> periodLabels(rows, groupingMode);
                default -> rows.stream().map(row -> labelFor(row, group, groupingMode)).distinct().toList();
            };
            return labels.stream()
                    .map(label -> new ChartDataPointDto(label, rows.stream()
                            .filter(row -> label.equals(labelFor(row, group, groupingMode)))
                            .map(ReceiptRow::amount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)))
                    .toList();
        }

        private List<String> periodLabels(List<ReceiptRow> rows, String groupingMode) {
            return rows.stream().map(row -> labelFor(row, "period", groupingMode)).distinct().toList();
        }

        private BigDecimal sumFor(String label, String type, String groupingMode, Long regionId,
                                  LocalDate dateFrom, LocalDate dateTo) {
            return activeReceipts(regionId).stream()
                    .filter(receipt -> inRange(receipt.weekStart(), dateFrom, dateTo))
                    .filter(receipt -> type.equals(receipt.collectionType()))
                    .filter(receipt -> label.equals(labelFor(receipt, "period", groupingMode)))
                    .map(ReceiptRow::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        private String labelFor(ReceiptRow row, String group, String groupingMode) {
            return switch (group) {
                case "region" -> church(row.churchId()).regionName();
                case "church" -> church(row.churchId()).churchName();
                case "type" -> row.collectionType();
                case "period" -> "MONTHLY".equals(groupingMode)
                        ? row.weekStart().toString().substring(0, 7)
                        : row.weekStart().toString();
                default -> "";
            };
        }

        private ChurchRow church(Long churchId) {
            return churches.stream()
                    .filter(church -> church.id().equals(churchId))
                    .findFirst()
                    .orElseThrow();
        }

        private ChurchRow church(Long regionId, Long churchId) {
            return churches.stream()
                    .filter(church -> church.regionId().equals(regionId) && church.id().equals(churchId))
                    .findFirst()
                    .orElseThrow();
        }

        private String grouping(LocalDate dateFrom, LocalDate dateTo) {
            return "WEEKLY";
        }

        private boolean inRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
            return !date.isBefore(dateFrom) && !date.isAfter(dateTo);
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String lastAction;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logDashboardWeeklyViewed(Long userId) {
            lastAction = DASHBOARD_WEEKLY_VIEWED;
        }

        @Override
        public void logDashboardTrendingViewed(Long userId) {
            lastAction = DASHBOARD_TRENDING_VIEWED;
        }
    }

    private record ChurchRow(Long id, Long regionId, String regionName, String churchName, boolean active) {
    }

    private record ReceiptRow(Long id, Long churchId, Long regionId, LocalDate weekStart, LocalDate receiptDate,
                              boolean active, boolean late, String collectionType, BigDecimal amount) {
    }
}
