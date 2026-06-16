package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.dashboard.ChartDataPointDto;
import com.churchmanagement.dto.dashboard.ChurchCollectionTrendDto;
import com.churchmanagement.dto.dashboard.CollectionTrendDto;
import com.churchmanagement.dto.dashboard.RegionCollectionTypeTotalDto;
import com.churchmanagement.dto.dashboard.RegionCollectionTrendDto;
import com.churchmanagement.dto.dashboard.RegionSubmissionProgressDto;
import com.churchmanagement.dto.dashboard.WeeklyDashboardDto;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class DashboardRepository {
    private final DataSource dataSource;

    public DashboardRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public DashboardRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public WeeklyDashboardDto getWeeklySummary(LocalDate weekStartDate, LocalDate weekEndDate, Long regionId) {
        WeeklyDashboardDto summary = new WeeklyDashboardDto();
        summary.setWeekStartDate(weekStartDate);
        summary.setWeekEndDate(weekEndDate);
        summary.setTotalRegions(countActiveRegions(regionId));
        summary.setCompletedRegions(countCompletedRegions(weekStartDate, weekEndDate, regionId));
        summary.setTotalChurches(countActiveChurches(regionId));
        summary.setSubmittedChurches(countSubmittedChurches(weekStartDate, weekEndDate, regionId));
        summary.setPendingChurches(countPendingChurches(weekStartDate, weekEndDate, regionId));
        summary.setLateSubmissions(countLateSubmissions(weekStartDate, weekEndDate, regionId));
        summary.setTodaysReceiptsTotal(sumTodaysReceipts(regionId));
        summary.setSmsFailedCount(countSmsFailed(weekStartDate, weekEndDate, regionId));
        LastBackupSnapshot lastBackup = findLastBackupSnapshot();
        summary.setLastBackupStatus(lastBackup.status());
        summary.setLastBackupCreatedAt(lastBackup.createdAt());
        return summary;
    }

    public List<ChartDataPointDto> getSubmittedVsPendingChart(LocalDate weekStartDate, LocalDate weekEndDate,
                                                              Long regionId) {
        return List.of(
                ChartDataPointDto.of("Submitted", countSubmittedChurches(weekStartDate, weekEndDate, regionId)),
                ChartDataPointDto.of("Pending", countPendingChurches(weekStartDate, weekEndDate, regionId))
        );
    }

    public List<RegionSubmissionProgressDto> getRegionWiseSubmissionProgress(LocalDate weekStartDate,
                                                                              LocalDate weekEndDate,
                                                                              Long regionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT rg.region_name,
                       COUNT(DISTINCT c.id) AS total_churches,
                       COUNT(DISTINCT r.church_id) AS submitted_churches
                FROM regions rg
                JOIN churches c ON c.region_id = rg.id
                LEFT JOIN receipts r ON r.church_id = c.id
                    AND r.week_start_date >= ?
                    AND r.week_start_date <= ?
                    AND r.status = 'ACTIVE'
                WHERE 1 = 1
                """);
        List<Object> parameters = baseDateParameters(weekStartDate, weekEndDate);
        if (regionId != null) {
            sql.append("AND rg.id = ? ");
            parameters.add(regionId);
        }
        sql.append("""
                GROUP BY rg.id, rg.region_code, rg.region_name
                ORDER BY rg.region_code
                """);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            setParameters(statement, parameters);
            List<RegionSubmissionProgressDto> progress = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    progress.add(new RegionSubmissionProgressDto(
                            resultSet.getString("region_name"),
                            resultSet.getLong("submitted_churches"),
                            resultSet.getLong("total_churches")
                    ));
                }
            }
            return progress;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load region submission progress.", exception);
        }
    }

    public List<RegionCollectionTypeTotalDto> getRegionWiseWeeklyCollection(LocalDate weekStartDate, LocalDate weekEndDate,
                                                                            Long regionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT rg.region_name,
                       ri.collection_type,
                       COALESCE(SUM(ri.amount), 0) AS amount
                FROM receipts r
                JOIN regions rg ON rg.id = r.region_id
                JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'ACTIVE'
                    AND r.week_start_date >= ?
                    AND r.week_start_date <= ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(Date.valueOf(weekStartDate));
        parameters.add(Date.valueOf(weekEndDate));
        if (regionId != null) {
            sql.append("AND rg.id = ? ");
            parameters.add(regionId);
        }
        sql.append("""
                GROUP BY rg.id, rg.region_code, rg.region_name, ri.collection_type
                ORDER BY rg.region_code, FIELD(ri.collection_type, 'OFFERTORY', 'TITHES', 'OTHER_DONATIONS')
                """);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            setParameters(statement, parameters);
            List<RegionCollectionTypeTotalDto> totals = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    totals.add(new RegionCollectionTypeTotalDto(
                            resultSet.getString("region_name"),
                            resultSet.getString("collection_type"),
                            resultSet.getBigDecimal("amount")
                    ));
                }
            }
            return totals;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load region weekly collection by type.", exception);
        }
    }

    public List<ChartDataPointDto> getCollectionTypeWeeklyTotals(LocalDate weekStartDate, LocalDate weekEndDate,
                                                                 Long regionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT ri.collection_type AS label, COALESCE(SUM(ri.amount), 0) AS value
                FROM receipt_items ri
                JOIN receipts r ON r.id = ri.receipt_id
                WHERE r.status = 'ACTIVE'
                  AND r.week_start_date >= ?
                  AND r.week_start_date <= ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(Date.valueOf(weekStartDate));
        parameters.add(Date.valueOf(weekEndDate));
        appendRegionFilter(sql, parameters, regionId, "r");
        appendCollectionTypeGrouping(sql);
        return queryPoints(sql.toString(), statement -> setParameters(statement, parameters));
    }

    public List<ChartDataPointDto> getCollectionTypeWeekReceiptTotals(LocalDate weekStartDate, LocalDate weekEndDate,
                                                                      Long regionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT ri.collection_type AS label, COALESCE(SUM(ri.amount), 0) AS value
                FROM receipt_items ri
                JOIN receipts r ON r.id = ri.receipt_id
                WHERE r.status = 'ACTIVE'
                  AND DATE(r.created_at) >= ?
                  AND DATE(r.created_at) <= ?
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(Date.valueOf(weekStartDate));
        parameters.add(Date.valueOf(weekEndDate));
        appendRegionFilter(sql, parameters, regionId, "r");
        appendCollectionTypeGrouping(sql);
        return queryPoints(sql.toString(), statement -> setParameters(statement, parameters));
    }

    public List<ChartDataPointDto> getTopWeeklyChurchCollections(LocalDate weekStartDate, LocalDate weekEndDate,
                                                                 Long regionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.church_name AS label, COALESCE(SUM(ri.amount), 0) AS value
                FROM receipts r
                JOIN churches c ON c.id = r.church_id
                JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'ACTIVE'
                  AND r.week_start_date >= ?
                  AND r.week_start_date <= ?
                """);
        List<Object> parameters = baseDateParameters(weekStartDate, weekEndDate);
        appendRegionFilter(sql, parameters, regionId, "r");
        sql.append("""
                GROUP BY c.id, c.church_name
                HAVING value > 0
                ORDER BY value DESC, c.church_name
                LIMIT 20
                """);
        return queryPoints(sql.toString(), statement -> setParameters(statement, parameters));
    }

    public List<ChartDataPointDto> getTopWeeklyRegionCollections(LocalDate weekStartDate, LocalDate weekEndDate,
                                                                 Long regionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT rg.region_name AS label, COALESCE(SUM(ri.amount), 0) AS value
                FROM receipts r
                JOIN regions rg ON rg.id = r.region_id
                JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'ACTIVE'
                  AND r.week_start_date >= ?
                  AND r.week_start_date <= ?
                """);
        List<Object> parameters = baseDateParameters(weekStartDate, weekEndDate);
        appendRegionFilter(sql, parameters, regionId, "r");
        sql.append("""
                GROUP BY rg.id, rg.region_name
                HAVING value > 0
                ORDER BY value DESC, rg.region_name
                LIMIT 3
                """);
        return queryPoints(sql.toString(), statement -> setParameters(statement, parameters));
    }

    private void appendCollectionTypeGrouping(StringBuilder sql) {
        sql.append("""
                GROUP BY ri.collection_type
                ORDER BY FIELD(ri.collection_type, 'OFFERTORY', 'TITHES', 'OTHER_DONATIONS')
                """);
    }

    public List<ChartDataPointDto> getTotalCollectionTrend(LocalDate dateFrom, LocalDate dateTo, Long regionId) {
        String periodExpression = periodExpression(dateFrom, dateTo);
        StringBuilder sql = new StringBuilder("""
                SELECT %s AS label, COALESCE(SUM(ri.amount), 0) AS value
                FROM receipts r
                JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'ACTIVE'
                  AND r.week_start_date >= ?
                  AND r.week_start_date <= ?
                """.formatted(periodExpression));
        List<Object> parameters = baseDateParameters(dateFrom, dateTo);
        appendRegionFilter(sql, parameters, regionId, "r");
        sql.append("GROUP BY ").append(periodExpression).append(" ORDER BY label");
        return queryPoints(sql.toString(), statement -> setParameters(statement, parameters));
    }

    public List<CollectionTrendDto> getCollectionTypeWiseTrend(LocalDate dateFrom, LocalDate dateTo, Long regionId) {
        String periodExpression = periodExpression(dateFrom, dateTo);
        StringBuilder sql = new StringBuilder("""
                SELECT %s AS period_label,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OFFERTORY' THEN ri.amount ELSE 0 END), 0) AS offertory_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'TITHES' THEN ri.amount ELSE 0 END), 0) AS tithes_total,
                       COALESCE(SUM(CASE WHEN ri.collection_type = 'OTHER_DONATIONS' THEN ri.amount ELSE 0 END), 0) AS other_donations_total
                FROM receipts r
                JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'ACTIVE'
                  AND r.week_start_date >= ?
                  AND r.week_start_date <= ?
                """.formatted(periodExpression));
        List<Object> parameters = baseDateParameters(dateFrom, dateTo);
        appendRegionFilter(sql, parameters, regionId, "r");
        sql.append("GROUP BY ").append(periodExpression).append(" ORDER BY period_label");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            setParameters(statement, parameters);
            List<CollectionTrendDto> trends = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    trends.add(new CollectionTrendDto(
                            resultSet.getString("period_label"),
                            resultSet.getBigDecimal("offertory_total"),
                            resultSet.getBigDecimal("tithes_total"),
                            resultSet.getBigDecimal("other_donations_total")
                    ));
                }
            }
            return trends;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load collection type trend.", exception);
        }
    }

    public List<ChurchCollectionTrendDto> getChurchWiseCollectionTrend(LocalDate dateFrom, LocalDate dateTo,
                                                                       Long regionId, List<Long> churchIds) {
        List<Long> selectedChurchIds = churchIds == null ? List.of() : churchIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        List<Long> trendChurchIds = selectedChurchIds.isEmpty()
                ? findTopChurchIdsForTrend(dateFrom, dateTo, regionId)
                : selectedChurchIds;
        if (trendChurchIds.isEmpty()) {
            return List.of();
        }

        String periodExpression = periodExpression(dateFrom, dateTo);
        StringBuilder sql = new StringBuilder("""
                SELECT %s AS period_label,
                       c.church_name,
                       COALESCE(SUM(ri.amount), 0) AS amount
                FROM receipts r
                JOIN churches c ON c.id = r.church_id
                JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'ACTIVE'
                  AND r.week_start_date >= ?
                  AND r.week_start_date <= ?
                """.formatted(periodExpression));
        List<Object> parameters = baseDateParameters(dateFrom, dateTo);
        appendRegionFilter(sql, parameters, regionId, "c");
        appendIdFilter(sql, parameters, "r.church_id", trendChurchIds);
        sql.append("GROUP BY ").append(periodExpression).append(", c.id, c.church_name ")
                .append("ORDER BY period_label, c.church_name");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            setParameters(statement, parameters);
            List<ChurchCollectionTrendDto> trends = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    trends.add(new ChurchCollectionTrendDto(
                            resultSet.getString("period_label"),
                            resultSet.getString("church_name"),
                            resultSet.getBigDecimal("amount")
                    ));
                }
            }
            return trends;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load church collection trend.", exception);
        }
    }

    public List<RegionCollectionTrendDto> getRegionWiseCollectionTrend(LocalDate dateFrom, LocalDate dateTo) {
        String periodExpression = periodExpression(dateFrom, dateTo);
        String sql = """
                SELECT %s AS period_label,
                       rg.region_name,
                       COALESCE(SUM(ri.amount), 0) AS amount
                FROM receipts r
                JOIN regions rg ON rg.id = r.region_id
                JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'ACTIVE'
                  AND r.week_start_date >= ?
                  AND r.week_start_date <= ?
                GROUP BY %s, rg.id, rg.region_name
                HAVING amount > 0
                ORDER BY period_label, rg.region_name
                """.formatted(periodExpression, periodExpression);
        List<Object> parameters = baseDateParameters(dateFrom, dateTo);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setParameters(statement, parameters);
            List<RegionCollectionTrendDto> trends = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    trends.add(new RegionCollectionTrendDto(
                            resultSet.getString("period_label"),
                            resultSet.getString("region_name"),
                            resultSet.getBigDecimal("amount")
                    ));
                }
            }
            return trends;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load region collection trend.", exception);
        }
    }

    private long countActiveRegions(Long regionId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM regions rg WHERE 1 = 1 ");
        List<Object> parameters = new ArrayList<>();
        if (regionId != null) {
            sql.append("AND rg.id = ? ");
            parameters.add(regionId);
        }
        return queryLong(sql.toString(), statement -> setParameters(statement, parameters));
    }

    private long countCompletedRegions(LocalDate weekStartDate, LocalDate weekEndDate, Long regionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM (
                    SELECT rg.id,
                           COUNT(DISTINCT c.id) AS active_church_count,
                           COUNT(DISTINCT r.church_id) AS submitted_church_count
                    FROM regions rg
                    JOIN churches c ON c.region_id = rg.id
                    LEFT JOIN receipts r ON r.church_id = c.id
                        AND r.week_start_date >= ?
                        AND r.week_start_date <= ?
                        AND r.status = 'ACTIVE'
                    WHERE 1 = 1
                """);
        List<Object> parameters = baseDateParameters(weekStartDate, weekEndDate);
        if (regionId != null) {
            sql.append("AND rg.id = ? ");
            parameters.add(regionId);
        }
        sql.append("""
                    GROUP BY rg.id
                    HAVING active_church_count > 0
                       AND submitted_church_count = active_church_count
                ) completed_regions
                """);
        return queryLong(sql.toString(), statement -> setParameters(statement, parameters));
    }

    private long countActiveChurches(Long regionId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM churches c WHERE 1 = 1 ");
        List<Object> parameters = new ArrayList<>();
        appendRegionFilter(sql, parameters, regionId, "c");
        return queryLong(sql.toString(), statement -> setParameters(statement, parameters));
    }

    private long countSubmittedChurches(LocalDate weekStartDate, LocalDate weekEndDate, Long regionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT r.church_id)
                FROM receipts r
                JOIN churches c ON c.id = r.church_id
                WHERE r.week_start_date >= ?
                  AND r.week_start_date <= ?
                  AND r.status = 'ACTIVE'
                """);
        List<Object> parameters = baseDateParameters(weekStartDate, weekEndDate);
        appendRegionFilter(sql, parameters, regionId, "c");
        return queryLong(sql.toString(), statement -> setParameters(statement, parameters));
    }

    private long countPendingChurches(LocalDate weekStartDate, LocalDate weekEndDate, Long regionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM churches c
                WHERE NOT EXISTS (
                      SELECT 1
                      FROM receipts r
                      WHERE r.church_id = c.id
                        AND r.week_start_date >= ?
                        AND r.week_start_date <= ?
                        AND r.status = 'ACTIVE'
                  )
                """);
        List<Object> parameters = baseDateParameters(weekStartDate, weekEndDate);
        appendRegionFilter(sql, parameters, regionId, "c");
        return queryLong(sql.toString(), statement -> setParameters(statement, parameters));
    }

    private long countLateSubmissions(LocalDate weekStartDate, LocalDate weekEndDate, Long regionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM receipts r
                WHERE r.status = 'ACTIVE'
                  AND r.is_late_submission = TRUE
                  AND r.week_start_date >= ?
                  AND r.week_start_date <= ?
                """);
        List<Object> parameters = baseDateParameters(weekStartDate, weekEndDate);
        appendRegionFilter(sql, parameters, regionId, "r");
        return queryLong(sql.toString(), statement -> setParameters(statement, parameters));
    }

    private BigDecimal sumTodaysReceipts(Long regionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(SUM(ri.amount), 0)
                FROM receipts r
                JOIN churches c ON c.id = r.church_id
                JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'ACTIVE'
                  AND DATE(r.receipt_datetime) = CURRENT_DATE
                """);
        List<Object> parameters = new ArrayList<>();
        appendRegionFilter(sql, parameters, regionId, "c");
        return queryDecimal(sql.toString(), statement -> setParameters(statement, parameters));
    }

    private long countSmsFailed(LocalDate dateFrom, LocalDate dateTo, Long regionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM sms_logs sl
                LEFT JOIN churches c ON c.id = sl.church_id
                WHERE sl.status = 'FAILED'
                  AND DATE(sl.created_at) >= ?
                  AND DATE(sl.created_at) <= ?
                """);
        List<Object> parameters = baseDateParameters(dateFrom, dateTo);
        if (regionId != null) {
            sql.append("AND c.region_id = ? ");
            parameters.add(regionId);
        }
        return queryLong(sql.toString(), statement -> setParameters(statement, parameters));
    }

    private LastBackupSnapshot findLastBackupSnapshot() {
        String sql = "SELECT status, created_at FROM backup_logs ORDER BY created_at DESC, id DESC LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return new LastBackupSnapshot("No backups", null);
            }
            return new LastBackupSnapshot(
                    resultSet.getString("status"),
                    resultSet.getTimestamp("created_at") == null
                            ? null
                            : resultSet.getTimestamp("created_at").toLocalDateTime());
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load last backup status.", exception);
        }
    }

    private String periodExpression(LocalDate dateFrom, LocalDate dateTo) {
        return "DATE_FORMAT(r.week_start_date, '%Y-%m-%d')";
    }

    private List<Object> baseDateParameters(LocalDate dateFrom, LocalDate dateTo) {
        List<Object> parameters = new ArrayList<>();
        parameters.add(Date.valueOf(dateFrom));
        parameters.add(Date.valueOf(dateTo));
        return parameters;
    }

    private void appendRegionFilter(StringBuilder sql, List<Object> parameters, Long regionId, String tableAlias) {
        if (regionId != null) {
            sql.append("AND ").append(tableAlias).append(".region_id = ? ");
            parameters.add(regionId);
        }
    }

    private void appendIdFilter(StringBuilder sql, List<Object> parameters, String columnName, List<Long> ids) {
        sql.append("AND ").append(columnName).append(" IN (");
        for (int index = 0; index < ids.size(); index++) {
            if (index > 0) {
                sql.append(", ");
            }
            sql.append("?");
            parameters.add(ids.get(index));
        }
        sql.append(") ");
    }

    private List<Long> findTopChurchIdsForTrend(LocalDate dateFrom, LocalDate dateTo, Long regionId) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.church_id
                FROM receipts r
                JOIN churches c ON c.id = r.church_id
                JOIN receipt_items ri ON ri.receipt_id = r.id
                WHERE r.status = 'ACTIVE'
                  AND r.week_start_date >= ?
                  AND r.week_start_date <= ?
                """);
        List<Object> parameters = baseDateParameters(dateFrom, dateTo);
        appendRegionFilter(sql, parameters, regionId, "c");
        sql.append("""
                GROUP BY r.church_id, c.church_name
                ORDER BY COALESCE(SUM(ri.amount), 0) DESC, c.church_name
                LIMIT 5
                """);
        return queryLongList(sql.toString(), statement -> setParameters(statement, parameters));
    }

    private long queryLong(String sql, SqlBinder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load dashboard count.", exception);
        }
    }

    private BigDecimal queryDecimal(String sql, SqlBinder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load dashboard total.", exception);
        }
    }

    private List<ChartDataPointDto> queryPoints(String sql, SqlBinder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            List<ChartDataPointDto> points = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    points.add(new ChartDataPointDto(resultSet.getString("label"), resultSet.getBigDecimal("value")));
                }
            }
            return points;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load dashboard chart data.", exception);
        }
    }

    private List<Long> queryLongList(String sql, SqlBinder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            List<Long> values = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.add(resultSet.getLong(1));
                }
            }
            return values;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load dashboard identifiers.", exception);
        }
    }

    private void setParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            statement.setObject(index + 1, parameters.get(index));
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private record LastBackupSnapshot(String status, LocalDateTime createdAt) {
    }
}
