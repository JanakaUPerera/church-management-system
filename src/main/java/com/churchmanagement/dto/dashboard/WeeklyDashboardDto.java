package com.churchmanagement.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WeeklyDashboardDto {
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private long totalRegions;
    private long completedRegions;
    private long totalChurches;
    private long submittedChurches;
    private long pendingChurches;
    private long lateSubmissions;
    private BigDecimal todaysReceiptsTotal = BigDecimal.ZERO;
    private long smsFailedCount;
    private String lastBackupStatus = "No backups";
    private LocalDateTime lastBackupCreatedAt;
    private boolean receiptSummaryVisible;
    private boolean chartsVisible;
    private boolean smsFailedVisible;
    private boolean backupStatusVisible;
    private boolean todaysReceiptsTotalVisible;
    private boolean lateSubmissionsVisible;
    private boolean weekCollectionVisible;
    private List<ChartDataPointDto> submittedVsPendingChart = new ArrayList<>();
    private List<RegionSubmissionProgressDto> regionSubmissionProgress = new ArrayList<>();
    private List<RegionCollectionTypeTotalDto> regionWiseWeeklyCollection = new ArrayList<>();
    private List<ChartDataPointDto> collectionTypeWeekReceiptTotals = new ArrayList<>();
    private List<ChartDataPointDto> collectionTypeWeeklyTotals = new ArrayList<>();
    private List<ChartDataPointDto> topWeeklyChurchCollections = new ArrayList<>();
    private List<ChartDataPointDto> topWeeklyRegionCollections = new ArrayList<>();

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public void setWeekStartDate(LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
    }

    public LocalDate getWeekEndDate() {
        return weekEndDate;
    }

    public void setWeekEndDate(LocalDate weekEndDate) {
        this.weekEndDate = weekEndDate;
    }

    public long getTotalRegions() {
        return totalRegions;
    }

    public void setTotalRegions(long totalRegions) {
        this.totalRegions = totalRegions;
    }

    public long getCompletedRegions() {
        return completedRegions;
    }

    public void setCompletedRegions(long completedRegions) {
        this.completedRegions = completedRegions;
    }

    public long getTotalChurches() {
        return totalChurches;
    }

    public void setTotalChurches(long totalChurches) {
        this.totalChurches = totalChurches;
    }

    public long getSubmittedChurches() {
        return submittedChurches;
    }

    public void setSubmittedChurches(long submittedChurches) {
        this.submittedChurches = submittedChurches;
    }

    public long getPendingChurches() {
        return pendingChurches;
    }

    public void setPendingChurches(long pendingChurches) {
        this.pendingChurches = pendingChurches;
    }

    public long getLateSubmissions() {
        return lateSubmissions;
    }

    public void setLateSubmissions(long lateSubmissions) {
        this.lateSubmissions = lateSubmissions;
    }

    public BigDecimal getTodaysReceiptsTotal() {
        return todaysReceiptsTotal == null ? BigDecimal.ZERO : todaysReceiptsTotal;
    }

    public void setTodaysReceiptsTotal(BigDecimal todaysReceiptsTotal) {
        this.todaysReceiptsTotal = todaysReceiptsTotal == null ? BigDecimal.ZERO : todaysReceiptsTotal;
    }

    public long getSmsFailedCount() {
        return smsFailedCount;
    }

    public void setSmsFailedCount(long smsFailedCount) {
        this.smsFailedCount = smsFailedCount;
    }

    public String getLastBackupStatus() {
        return lastBackupStatus;
    }

    public void setLastBackupStatus(String lastBackupStatus) {
        this.lastBackupStatus = lastBackupStatus == null || lastBackupStatus.isBlank() ? "No backups" : lastBackupStatus;
    }

    public LocalDateTime getLastBackupCreatedAt() {
        return lastBackupCreatedAt;
    }

    public void setLastBackupCreatedAt(LocalDateTime lastBackupCreatedAt) {
        this.lastBackupCreatedAt = lastBackupCreatedAt;
    }

    public boolean isReceiptSummaryVisible() {
        return receiptSummaryVisible;
    }

    public void setReceiptSummaryVisible(boolean receiptSummaryVisible) {
        this.receiptSummaryVisible = receiptSummaryVisible;
    }

    public boolean isChartsVisible() {
        return chartsVisible;
    }

    public void setChartsVisible(boolean chartsVisible) {
        this.chartsVisible = chartsVisible;
    }

    public boolean isSmsFailedVisible() {
        return smsFailedVisible;
    }

    public void setSmsFailedVisible(boolean smsFailedVisible) {
        this.smsFailedVisible = smsFailedVisible;
    }

    public boolean isBackupStatusVisible() {
        return backupStatusVisible;
    }

    public void setBackupStatusVisible(boolean backupStatusVisible) {
        this.backupStatusVisible = backupStatusVisible;
    }

    public boolean isTodaysReceiptsTotalVisible() {
        return todaysReceiptsTotalVisible;
    }

    public void setTodaysReceiptsTotalVisible(boolean todaysReceiptsTotalVisible) {
        this.todaysReceiptsTotalVisible = todaysReceiptsTotalVisible;
    }

    public boolean isLateSubmissionsVisible() {
        return lateSubmissionsVisible;
    }

    public void setLateSubmissionsVisible(boolean lateSubmissionsVisible) {
        this.lateSubmissionsVisible = lateSubmissionsVisible;
    }

    public boolean isWeekCollectionVisible() {
        return weekCollectionVisible;
    }

    public void setWeekCollectionVisible(boolean weekCollectionVisible) {
        this.weekCollectionVisible = weekCollectionVisible;
    }

    public List<ChartDataPointDto> getSubmittedVsPendingChart() {
        return submittedVsPendingChart;
    }

    public void setSubmittedVsPendingChart(List<ChartDataPointDto> submittedVsPendingChart) {
        this.submittedVsPendingChart = safeList(submittedVsPendingChart);
    }

    public List<RegionSubmissionProgressDto> getRegionSubmissionProgress() {
        return regionSubmissionProgress;
    }

    public void setRegionSubmissionProgress(List<RegionSubmissionProgressDto> regionSubmissionProgress) {
        this.regionSubmissionProgress = regionSubmissionProgress == null
                ? new ArrayList<>()
                : new ArrayList<>(regionSubmissionProgress);
    }

    public List<RegionCollectionTypeTotalDto> getRegionWiseWeeklyCollection() {
        return regionWiseWeeklyCollection;
    }

    public void setRegionWiseWeeklyCollection(List<RegionCollectionTypeTotalDto> regionWiseWeeklyCollection) {
        this.regionWiseWeeklyCollection = regionWiseWeeklyCollection == null
                ? new ArrayList<>()
                : new ArrayList<>(regionWiseWeeklyCollection);
    }

    public List<ChartDataPointDto> getCollectionTypeWeekReceiptTotals() {
        return collectionTypeWeekReceiptTotals;
    }

    public void setCollectionTypeWeekReceiptTotals(List<ChartDataPointDto> collectionTypeWeekReceiptTotals) {
        this.collectionTypeWeekReceiptTotals = safeList(collectionTypeWeekReceiptTotals);
    }

    public List<ChartDataPointDto> getCollectionTypeWeeklyTotals() {
        return collectionTypeWeeklyTotals;
    }

    public void setCollectionTypeWeeklyTotals(List<ChartDataPointDto> collectionTypeWeeklyTotals) {
        this.collectionTypeWeeklyTotals = safeList(collectionTypeWeeklyTotals);
    }

    public List<ChartDataPointDto> getTopWeeklyChurchCollections() {
        return topWeeklyChurchCollections;
    }

    public void setTopWeeklyChurchCollections(List<ChartDataPointDto> topWeeklyChurchCollections) {
        this.topWeeklyChurchCollections = safeList(topWeeklyChurchCollections);
    }

    public List<ChartDataPointDto> getTopWeeklyRegionCollections() {
        return topWeeklyRegionCollections;
    }

    public void setTopWeeklyRegionCollections(List<ChartDataPointDto> topWeeklyRegionCollections) {
        this.topWeeklyRegionCollections = safeList(topWeeklyRegionCollections);
    }

    private List<ChartDataPointDto> safeList(List<ChartDataPointDto> points) {
        return points == null ? new ArrayList<>() : new ArrayList<>(points);
    }
}
