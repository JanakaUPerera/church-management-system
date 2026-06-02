package com.churchmanagement.dto.dashboard;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TrendingDashboardDto {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private String groupingMode;
    private boolean chartsVisible;
    private List<ChartDataPointDto> totalCollectionTrend = new ArrayList<>();
    private List<CollectionTrendDto> collectionTypeWiseTrend = new ArrayList<>();
    private List<ChurchCollectionTrendDto> churchWiseCollectionTrend = new ArrayList<>();
    private List<RegionCollectionTrendDto> regionWiseCollectionTrend = new ArrayList<>();

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }

    public String getGroupingMode() {
        return groupingMode;
    }

    public void setGroupingMode(String groupingMode) {
        this.groupingMode = groupingMode;
    }

    public boolean isChartsVisible() {
        return chartsVisible;
    }

    public void setChartsVisible(boolean chartsVisible) {
        this.chartsVisible = chartsVisible;
    }

    public List<ChartDataPointDto> getTotalCollectionTrend() {
        return totalCollectionTrend;
    }

    public void setTotalCollectionTrend(List<ChartDataPointDto> totalCollectionTrend) {
        this.totalCollectionTrend = safePointList(totalCollectionTrend);
    }

    public List<CollectionTrendDto> getCollectionTypeWiseTrend() {
        return collectionTypeWiseTrend;
    }

    public void setCollectionTypeWiseTrend(List<CollectionTrendDto> collectionTypeWiseTrend) {
        this.collectionTypeWiseTrend = collectionTypeWiseTrend == null
                ? new ArrayList<>()
                : new ArrayList<>(collectionTypeWiseTrend);
    }

    public List<ChurchCollectionTrendDto> getChurchWiseCollectionTrend() {
        return churchWiseCollectionTrend;
    }

    public void setChurchWiseCollectionTrend(List<ChurchCollectionTrendDto> churchWiseCollectionTrend) {
        this.churchWiseCollectionTrend = churchWiseCollectionTrend == null
                ? new ArrayList<>()
                : new ArrayList<>(churchWiseCollectionTrend);
    }

    public List<RegionCollectionTrendDto> getRegionWiseCollectionTrend() {
        return regionWiseCollectionTrend;
    }

    public void setRegionWiseCollectionTrend(List<RegionCollectionTrendDto> regionWiseCollectionTrend) {
        this.regionWiseCollectionTrend = regionWiseCollectionTrend == null
                ? new ArrayList<>()
                : new ArrayList<>(regionWiseCollectionTrend);
    }

    private List<ChartDataPointDto> safePointList(List<ChartDataPointDto> points) {
        return points == null ? new ArrayList<>() : new ArrayList<>(points);
    }
}
