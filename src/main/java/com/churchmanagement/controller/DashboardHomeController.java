package com.churchmanagement.controller;

import com.churchmanagement.dto.dashboard.ChartDataPointDto;
import com.churchmanagement.dto.dashboard.ChurchCollectionTrendDto;
import com.churchmanagement.dto.dashboard.CollectionTrendDto;
import com.churchmanagement.dto.dashboard.RegionCollectionTypeTotalDto;
import com.churchmanagement.dto.dashboard.RegionCollectionTrendDto;
import com.churchmanagement.dto.dashboard.RegionSubmissionProgressDto;
import com.churchmanagement.dto.dashboard.TrendingDashboardDto;
import com.churchmanagement.dto.dashboard.WeeklyDashboardDto;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Region;
import com.churchmanagement.service.ChurchService;
import com.churchmanagement.service.DashboardService;
import com.churchmanagement.service.RegionService;
import com.churchmanagement.util.ComboBoxUtil;
import com.churchmanagement.util.DatePickerUtil;
import com.churchmanagement.util.SystemDateTimeFormatter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.geometry.HPos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardHomeController {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#,##0.##");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.#");
    private static final String[] FALLBACK_CHART_COLORS = {
            "#f59e0b", "#2563eb", "#16a34a", "#7c3aed", "#dc2626", "#0891b2", "#ca8a04", "#4f46e5",
            "#ea580c", "#0d9488", "#9333ea", "#65a30d", "#db2777", "#0284c7", "#b45309", "#4338ca",
            "#be123c", "#15803d", "#c026d3", "#0369a1"
    };

    private final DashboardService dashboardService = new DashboardService();
    private final RegionService regionService = new RegionService();
    private final ChurchService churchService = new ChurchService();
    private final SystemDateTimeFormatter dateTimeFormatter = new SystemDateTimeFormatter();
    private final List<Church> trendingChurches = new ArrayList<>();
    private Long trendingChurchRegionId;
    private boolean rebuildingTrendingChurchMenu;

    @FXML private DatePicker weeklyWeekDatePicker;
    @FXML private ComboBox<Region> weeklyRegionComboBox;
    @FXML private VBox weeklyReceiptCards;
    @FXML private VBox weeklySmsCard;
    @FXML private VBox weeklyBackupCard;
    @FXML private VBox weeklyCollectionAmountsBox;
    @FXML private VBox topWeeklyChurchesBox;
    @FXML private VBox topWeeklyRegionsBox;
    @FXML private VBox weeklyChartsSection;
    @FXML private Label completedRegionsLabel;
    @FXML private VBox submittedChurchesBox;
    @FXML private Label submittedChurchesLabel;
    @FXML private VBox pendingChurchesBox;
    @FXML private Label pendingChurchesLabel;
    @FXML private VBox lateSubmissionsBox;
    @FXML private Label lateSubmissionsLabel;
    @FXML private VBox todaysReceiptsSummaryBox;
    @FXML private Label todaysReceiptsTotalLabel;
    @FXML private Label smsFailedCountLabel;
    @FXML private Label lastBackupStatusLabel;
    @FXML private Label lastBackupTimeLabel;
    @FXML private PieChart submittedPendingPieChart;
    @FXML private PieChart collectionTypeWeeklyPieChart;
    @FXML private VBox regionSubmissionProgressRowsBox;
    @FXML private BarChart<String, Number> regionWeeklyCollectionChart;
    @FXML private CategoryAxis regionWeeklyXAxis;
    @FXML private NumberAxis regionWeeklyYAxis;

    @FXML private DatePicker trendingDateFromPicker;
    @FXML private DatePicker trendingDateToPicker;
    @FXML private ComboBox<Region> trendingRegionComboBox;
    @FXML private Label trendingMessageLabel;
    @FXML private MenuButton trendingChurchMenuButton;
    @FXML private VBox trendingChartsSection;
    @FXML private LineChart<String, Number> totalCollectionTrendChart;
    @FXML private NumberAxis totalCollectionTrendYAxis;
    @FXML private LineChart<String, Number> collectionTypeTrendChart;
    @FXML private NumberAxis collectionTypeTrendYAxis;
    @FXML private LineChart<String, Number> churchCollectionTrendChart;
    @FXML private NumberAxis churchCollectionTrendYAxis;
    @FXML private LineChart<String, Number> regionCollectionTrendChart;
    @FXML private NumberAxis regionCollectionTrendYAxis;

    @FXML
    private void initialize() {
        configureFilters();
        configureCharts();
        loadRegions();
        loadTrendingChurches();
        loadWeekly(false);
        loadTrending(false);
    }

    @FXML
    private void refreshWeekly() {
        loadWeekly(false);
    }

    @FXML
    private void refreshTrending() {
        loadTrending(false);
    }

    @FXML
    private void weeklyFiltersChanged() {
        loadWeekly(true);
    }

    @FXML
    private void previousWeek() {
        shiftWeeklyDate(-1);
    }

    @FXML
    private void nextWeek() {
        shiftWeeklyDate(1);
    }

    @FXML
    private void trendingFiltersChanged() {
        reloadTrendingChurchesIfRegionChanged();
        loadTrending(true);
    }

    @FXML
    private void clearWeeklyRegionFilter() {
        weeklyRegionComboBox.getSelectionModel().clearSelection();
        loadWeekly(true);
    }

    @FXML
    private void clearTrendingRegionFilter() {
        trendingRegionComboBox.getSelectionModel().clearSelection();
        reloadTrendingChurchesIfRegionChanged();
        loadTrending(true);
    }

    @FXML
    private void clearTrendingChurchSelection() {
        rebuildingTrendingChurchMenu = true;
        trendingChurchMenuButton.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .forEach(item -> item.setSelected(false));
        rebuildingTrendingChurchMenu = false;
        updateTrendingChurchMenuText();
        loadChurchTrend(true);
    }

    @FXML
    private void applyMonthRange() {
        applyTrendingRange(dashboardService.quickMonthRange());
    }

    @FXML
    private void applyQuarterRange() {
        applyTrendingRange(dashboardService.quickQuarterRange());
    }

    @FXML
    private void applyHalfYearRange() {
        applyTrendingRange(dashboardService.quickHalfYearRange());
    }

    @FXML
    private void applyYearRange() {
        applyTrendingRange(dashboardService.quickYearRange());
    }

    private void shiftWeeklyDate(int weeks) {
        LocalDate selectedWeek = weeklyWeekDatePicker.getValue();
        if (selectedWeek == null) {
            selectedWeek = dashboardService.defaultWeeklyRange().dateFrom();
        }
        weeklyWeekDatePicker.setValue(selectedWeek.plusWeeks(weeks));
        loadWeekly(true);
    }

    private void configureFilters() {
        DashboardService.DateRange weeklyRange = dashboardService.defaultWeeklyRange();
        DashboardService.DateRange trendingRange = dashboardService.defaultTrendingRange();
        DatePickerUtil.enableMondaysOnlyAndDisableFutureDates(weeklyWeekDatePicker);
        DatePickerUtil.disableFutureDates(trendingDateFromPicker);
        DatePickerUtil.disableFutureDates(trendingDateToPicker);
        weeklyWeekDatePicker.setValue(weeklyRange.dateFrom());
        trendingDateFromPicker.setValue(trendingRange.dateFrom());
        trendingDateToPicker.setValue(trendingRange.dateTo());
        ComboBoxUtil.makeSearchable(weeklyRegionComboBox, this::regionDisplayText);
        ComboBoxUtil.makeSearchable(trendingRegionComboBox, this::regionDisplayText);
    }

    private void configureCharts() {
        submittedPendingPieChart.setLegendVisible(true);
        submittedPendingPieChart.setAnimated(false);
        submittedPendingPieChart.setLabelsVisible(false);
        collectionTypeWeeklyPieChart.setLegendVisible(true);
        collectionTypeWeeklyPieChart.setAnimated(false);
        collectionTypeWeeklyPieChart.setLabelsVisible(false);
        regionWeeklyCollectionChart.setAnimated(false);
        regionWeeklyCollectionChart.setLegendVisible(false);
        regionWeeklyCollectionChart.setHorizontalGridLinesVisible(true);
        regionWeeklyCollectionChart.setVerticalGridLinesVisible(true);
        regionWeeklyXAxis.setAnimated(false);
        regionWeeklyXAxis.setAutoRanging(false);
        regionWeeklyXAxis.setTickLabelRotation(90);
        regionWeeklyYAxis.setAnimated(false);
        totalCollectionTrendChart.setLegendVisible(false);
        collectionTypeTrendChart.setLegendVisible(true);
        churchCollectionTrendChart.setLegendVisible(false);
        regionCollectionTrendChart.setLegendVisible(true);

        List.of(regionWeeklyYAxis, totalCollectionTrendYAxis, collectionTypeTrendYAxis,
                churchCollectionTrendYAxis, regionCollectionTrendYAxis)
                .forEach(axis -> axis.setLabel("Amount"));
    }

    private void loadRegions() {
        try {
            List<Region> regions = regionService.findAll();
            weeklyRegionComboBox.setItems(FXCollections.observableArrayList(regions));
            trendingRegionComboBox.setItems(FXCollections.observableArrayList(regions));
        } catch (RuntimeException exception) {
            weeklyRegionComboBox.setItems(FXCollections.observableArrayList());
            trendingRegionComboBox.setItems(FXCollections.observableArrayList());
        }
    }

    private String regionDisplayText(Region region) {
        if (region == null) {
            return "";
        }
        return region.getRegionCode() + " - " + region.getRegionName();
    }

    private void loadTrendingChurches() {
        try {
            Region region = trendingRegionComboBox.getValue();
            Long regionId = region == null ? null : region.getId();
            trendingChurches.clear();
            trendingChurches.addAll(churchService.findAll().stream()
                    .filter(church -> regionId == null || regionId.equals(church.getRegionId()))
                    .toList());
            rebuildingTrendingChurchMenu = true;
            trendingChurchMenuButton.getItems().clear();
            for (Church church : trendingChurches) {
                CheckMenuItem item = new CheckMenuItem(church.getChurchCode() + " - " + church.getChurchName());
                item.setUserData(church.getId());
                item.selectedProperty().addListener((observable, oldValue, selected) -> {
                    if (rebuildingTrendingChurchMenu) {
                        return;
                    }
                    updateTrendingChurchMenuText();
                    loadChurchTrend(true);
                });
                trendingChurchMenuButton.getItems().add(item);
            }
            rebuildingTrendingChurchMenu = false;
            trendingChurchRegionId = regionId;
            updateTrendingChurchMenuText();
        } catch (RuntimeException exception) {
            rebuildingTrendingChurchMenu = false;
            trendingChurches.clear();
            trendingChurchMenuButton.getItems().clear();
            trendingChurchRegionId = null;
            trendingChurchMenuButton.setText("Top churches");
        }
    }

    private void reloadTrendingChurchesIfRegionChanged() {
        Region region = trendingRegionComboBox.getValue();
        Long regionId = region == null ? null : region.getId();
        if (java.util.Objects.equals(trendingChurchRegionId, regionId)) {
            return;
        }
        loadTrendingChurches();
    }

    private void loadWeekly(boolean filterChanged) {
        try {
            Region region = weeklyRegionComboBox.getValue();
            WeeklyDashboardDto weekly = dashboardService.loadWeeklyDashboard(
                    weeklyWeekDatePicker.getValue(),
                    weeklyWeekDatePicker.getValue() == null ? null : weeklyWeekDatePicker.getValue().plusDays(6),
                    region == null ? null : region.getId());
            applyWeekly(weekly);
            if (filterChanged) {
                dashboardService.logWeeklyFilterChanged(weekly.getWeekStartDate(), weekly.getWeekEndDate(),
                        region == null ? "All Regions" : region.getRegionName());
            }
        } catch (RuntimeException exception) {
            clearWeeklyCharts();
        }
    }

    private void loadTrending(boolean filterChanged) {
        try {
            Region region = trendingRegionComboBox.getValue();
            TrendingDashboardDto trending = dashboardService.loadTrendingDashboard(
                    trendingDateFromPicker.getValue(),
                    trendingDateToPicker.getValue(),
                    region == null ? null : region.getId(),
                    selectedTrendingChurchIds());
            applyTrending(trending);
            trendingMessageLabel.setText("Grouping: " + displayGrouping(trending.getGroupingMode()));
            if (filterChanged) {
                dashboardService.logTrendingFilterChanged(trending.getDateFrom(), trending.getDateTo(),
                        region == null ? "All Regions" : region.getRegionName());
            }
        } catch (RuntimeException exception) {
            trendingMessageLabel.setText(exception.getMessage() == null ? "Unable to load trending charts." : exception.getMessage());
            clearTrendingCharts();
        }
    }

    private void loadChurchTrend(boolean filterChanged) {
        try {
            Region region = trendingRegionComboBox.getValue();
            setChurchCollectionTrendData(churchCollectionTrendChart, dashboardService.loadChurchCollectionTrend(
                    trendingDateFromPicker.getValue(),
                    trendingDateToPicker.getValue(),
                    region == null ? null : region.getId(),
                    selectedTrendingChurchIds()));
            if (filterChanged) {
                dashboardService.logTrendingFilterChanged(trendingDateFromPicker.getValue(), trendingDateToPicker.getValue(),
                        region == null ? "All Regions" : region.getRegionName());
            }
        } catch (RuntimeException exception) {
            trendingMessageLabel.setText(exception.getMessage() == null ? "Unable to load church trend." : exception.getMessage());
            churchCollectionTrendChart.getData().clear();
        }
    }

    private void applyWeekly(WeeklyDashboardDto weekly) {
        completedRegionsLabel.setText(weekly.getCompletedRegions() + " / " + weekly.getTotalRegions());
        submittedChurchesLabel.setText(weekly.getSubmittedChurches() + " / " + weekly.getTotalChurches());
        pendingChurchesLabel.setText(String.valueOf(weekly.getPendingChurches()));
        lateSubmissionsLabel.setText(String.valueOf(weekly.getLateSubmissions()));
        todaysReceiptsTotalLabel.setText("Rs. " + MONEY_FORMAT.format(weekly.getTodaysReceiptsTotal()));
        smsFailedCountLabel.setText(String.valueOf(weekly.getSmsFailedCount()));
        setLastBackupStatus(weekly.getLastBackupStatus(), weekly.getLastBackupCreatedAt());

        setVisible(weeklyReceiptCards, weekly.isReceiptSummaryVisible());
        setVisible(submittedChurchesBox, weekly.isReceiptSummaryVisible());
        setVisible(pendingChurchesBox, weekly.isReceiptSummaryVisible());
        setVisible(todaysReceiptsSummaryBox, weekly.isTodaysReceiptsTotalVisible());
        setVisible(lateSubmissionsBox, weekly.isLateSubmissionsVisible());
        setVisible(weeklyCollectionAmountsBox, weekly.isReceiptSummaryVisible());
        setVisible(topWeeklyChurchesBox, weekly.isChartsVisible());
        setVisible(topWeeklyRegionsBox, weekly.isChartsVisible());
        setVisible(weeklySmsCard, weekly.isSmsFailedVisible());
        setVisible(weeklyBackupCard, weekly.isBackupStatusVisible());
        setVisible(weeklyChartsSection, weekly.isChartsVisible());

        if (weekly.isReceiptSummaryVisible()) {
            setWeeklyCollectionAmounts(weekly.getCollectionTypeWeekReceiptTotals(),
                    weekly.getCollectionTypeWeeklyTotals(),
                    weekly.isWeekCollectionVisible());
        } else {
            weeklyCollectionAmountsBox.getChildren().clear();
        }
        if (weekly.isChartsVisible()) {
            setTopPerformanceCard(topWeeklyChurchesBox, "Top 3 Churches",
                    weekly.getTopWeeklyChurchCollections().stream().limit(3).toList());
            setTopPerformanceCard(topWeeklyRegionsBox, "Top 3 Regions", weekly.getTopWeeklyRegionCollections());
        } else {
            topWeeklyChurchesBox.getChildren().clear();
            topWeeklyRegionsBox.getChildren().clear();
        }

        if (weekly.isChartsVisible()) {
            setPieData(submittedPendingPieChart, "Submitted vs Pending Churches", weekly.getSubmittedVsPendingChart());
            setPieData(collectionTypeWeeklyPieChart, "Collection Type-wise Weekly Collection",
                    weekly.getCollectionTypeWeeklyTotals());
            setRegionSubmissionProgress(weekly.getRegionSubmissionProgress());
            setBarData(regionWeeklyCollectionChart, "Top 20 Church-wise Weekly Collection",
                    weekly.getTopWeeklyChurchCollections());
        } else {
            clearWeeklyCharts();
        }
    }

    private void applyTrending(TrendingDashboardDto trending) {
        setVisible(trendingChartsSection, trending.isChartsVisible());
        if (!trending.isChartsVisible()) {
            clearTrendingCharts();
            return;
        }
        setLineData(totalCollectionTrendChart, "Total Collection Trend", trending.getTotalCollectionTrend());
        setCollectionTypeTrendData(collectionTypeTrendChart, trending.getCollectionTypeWiseTrend());
        setChurchCollectionTrendData(churchCollectionTrendChart, trending.getChurchWiseCollectionTrend());
        setRegionCollectionTrendData(regionCollectionTrendChart, trending.getRegionWiseCollectionTrend());
    }

    private void applyTrendingRange(DashboardService.DateRange range) {
        trendingDateFromPicker.setValue(range.dateFrom());
        trendingDateToPicker.setValue(range.dateTo());
        loadTrending(true);
    }

    private void setPieData(PieChart chart, String title, List<ChartDataPointDto> points) {
        chart.setTitle(hasPointData(points) ? title : title + " - No data available");
        List<ChartDataPointDto> visiblePoints = (points == null ? List.<ChartDataPointDto>of() : points).stream()
                .filter(point -> point.getValue().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        BigDecimal total = visiblePoints.stream()
                .map(ChartDataPointDto::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        chart.setData(FXCollections.observableArrayList(visiblePoints.stream()
                .map(point -> new PieChart.Data(pieLabel(point, total), point.getValue().doubleValue()))
                .toList()));
        Platform.runLater(() -> {
            for (int index = 0; index < chart.getData().size(); index++) {
                PieChart.Data data = chart.getData().get(index);
                ChartDataPointDto point = visiblePoints.get(index);
                String color = colorForLabel(point.getLabel(), index);
                applyNodeStyle(data.getNode(), "-fx-pie-color: " + color + ";",
                        tooltip(displayLabel(point.getLabel()) + ": " + NUMBER_FORMAT.format(point.getValue())
                                + " (" + percentage(point.getValue(), total) + "%)"));
            }
            stylePieLegendSymbols(chart, visiblePoints);
        });
    }

    private void stylePieLegendSymbols(PieChart chart, List<ChartDataPointDto> points) {
        chart.applyCss();
        for (Node legendItem : chart.lookupAll(".chart-legend-item")) {
            if (!(legendItem instanceof Label label)) {
                continue;
            }
            for (int index = 0; index < points.size(); index++) {
                ChartDataPointDto point = points.get(index);
                if (!label.getText().startsWith(displayLabel(point.getLabel()))) {
                    continue;
                }
                Node symbol = legendItem.lookup(".chart-legend-item-symbol");
                if (symbol != null) {
                    symbol.setStyle("-fx-background-color: " + colorForLabel(point.getLabel(), index) + ";");
                }
                break;
            }
        }
    }

    private void setRegionSubmissionProgress(List<RegionSubmissionProgressDto> progressItems) {
        regionSubmissionProgressRowsBox.getChildren().clear();

        if (progressItems == null || progressItems.isEmpty()) {
            Label empty = new Label("No data available");
            empty.getStyleClass().add("muted-label");
            regionSubmissionProgressRowsBox.getChildren().add(empty);
            return;
        }

        for (RegionSubmissionProgressDto item : progressItems) {
            Label nameLabel = new Label(item.getRegionName());
            nameLabel.getStyleClass().add("dashboard-progress-region");
            String percent = percentage(BigDecimal.valueOf(item.getSubmittedChurches()),
                    BigDecimal.valueOf(item.getTotalChurches()));
            Label countLabel = new Label(item.getSubmittedChurches() + " / " + item.getTotalChurches()
                    + " (" + percent + "%)");
            countLabel.getStyleClass().add("value-label");
            HBox header = new HBox(8, nameLabel, countLabel);
            header.getStyleClass().add("dashboard-progress-row-header");

            ProgressBar progressBar = new ProgressBar(item.getProgress());
            progressBar.setMaxWidth(Double.MAX_VALUE);
            progressBar.getStyleClass().add("dashboard-progress-bar");
            Tooltip.install(progressBar, tooltip(item.getRegionName() + ": " + item.getSubmittedChurches()
                    + " of " + item.getTotalChurches() + " churches submitted (" + percent + "%)"));

            VBox row = new VBox(4, header, progressBar);
            row.getStyleClass().add("dashboard-progress-row");
            regionSubmissionProgressRowsBox.getChildren().add(row);
        }
    }

    private void setBarData(BarChart<String, Number> chart, String title, List<ChartDataPointDto> points) {
        chart.setTitle(hasPointData(points) ? title : title + " - No data available");
        chart.setLegendVisible(false);
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(true);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Weekly Collection");
        List<ChartDataPointDto> visiblePoints = (points == null ? List.<ChartDataPointDto>of() : points).stream()
                .filter(point -> point.getValue().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        regionWeeklyCollectionChart.getData().clear();
        regionWeeklyXAxis.setCategories(FXCollections.observableArrayList(visiblePoints.stream()
                .map(point -> displayLabel(point.getLabel()))
                .toList()));
        for (int index = 0; index < visiblePoints.size(); index++) {
            ChartDataPointDto point = visiblePoints.get(index);
            XYChart.Data<String, Number> data = new XYChart.Data<>(displayLabel(point.getLabel()), point.getValue());
            applyDataNodeStyle(data, "-fx-bar-fill: " + uniqueBarColor(index) + ";",
                    tooltip(displayLabel(point.getLabel()) + ": Rs. " + MONEY_FORMAT.format(point.getValue())));
            series.getData().add(data);
        }
        chart.getData().setAll(series);
    }

    private String uniqueBarColor(int index) {
        return FALLBACK_CHART_COLORS[Math.floorMod(index, FALLBACK_CHART_COLORS.length)];
    }

    private void setRegionWeeklyCollectionData(BarChart<String, Number> chart,
                                               List<RegionCollectionTypeTotalDto> points) {
        boolean hasData = points != null && points.stream()
                .anyMatch(point -> point.getAmount().compareTo(BigDecimal.ZERO) > 0);
        chart.setTitle(hasData ? "Region-wise Weekly Collection" : "Region-wise Weekly Collection - No data available");
        chart.setLegendVisible(true);

        Map<String, XYChart.Series<String, Number>> seriesByType = new LinkedHashMap<>();
        for (String type : List.of("OFFERTORY", "TITHES", "OTHER_DONATIONS")) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(displayLabel(type));
            seriesByType.put(type, series);
        }

        if (points != null) {
            for (RegionCollectionTypeTotalDto point : points) {
                if (point.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                String collectionType = point.getCollectionType();
                XYChart.Series<String, Number> series = seriesByType.computeIfAbsent(collectionType, type -> {
                    XYChart.Series<String, Number> extraSeries = new XYChart.Series<>();
                    extraSeries.setName(displayLabel(type));
                    return extraSeries;
                });
                XYChart.Data<String, Number> data = new XYChart.Data<>(point.getRegionName(), point.getAmount());
                int colorIndex = collectionTypeIndex(collectionType);
                applyDataNodeStyle(data, "-fx-bar-fill: " + colorForLabel(collectionType, colorIndex) + ";",
                        tooltip(point.getRegionName() + " - " + displayLabel(collectionType)
                                + ": Rs. " + MONEY_FORMAT.format(point.getAmount())));
                series.getData().add(data);
            }
        }

        chart.getData().setAll(seriesByType.values().stream()
                .filter(series -> !series.getData().isEmpty())
                .toList());
    }

    private void setWeeklyCollectionAmounts(List<ChartDataPointDto> weekReceiptPoints,
                                            List<ChartDataPointDto> weekCollectionPoints,
                                            boolean weekCollectionVisible) {
        weeklyCollectionAmountsBox.getChildren().clear();
        Label title = new Label("Weekly Collection Amounts");
        title.getStyleClass().add("dashboard-progress-title");
        weeklyCollectionAmountsBox.getChildren().add(title);

        if (!hasPointData(weekReceiptPoints) && (!weekCollectionVisible || !hasPointData(weekCollectionPoints))) {
            Label empty = new Label("No data available");
            empty.getStyleClass().add("muted-label");
            weeklyCollectionAmountsBox.getChildren().add(empty);
            return;
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        grid.getStyleClass().add("dashboard-amount-grid");
        ColumnConstraints typeColumn = new ColumnConstraints();
        typeColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints actualColumn = new ColumnConstraints();
        actualColumn.setMinWidth(135);
        actualColumn.setHalignment(HPos.RIGHT);
        ColumnConstraints owedColumn = new ColumnConstraints();
        owedColumn.setMinWidth(125);
        owedColumn.setHalignment(HPos.RIGHT);
        if (weekCollectionVisible) {
            grid.getColumnConstraints().setAll(typeColumn, actualColumn, owedColumn);
        } else {
            grid.getColumnConstraints().setAll(typeColumn, actualColumn);
        }

        addAmountHeader(grid, weekCollectionVisible);
        Map<String, BigDecimal> weekReceipts = pointMap(weekReceiptPoints);
        Map<String, BigDecimal> weekCollections = pointMap(weekCollectionPoints);
        BigDecimal weekReceiptTotal = BigDecimal.ZERO;
        BigDecimal weekCollectionTotal = BigDecimal.ZERO;
        int rowIndex = 1;
        for (String label : orderedCollectionTypeLabels(weekReceipts, weekCollections)) {
            BigDecimal weekReceiptAmount = weekReceipts.getOrDefault(label, BigDecimal.ZERO);
            BigDecimal weekCollectionAmount = weekCollections.getOrDefault(label, BigDecimal.ZERO);
            if (weekReceiptAmount.compareTo(BigDecimal.ZERO) <= 0
                    && (!weekCollectionVisible || weekCollectionAmount.compareTo(BigDecimal.ZERO) <= 0)) {
                continue;
            }
            weekReceiptTotal = weekReceiptTotal.add(weekReceiptAmount);
            weekCollectionTotal = weekCollectionTotal.add(weekCollectionAmount);
            addAmountRow(grid, rowIndex++, displayLabel(label), money(weekReceiptAmount),
                    money(weekCollectionAmount), false, weekCollectionVisible);
        }
        addAmountRow(grid, rowIndex, "Total", money(weekReceiptTotal), money(weekCollectionTotal),
                true, weekCollectionVisible);
        weeklyCollectionAmountsBox.getChildren().add(grid);
    }

    private void setTopPerformanceCard(VBox card, String titleText, List<ChartDataPointDto> points) {
        card.getChildren().clear();
        Label title = new Label(titleText);
        title.getStyleClass().add("dashboard-progress-title");
        card.getChildren().add(title);

        List<ChartDataPointDto> visiblePoints = points == null
                ? List.of()
                : points.stream()
                .filter(point -> point.getValue().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        if (visiblePoints.isEmpty()) {
            Label empty = new Label("No data available");
            empty.getStyleClass().add("muted-label");
            card.getChildren().add(empty);
            return;
        }

        BigDecimal maxValue = visiblePoints.stream()
                .map(ChartDataPointDto::getValue)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        int rank = 1;
        for (ChartDataPointDto point : visiblePoints) {
            HBox row = new HBox(8);
            row.getStyleClass().add("dashboard-performance-row");
            Label rankBadge = new Label(String.valueOf(rank));
            rankBadge.getStyleClass().addAll("dashboard-rank-badge", "dashboard-rank-" + rank);
            Label name = new Label(displayLabel(point.getLabel()));
            name.getStyleClass().add("dashboard-performance-name");
            Label amount = new Label(money(point.getValue()));
            amount.getStyleClass().add("dashboard-performance-amount");
            HBox.setHgrow(name, Priority.ALWAYS);
            row.getChildren().setAll(rankBadge, name, amount);

            ProgressBar progressBar = new ProgressBar(progressRatio(point.getValue(), maxValue));
            progressBar.setMaxWidth(Double.MAX_VALUE);
            progressBar.getStyleClass().addAll("dashboard-performance-progress",
                    "dashboard-performance-progress-" + Math.min(rank, 3));
            Tooltip.install(progressBar, tooltip(displayLabel(point.getLabel()) + "\nRs. " + money(point.getValue())));

            VBox item = new VBox(4, row, progressBar);
            item.getStyleClass().add("dashboard-performance-item");
            card.getChildren().add(item);
            rank++;
        }
    }

    private double progressRatio(BigDecimal value, BigDecimal maxValue) {
        if (value == null || maxValue == null || maxValue.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, value.doubleValue() / maxValue.doubleValue()));
    }

    private void addAmountHeader(GridPane grid, boolean weekCollectionVisible) {
        Label type = amountHeader("Type");
        Label actual = amountHeader("Week-Receipts");
        grid.add(type, 0, 0);
        grid.add(actual, 1, 0);
        if (weekCollectionVisible) {
            Label owed = amountHeader("Week-Collection");
            grid.add(owed, 2, 0);
        }
    }

    private Label amountHeader(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("dashboard-amount-header");
        return label;
    }

    private void addAmountRow(GridPane grid, int rowIndex, String typeText, String actualText, String owedText,
                              boolean total, boolean weekCollectionVisible) {
        Label type = new Label(typeText);
        type.getStyleClass().add(total ? "dashboard-amount-total-label" : "dashboard-progress-region");
        Label actual = new Label(actualText);
        actual.getStyleClass().add(total ? "dashboard-amount-total-value" : "dashboard-amount-value");
        actual.setMaxWidth(Double.MAX_VALUE);
        actual.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        grid.add(type, 0, rowIndex);
        grid.add(actual, 1, rowIndex);
        if (weekCollectionVisible) {
            Label owed = new Label(owedText);
            owed.getStyleClass().add(total ? "dashboard-amount-total-value" : "dashboard-amount-value");
            owed.setMaxWidth(Double.MAX_VALUE);
            owed.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
            grid.add(owed, 2, rowIndex);
        }
    }

    private String money(BigDecimal amount) {
        return MONEY_FORMAT.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private Map<String, BigDecimal> pointMap(List<ChartDataPointDto> points) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        if (points == null) {
            return values;
        }
        for (ChartDataPointDto point : points) {
            values.put(point.getLabel(), point.getValue() == null ? BigDecimal.ZERO : point.getValue());
        }
        return values;
    }

    private List<String> orderedCollectionTypeLabels(Map<String, BigDecimal> weekReceipts,
                                                     Map<String, BigDecimal> weekCollections) {
        LinkedHashMap<String, BigDecimal> labels = new LinkedHashMap<>();
        labels.put("OFFERTORY", BigDecimal.ZERO);
        labels.put("TITHES", BigDecimal.ZERO);
        labels.put("OTHER_DONATIONS", BigDecimal.ZERO);
        weekReceipts.keySet().forEach(label -> labels.putIfAbsent(label, BigDecimal.ZERO));
        weekCollections.keySet().forEach(label -> labels.putIfAbsent(label, BigDecimal.ZERO));
        return labels.keySet().stream()
                .filter(label -> weekReceipts.getOrDefault(label, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0
                        || weekCollections.getOrDefault(label, BigDecimal.ZERO).compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    private void setLastBackupStatus(String status, LocalDateTime createdAt) {
        String displayStatus = status == null || status.isBlank() ? "No backups" : status;
        lastBackupStatusLabel.setText(displayStatus);
        lastBackupStatusLabel.getStyleClass().removeAll(
                "dashboard-backup-status-success",
                "dashboard-backup-status-failed",
                "dashboard-backup-status-empty");
        lastBackupStatusLabel.getStyleClass().add(backupStatusStyle(displayStatus));

        boolean hasTime = createdAt != null;
        lastBackupTimeLabel.setText(hasTime ? dateTimeFormatter.formatDateTime(createdAt) : "");
        lastBackupTimeLabel.setVisible(hasTime);
        lastBackupTimeLabel.setManaged(hasTime);
    }

    private String backupStatusStyle(String status) {
        return switch (status) {
            case "SUCCESS" -> "dashboard-backup-status-success";
            case "FAILED" -> "dashboard-backup-status-failed";
            default -> "dashboard-backup-status-empty";
        };
    }

    private void setLineData(LineChart<String, Number> chart, String title, List<ChartDataPointDto> points) {
        chart.setCreateSymbols(true);
        chart.setTitle(hasPointData(points) ? title : title + " - No data available");
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(title);
        String color = colorForLabel(title, 0);
        points.stream()
                .filter(point -> point.getValue().compareTo(BigDecimal.ZERO) > 0)
                .forEach(point -> {
                    XYChart.Data<String, Number> data = new XYChart.Data<>(displayLabel(point.getLabel()), point.getValue());
                    applyDataNodeStyle(data, linePointStyle(color),
                            amountTooltip(title, displayLabel(point.getLabel()), point.getValue()));
                    series.getData().add(data);
                });
        chart.getData().setAll(series);
        styleLineSeries(chart, series, color);
    }

    private void setCollectionTypeTrendData(LineChart<String, Number> chart, List<CollectionTrendDto> trends) {
        chart.setCreateSymbols(true);
        boolean hasData = trends.stream().anyMatch(trend ->
                trend.getOffertoryTotal().compareTo(BigDecimal.ZERO) > 0
                        || trend.getTithesTotal().compareTo(BigDecimal.ZERO) > 0
                        || trend.getOtherDonationsTotal().compareTo(BigDecimal.ZERO) > 0);
        chart.setTitle(hasData ? "Collection Type-wise Trend" : "Collection Type-wise Trend - No data available");
        XYChart.Series<String, Number> offertory = new XYChart.Series<>();
        offertory.setName("Offerings");
        XYChart.Series<String, Number> tithes = new XYChart.Series<>();
        tithes.setName("Tithes");
        XYChart.Series<String, Number> other = new XYChart.Series<>();
        other.setName("Other Donations");
        for (CollectionTrendDto trend : trends) {
            offertory.getData().add(trendPoint(trend.getPeriodLabel(), trend.getOffertoryTotal(), "OFFERTORY", 0));
            tithes.getData().add(trendPoint(trend.getPeriodLabel(), trend.getTithesTotal(), "TITHES", 1));
            other.getData().add(trendPoint(trend.getPeriodLabel(), trend.getOtherDonationsTotal(), "OTHER_DONATIONS", 2));
        }
        chart.getData().setAll(offertory, tithes, other);
        styleLineSeries(chart, offertory, colorForLabel("OFFERTORY", 0));
        styleLineSeries(chart, tithes, colorForLabel("TITHES", 1));
        styleLineSeries(chart, other, colorForLabel("OTHER_DONATIONS", 2));
    }

    private void setChurchCollectionTrendData(LineChart<String, Number> chart, List<ChurchCollectionTrendDto> trends) {
        chart.setCreateSymbols(true);
        boolean hasData = trends != null && trends.stream()
                .anyMatch(trend -> trend.getAmount().compareTo(BigDecimal.ZERO) > 0);
        chart.setTitle(hasData ? "Church-wise Collection Trend" : "Church-wise Collection Trend - No data available");
        chart.setLegendVisible(true);

        Map<String, XYChart.Series<String, Number>> seriesByChurch = new LinkedHashMap<>();
        if (trends != null) {
            for (ChurchCollectionTrendDto trend : trends) {
                if (trend.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                XYChart.Series<String, Number> series = seriesByChurch.computeIfAbsent(trend.getChurchName(), name -> {
                    XYChart.Series<String, Number> newSeries = new XYChart.Series<>();
                    newSeries.setName(name);
                    return newSeries;
                });
                int colorIndex = new ArrayList<>(seriesByChurch.keySet()).indexOf(trend.getChurchName());
                String color = FALLBACK_CHART_COLORS[Math.floorMod(colorIndex, FALLBACK_CHART_COLORS.length)];
                XYChart.Data<String, Number> data = new XYChart.Data<>(trend.getPeriodLabel(), trend.getAmount());
                applyDataNodeStyle(data, linePointStyle(color),
                        amountTooltip(trend.getChurchName(), trend.getPeriodLabel(), trend.getAmount()));
                series.getData().add(data);
            }
        }

        chart.getData().setAll(seriesByChurch.values());
        int index = 0;
        for (XYChart.Series<String, Number> series : seriesByChurch.values()) {
            styleLineSeries(chart, series, FALLBACK_CHART_COLORS[Math.floorMod(index, FALLBACK_CHART_COLORS.length)]);
            index++;
        }
    }

    private void setRegionCollectionTrendData(LineChart<String, Number> chart, List<RegionCollectionTrendDto> trends) {
        chart.setCreateSymbols(true);
        boolean hasData = trends != null && trends.stream()
                .anyMatch(trend -> trend.getAmount().compareTo(BigDecimal.ZERO) > 0);
        chart.setTitle(hasData ? "Region-wise Collection Trend" : "Region-wise Collection Trend - No data available");
        chart.setLegendVisible(true);

        Map<String, XYChart.Series<String, Number>> seriesByRegion = new LinkedHashMap<>();
        if (trends != null) {
            for (RegionCollectionTrendDto trend : trends) {
                if (trend.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                XYChart.Series<String, Number> series = seriesByRegion.computeIfAbsent(trend.getRegionName(), name -> {
                    XYChart.Series<String, Number> newSeries = new XYChart.Series<>();
                    newSeries.setName(name);
                    return newSeries;
                });
                int colorIndex = new ArrayList<>(seriesByRegion.keySet()).indexOf(trend.getRegionName());
                String color = FALLBACK_CHART_COLORS[Math.floorMod(colorIndex, FALLBACK_CHART_COLORS.length)];
                XYChart.Data<String, Number> data = new XYChart.Data<>(trend.getPeriodLabel(), trend.getAmount());
                applyDataNodeStyle(data, linePointStyle(color),
                        amountTooltip(trend.getRegionName(), trend.getPeriodLabel(), trend.getAmount()));
                series.getData().add(data);
            }
        }

        chart.getData().setAll(seriesByRegion.values());
        int index = 0;
        for (XYChart.Series<String, Number> series : seriesByRegion.values()) {
            styleLineSeries(chart, series, FALLBACK_CHART_COLORS[Math.floorMod(index, FALLBACK_CHART_COLORS.length)]);
            index++;
        }
    }

    private boolean hasPointData(List<ChartDataPointDto> points) {
        return points != null && points.stream().anyMatch(point -> point.getValue().compareTo(BigDecimal.ZERO) > 0);
    }

    private String displayLabel(String label) {
        if (label == null || label.isBlank()) {
            return "-";
        }
        return switch (label) {
            case "OFFERTORY" -> "Offerings";
            case "TITHES" -> "Tithes";
            case "OTHER_DONATIONS" -> "Other Donations";
            default -> label;
        };
    }

    private String displayGrouping(String groupingMode) {
        return "MONTHLY".equals(groupingMode) ? "Monthly" : "Weekly";
    }

    private List<Long> selectedTrendingChurchIds() {
        return trendingChurchMenuButton.getItems().stream()
                .filter(CheckMenuItem.class::isInstance)
                .map(CheckMenuItem.class::cast)
                .filter(CheckMenuItem::isSelected)
                .map(item -> (Long) item.getUserData())
                .toList();
    }

    private void updateTrendingChurchMenuText() {
        int selectedCount = selectedTrendingChurchIds().size();
        trendingChurchMenuButton.setText(selectedCount == 0
                ? "Top churches"
                : selectedCount + " church" + (selectedCount == 1 ? "" : "es") + " selected");
    }

    private XYChart.Data<String, Number> trendPoint(String periodLabel, BigDecimal value, String collectionType,
                                                    int colorIndex) {
        String color = colorForLabel(collectionType, colorIndex);
        XYChart.Data<String, Number> data = new XYChart.Data<>(periodLabel, value);
        applyDataNodeStyle(data, linePointStyle(color),
                amountTooltip(displayLabel(collectionType), periodLabel, value));
        return data;
    }

    private String linePointStyle(String color) {
        return "-fx-background-color: " + color + ", white;"
                + " -fx-background-radius: 7, 5;"
                + " -fx-background-insets: 0, 2;"
                + " -fx-padding: 5;";
    }

    private Tooltip amountTooltip(String seriesLabel, String periodLabel, BigDecimal value) {
        return tooltip(seriesLabel + "\n" + periodLabel + "\nRs. " + MONEY_FORMAT.format(value));
    }

    private void styleLineSeries(LineChart<String, Number> chart, XYChart.Series<String, Number> series, String color) {
        series.nodeProperty().addListener((observable, oldNode, newNode) -> styleSeriesNode(newNode, color));
        styleSeriesNode(series.getNode(), color);
        Platform.runLater(() -> {
            styleSeriesNode(series.getNode(), color);
            styleLegendSymbol(chart, series, color);
        });
    }

    private void styleSeriesNode(Node node, String color) {
        if (node != null) {
            node.setStyle("-fx-stroke: " + color + ";");
        }
    }

    private void styleLegendSymbol(LineChart<String, Number> chart, XYChart.Series<String, Number> series, String color) {
        chart.applyCss();
        for (Node legendItem : chart.lookupAll(".chart-legend-item")) {
            if (legendItem instanceof Label label && series.getName().equals(label.getText())) {
                Node symbol = legendItem.lookup(".chart-legend-item-symbol");
                if (symbol != null) {
                    symbol.setStyle("-fx-background-color: " + color + ", white;");
                }
            }
        }
    }

    private void applyDataNodeStyle(XYChart.Data<String, Number> data, String style, Tooltip tooltip) {
        data.nodeProperty().addListener((observable, oldNode, newNode) -> applyNodeStyle(newNode, style, tooltip));
        Platform.runLater(() -> applyNodeStyle(data.getNode(), style, tooltip));
    }

    private void applyNodeStyle(Node node, String style, Tooltip tooltip) {
        if (node == null) {
            return;
        }
        node.setStyle(style);
        Tooltip.install(node, tooltip);
    }

    private Tooltip tooltip(String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setWrapText(true);
        return tooltip;
    }

    private String pieLabel(ChartDataPointDto point, BigDecimal total) {
        return displayLabel(point.getLabel()) + " " + NUMBER_FORMAT.format(point.getValue())
                + " (" + percentage(point.getValue(), total) + "%)";
    }

    private String percentage(BigDecimal value, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return "0";
        }
        return PERCENT_FORMAT.format(value.multiply(BigDecimal.valueOf(100)).divide(total, 4, java.math.RoundingMode.HALF_UP));
    }

    private String colorForLabel(String label, int index) {
        if (label == null) {
            return FALLBACK_CHART_COLORS[Math.floorMod(index, FALLBACK_CHART_COLORS.length)];
        }
        return switch (label) {
            case "Submitted" -> "#16a34a";
            case "Pending" -> "#f97316";
            case "OFFERTORY" -> "#f59e0b";
            case "TITHES" -> "#2563eb";
            case "OTHER_DONATIONS" -> "#7c3aed";
            case "SUCCESS" -> "#16a34a";
            case "FAILED" -> "#dc2626";
            case "SKIPPED" -> "#ca8a04";
            case "Total Collection Trend" -> "#0891b2";
            default -> FALLBACK_CHART_COLORS[Math.floorMod(index, FALLBACK_CHART_COLORS.length)];
        };
    }

    private int collectionTypeIndex(String collectionType) {
        return switch (collectionType) {
            case "OFFERTORY" -> 0;
            case "TITHES" -> 1;
            case "OTHER_DONATIONS" -> 2;
            default -> 3;
        };
    }

    private void clearWeeklyCharts() {
        submittedPendingPieChart.getData().clear();
        collectionTypeWeeklyPieChart.getData().clear();
        regionSubmissionProgressRowsBox.getChildren().clear();
        regionWeeklyCollectionChart.getData().clear();
        regionWeeklyXAxis.getCategories().clear();
    }

    private void clearTrendingCharts() {
        totalCollectionTrendChart.getData().clear();
        collectionTypeTrendChart.getData().clear();
        churchCollectionTrendChart.getData().clear();
        regionCollectionTrendChart.getData().clear();
    }

    private void setVisible(VBox node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static class RegionListCell extends ListCell<Region> {
        @Override
        protected void updateItem(Region region, boolean empty) {
            super.updateItem(region, empty);
            setText(empty || region == null ? null : region.getRegionCode() + " - " + region.getRegionName());
        }
    }
}
