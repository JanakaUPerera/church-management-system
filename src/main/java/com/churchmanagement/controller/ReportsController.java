package com.churchmanagement.controller;

import com.churchmanagement.dto.PrintResult;
import com.churchmanagement.dto.report.ReportResult;
import com.churchmanagement.dto.report.ReportSearchCriteria;
import com.churchmanagement.dto.report.ReportSummaryTotals;
import com.churchmanagement.dto.report.ReportTableRow;
import com.churchmanagement.dto.report.ReportType;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Region;
import com.churchmanagement.repository.UserRepository;
import com.churchmanagement.service.ChurchService;
import com.churchmanagement.service.RegionService;
import com.churchmanagement.service.ReportService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.ComboBoxUtil;
import com.churchmanagement.util.DatePickerUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ProcessingDialog;
import com.churchmanagement.util.SystemDateTimeFormatter;
import com.churchmanagement.util.TablePaginationUtil;
import com.churchmanagement.util.WeekUtil;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;

public class ReportsController {
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");
    private static final String ALL = "ALL";
    private static final List<ReportType> WEEKLY_REPORT_TYPES = List.of(
            ReportType.WEEKLY_CHURCH_COLLECTION,
            ReportType.WEEKLY_REGION_SUMMARY,
            ReportType.SUBMISSION_STATUS,
            ReportType.LATE_SUBMISSION);
    private static final List<ReportType> REGION_FILTER_EXCLUDED_REPORT_TYPES = List.of(
            ReportType.SMS_DELIVERY,
            ReportType.USER_ACTIVITY,
            ReportType.BACKUP_RESTORE_HISTORY);
    private static final List<ReportType> CHURCH_FILTER_EXCLUDED_REPORT_TYPES = List.of(
            ReportType.WEEKLY_REGION_SUMMARY,
            ReportType.REGION_ANNUAL_COLLECTION,
            ReportType.REGION_MONTHLY_COLLECTION,
            ReportType.REGION_PROGRESS,
            ReportType.USER_ACTIVITY,
            ReportType.BACKUP_RESTORE_HISTORY);
    private static final List<ReportType> STATUS_FILTER_REPORT_TYPES = List.of(
            ReportType.SUBMISSION_STATUS,
            ReportType.RECEIPT_PRINT_STATUS,
            ReportType.SMS_DELIVERY,
            ReportType.BACKUP_RESTORE_HISTORY);
    private static final List<ReportType> COLLECTION_COLUMN_REPORT_TYPES = List.of(
            ReportType.WEEKLY_CHURCH_COLLECTION,
            ReportType.WEEKLY_REGION_SUMMARY,
            ReportType.SUBMISSION_STATUS,
            ReportType.LATE_SUBMISSION,
            ReportType.CHURCH_ANNUAL_COLLECTION,
            ReportType.REGION_ANNUAL_COLLECTION,
            ReportType.CHURCH_MONTHLY_COLLECTION,
            ReportType.REGION_MONTHLY_COLLECTION,
            ReportType.CHURCH_PROGRESS,
            ReportType.REGION_PROGRESS);
    private static final List<ReportType> TOTALS_ROW_DISABLED_REPORT_TYPES = List.of(
            ReportType.CANCELLED_RECEIPT,
            ReportType.RECEIPT_PRINT_STATUS,
            ReportType.SMS_DELIVERY,
            ReportType.USER_ACTIVITY,
            ReportType.BACKUP_RESTORE_HISTORY);

    private final ReportService reportService = new ReportService();
    private final RegionService regionService = new RegionService();
    private final ChurchService churchService = new ChurchService();
    private final UserRepository userRepository = new UserRepository();
    private final SystemDateTimeFormatter dateTimeFormatter = new SystemDateTimeFormatter();
    private final ObservableList<ReportTableRow> reportRows = FXCollections.observableArrayList();
    private final ObservableList<ReportTableRow> filteredRows = FXCollections.observableArrayList();
    private final ObservableList<Region> regions = FXCollections.observableArrayList();
    private final ObservableList<Church> churches = FXCollections.observableArrayList();
    private final ObservableList<Church> filteredChurches = FXCollections.observableArrayList();
    private final ObservableList<UserRepository.UserSummary> users = FXCollections.observableArrayList();
    private ReportType currentReportType = ReportType.WEEKLY_CHURCH_COLLECTION;

    @FXML private StackPane reportContentHost;
    @FXML private TilePane reportCardPane;
    @FXML private javafx.scene.layout.BorderPane reportWorkspace;
    @FXML private DatePicker dateFromPicker;
    @FXML private DatePicker dateToPicker;
    @FXML private DatePicker weekStartDatePicker;
    @FXML private ComboBox<Region> regionComboBox;
    @FXML private ComboBox<Church> churchComboBox;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private TextField receiptNoField;
    @FXML private ComboBox<UserRepository.UserSummary> userComboBox;
    @FXML private ComboBox<String> quickDateComboBox;
    @FXML private Button exportPdfButton;
    @FXML private Button exportExcelButton;
    @FXML private Button printButton;
    @FXML private Button searchButton;
    @FXML private Button clearButton;
    @FXML private TextField tableSearchField;
    @FXML private TableView<ReportTableRow> reportTable;
    @FXML private Pagination reportPagination;
    @FXML private ComboBox<Integer> itemsPerPageComboBox;
    @FXML private Label paginationSummaryLabel;
    @FXML private Label offertoryTotalLabel;
    @FXML private Label tithesTotalLabel;
    @FXML private Label otherDonationsTotalLabel;
    @FXML private Label grandTotalLabel;
    @FXML private Label messageLabel;
    @FXML private GridPane filterGrid;
    @FXML private Label dateFromLabel;
    @FXML private Label dateToLabel;
    @FXML private Label weekStartLabel;
    @FXML private Label weekEndLabel;
    @FXML private Label weekEndDateLabel;
    @FXML private Label regionLabel;
    @FXML private Label churchLabel;
    @FXML private Label statusLabel;
    @FXML private Label userLabel;
    @FXML private Label collectionColumnsLabel;
    @FXML private HBox totalsRow;
    @FXML private HBox collectionColumnsBox;
    @FXML private CheckBox collectionAllCheckBox;
    @FXML private CheckBox offertoryColumnCheckBox;
    @FXML private CheckBox tithesColumnCheckBox;
    @FXML private CheckBox otherDonationsColumnCheckBox;
    @FXML private CheckBox grandTotalColumnCheckBox;
    private boolean updatingCollectionColumnSelection;

    @FXML
    private void initialize() {
        configureButtons();
        configureFilters();
        configureTable();
        loadLookupData();
        buildReportCards();
        selectReportType(currentReportType, false);
    }

    @FXML
    private void handleSearch() {
        ReportSearchCriteria criteria = criteriaForAction();
        ProcessingDialog.run("Search Report", "Searching report...",
                () -> {
                    reportService.logFilterChanged(criteria);
                    return reportService.loadReport(criteria);
                },
                this::applyReportResult,
                throwable -> showError("Reports", friendly(throwable, "Unable to load reports right now. Please try again later.")));
    }

    @FXML
    private void handleClear() {
        ReportSearchCriteria defaults = reportService.defaultCriteria(selectedReportType());
        dateFromPicker.setValue(defaults.getDateFrom());
        dateToPicker.setValue(defaults.getDateTo());
        weekStartDatePicker.setValue(defaults.getWeekStartDate());
        regionComboBox.getSelectionModel().selectFirst();
        churchComboBox.getSelectionModel().selectFirst();
        statusComboBox.setValue(ALL);
        receiptNoField.clear();
        userComboBox.getSelectionModel().selectFirst();
        setAllCollectionColumnsSelected(true);
        tableSearchField.clear();
        refreshReport(true);
    }

    @FXML
    private void collectionColumnSelectionChanged(ActionEvent event) {
        if (updatingCollectionColumnSelection) {
            return;
        }
        updatingCollectionColumnSelection = true;
        try {
            if (event != null && event.getSource() == collectionAllCheckBox) {
                boolean selected = collectionAllCheckBox.isSelected();
                offertoryColumnCheckBox.setSelected(selected);
                tithesColumnCheckBox.setSelected(selected);
                otherDonationsColumnCheckBox.setSelected(selected);
                grandTotalColumnCheckBox.setSelected(selected || !anyCollectionTypeSelected());
            } else {
                if (!anyCollectionTypeSelected()) {
                    grandTotalColumnCheckBox.setSelected(true);
                }
                collectionAllCheckBox.setSelected(offertoryColumnCheckBox.isSelected()
                        && tithesColumnCheckBox.isSelected()
                        && otherDonationsColumnCheckBox.isSelected()
                        && grandTotalColumnCheckBox.isSelected());
            }
        } finally {
            updatingCollectionColumnSelection = false;
        }
        if (collectionColumnsBox.isVisible()) {
            refreshReport(true);
        }
    }

    @FXML
    private void handleExportPdf() {
        ProcessingDialog.run("Export PDF", "Exporting report...",
                () -> reportService.exportPdf(criteriaForAction()),
                path -> showInfo("Export PDF", "Report exported to:\n" + path),
                throwable -> showError("Export PDF", friendly(throwable, "Export failed.")));
    }

    @FXML
    private void handleExportExcel() {
        ProcessingDialog.run("Export Excel", "Exporting report...",
                () -> reportService.exportExcel(criteriaForAction()),
                path -> showInfo("Export Excel", "Report exported to:\n" + path),
                throwable -> showError("Export Excel", friendly(throwable, "Export failed.")));
    }

    @FXML
    private void handlePrint() {
        ProcessingDialog.run("Print Report", "Generating PDF and sending to printer...",
                () -> reportService.printReport(criteriaForAction()),
                this::showPrintResult,
                throwable -> showError("Print Report", friendly(throwable, "Print failed.")));
    }

    private void configureButtons() {
        ButtonIconUtil.applyIcon(exportPdfButton, "fas-file-pdf");
        ButtonIconUtil.applyIcon(exportExcelButton, "fas-file-excel");
        ButtonIconUtil.applyIcon(printButton, "fas-print");
        ButtonIconUtil.applyIcon(searchButton, "fas-search");
        ButtonIconUtil.applyIcon(clearButton, "fas-eraser");
    }

    private void configureFilters() {
        DatePickerUtil.disableFutureDates(dateFromPicker);
        DatePickerUtil.disableFutureDates(dateToPicker);
        DatePickerUtil.enableMondaysOnlyAndDisableFutureDates(weekStartDatePicker);
        updateStatusOptions(ReportType.WEEKLY_CHURCH_COLLECTION);
        quickDateComboBox.setItems(FXCollections.observableArrayList("This Week", "Previous Week", "This Month", "Quarter", "Year"));
        quickDateComboBox.setValue("This Month");
        regionComboBox.setItems(regions);
        churchComboBox.setItems(filteredChurches);
        userComboBox.setItems(users);
        ComboBoxUtil.makeSearchable(regionComboBox, this::regionText);
        ComboBoxUtil.makeSearchable(churchComboBox, this::churchText);

        quickDateComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyQuickDate(newValue));
        weekStartDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> updateWeekEndDate());
        regionComboBox.valueProperty().addListener((obs, oldValue, newValue) -> updateChurchFilter());
        tableSearchField.textProperty().addListener((obs, oldValue, newValue) -> applyTableSearch());
        updateWeekEndDate();
    }

    private void buildReportCards() {
        reportCardPane.getChildren().clear();
        for (ReportType reportType : ReportType.values()) {
            reportCardPane.getChildren().add(reportCard(reportType));
        }
    }

    private Node reportCard(ReportType reportType) {
        FontIcon icon = new FontIcon(iconFor(reportType));
        icon.getStyleClass().add("report-card-icon");

        Label title = new Label(reportType.getDisplayName());
        title.setWrapText(true);
        title.getStyleClass().add("report-card-title");

        Label action = new Label("Open report");
        action.getStyleClass().add("report-card-action");

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox card = new VBox(10, icon, title, spacer, action);
        card.setAlignment(Pos.TOP_LEFT);
        card.getStyleClass().add("report-card");
        card.setOnMouseClicked(event -> openReportDialog(reportType));
        return card;
    }

    private void openReportDialog(ReportType reportType) {
        selectReportType(reportType, false);
        prepareWorkspaceForDialog();

        Dialog<ButtonType> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle(reportType.getDisplayName());
        dialog.setHeaderText(reportType.getDisplayName());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(reportWorkspace);
        dialog.getDialogPane().setPrefWidth(1180);
        dialog.getDialogPane().setPrefHeight(760);
        dialog.setOnShown(event -> Platform.runLater(this::handleClear));
        dialog.setOnHidden(event -> restoreWorkspaceToPage());
        dialog.showAndWait();
    }

    private void prepareWorkspaceForDialog() {
        if (reportWorkspace.getParent() instanceof javafx.scene.layout.Pane parent) {
            parent.getChildren().remove(reportWorkspace);
        }
        reportWorkspace.setManaged(true);
        reportWorkspace.setVisible(true);
    }

    private void restoreWorkspaceToPage() {
        if (reportWorkspace.getParent() instanceof javafx.scene.layout.Pane parent) {
            parent.getChildren().remove(reportWorkspace);
        }
        if (!reportContentHost.getChildren().contains(reportWorkspace)) {
            reportContentHost.getChildren().add(reportWorkspace);
        }
        reportWorkspace.setManaged(false);
        reportWorkspace.setVisible(false);
    }

    private void selectReportType(ReportType reportType, boolean refresh) {
        currentReportType = reportType == null ? ReportType.WEEKLY_CHURCH_COLLECTION : reportType;
        applyReportTypeVisibility(currentReportType);
        if (refresh) {
            handleClear();
        }
    }

    private void configureTable() {
        reportTable.getStyleClass().add("report-table");
        TablePaginationUtil.configure(reportTable, filteredRows, reportPagination, itemsPerPageComboBox,
                paginationSummaryLabel, "rows");
        reportTable.setRowFactory(table -> {
            TableRow<ReportTableRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showRowDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private void loadLookupData() {
        Region allRegions = new Region();
        allRegions.setRegionName(ALL);
        regions.setAll(allRegions);
        regions.addAll(regionService.findAll());

        Church allChurches = new Church();
        allChurches.setChurchName(ALL);
        churches.setAll(churchService.findAll());
        filteredChurches.setAll(allChurches);
        filteredChurches.addAll(churches);

        users.setAll(new UserRepository.UserSummary(0L, ALL, ALL));
        users.addAll(userRepository.findActiveUserSummaries());
        regionComboBox.getSelectionModel().selectFirst();
        churchComboBox.getSelectionModel().selectFirst();
        userComboBox.getSelectionModel().selectFirst();
    }

    private void refreshReport(boolean logFilterChange) {
        try {
            ReportSearchCriteria criteria = criteriaForAction();
            if (logFilterChange) {
                reportService.logFilterChanged(criteria);
            }
            ReportResult<? extends ReportTableRow> result = reportService.loadReport(criteria);
            applyReportResult(result);
        } catch (ReportService.ReportException exception) {
            showError("Reports", exception.getMessage());
        } catch (RuntimeException exception) {
            showError("Reports", friendly(exception, "Unable to load reports right now. Please try again later."));
        }
    }

    private void applyReportResult(ReportResult<? extends ReportTableRow> result) {
        rebuildColumns(result.getRows());
        reportRows.setAll(result.getRows());
        applyTableSearch();
        updateTotals(result.getTotals());
        messageLabel.setText(result.getRows().isEmpty() ? "No data available." : "");
    }

    private void rebuildColumns(List<? extends ReportTableRow> rows) {
        reportTable.getColumns().clear();
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashMap<String, Object> first = rows.getFirst().columns();
        int index = 0;
        for (String header : first.keySet()) {
            final int columnIndex = index++;
            TableColumn<ReportTableRow, Object> column = new TableColumn<>(header);
            column.setCellValueFactory(cell -> new SimpleObjectProperty<>(
                    displayValue(valueAtColumn(cell.getValue(), columnIndex))));
            if (isRightAlignedColumn(header)) {
                column.setCellFactory(ignored -> rightAlignedCell());
            }
            double preferredWidth = preferredColumnWidth(header, columnIndex, rows);
            column.setMinWidth(Math.min(preferredWidth, 170));
            column.setPrefWidth(preferredWidth);
            reportTable.getColumns().add(column);
        }
    }

    private TableCell<ReportTableRow, Object> rightAlignedCell() {
        return new TableCell<>() {
            {
                setAlignment(Pos.CENTER_RIGHT);
                getStyleClass().add("report-right-cell");
            }

            @Override
            protected void updateItem(Object value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? "" : value.toString());
            }
        };
    }

    private boolean isRightAlignedColumn(String header) {
        if (header == null) {
            return false;
        }
        String normalized = header.toLowerCase();
        return normalized.contains("amount")
                || normalized.contains("total")
                || normalized.contains("collection")
                || normalized.contains("Offerings")
                || normalized.contains("tithes")
                || normalized.contains("donation")
                || normalized.equals("submitted")
                || normalized.equals("missing")
                || normalized.equals("late")
                || normalized.endsWith(" count")
                || normalized.endsWith(" churches")
                || normalized.endsWith(" weeks");
    }

    private double preferredColumnWidth(String header, int columnIndex, List<? extends ReportTableRow> rows) {
        int maxLength = header == null ? 0 : header.length();
        for (ReportTableRow row : rows.stream().limit(30).toList()) {
            Object value = valueAtColumn(row, columnIndex);
            maxLength = Math.max(maxLength, approximateDisplayLength(value));
        }
        double width = maxLength * 9.0 + 36;
        return Math.max(125, Math.min(width, 280));
    }

    private Object valueAtColumn(ReportTableRow row, int columnIndex) {
        if (row == null || row.columns() == null || columnIndex < 0) {
            return null;
        }
        int index = 0;
        for (Object value : row.columns().values()) {
            if (index == columnIndex) {
                return value;
            }
            index++;
        }
        return null;
    }

    private int approximateDisplayLength(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof LocalDateTime) {
            return 18;
        }
        if (value instanceof LocalDate) {
            return 12;
        }
        if (value instanceof LocalTime) {
            return 8;
        }
        return value.toString().length();
    }

    private void applyTableSearch() {
        String term = tableSearchField.getText() == null ? "" : tableSearchField.getText().strip().toLowerCase();
        if (term.isBlank()) {
            filteredRows.setAll(reportRows);
            return;
        }
        filteredRows.setAll(reportRows.stream().filter(row -> row.searchText().contains(term)).toList());
    }

    private ReportSearchCriteria criteriaForAction() {
        ReportSearchCriteria criteria = new ReportSearchCriteria();
        criteria.setReportType(selectedReportType());
        criteria.setDateFrom(dateFromPicker.getValue());
        criteria.setDateTo(dateToPicker.getValue());
        criteria.setWeekStartDate(weekStartDatePicker.getValue());
        criteria.setRegionId(selectedRegionId());
        criteria.setChurchId(selectedChurchId());
        criteria.setStatus(statusComboBox.getValue());
        criteria.setReceiptNo(receiptNoField.getText());
        UserRepository.UserSummary user = userComboBox.getValue();
        criteria.setUserId(user == null || user.userId() <= 0 ? null : user.userId());
        criteria.setLimit(itemsPerPageComboBox.getValue() == null ? 500 : Math.max(500, itemsPerPageComboBox.getValue()));
        criteria.setOffertoryColumnSelected(offertoryColumnCheckBox.isSelected());
        criteria.setTithesColumnSelected(tithesColumnCheckBox.isSelected());
        criteria.setOtherDonationsColumnSelected(otherDonationsColumnCheckBox.isSelected());
        criteria.setGrandTotalColumnSelected(grandTotalColumnCheckBox.isSelected() || !anyCollectionTypeSelected());
        return criteria;
    }

    private ReportType selectedReportType() {
        return currentReportType == null ? ReportType.WEEKLY_CHURCH_COLLECTION : currentReportType;
    }

    private void applyQuickDate(String option) {
        ReportService.DateRange range = reportService.quickRange(option == null ? "This Month" : option);
        dateFromPicker.setValue(range.dateFrom());
        dateToPicker.setValue(range.dateTo());
        if ("This Week".equals(option) || "Previous Week".equals(option)) {
            weekStartDatePicker.setValue(range.dateFrom());
        }
    }

    private void applyReportTypeVisibility(ReportType reportType) {
        boolean weekly = WEEKLY_REPORT_TYPES.contains(reportType);
        boolean region = !REGION_FILTER_EXCLUDED_REPORT_TYPES.contains(reportType);
        boolean church = !CHURCH_FILTER_EXCLUDED_REPORT_TYPES.contains(reportType);
        boolean status = STATUS_FILTER_REPORT_TYPES.contains(reportType);
        boolean collectionColumns = supportsCollectionColumnSelection(reportType);
        updateStatusOptions(reportType);
        setVisible(!weekly, dateFromLabel, dateFromPicker, dateToLabel, dateToPicker);
        setVisible(weekly, weekStartLabel, weekStartDatePicker, weekEndLabel, weekEndDateLabel);
        setVisible(region, regionLabel, regionComboBox);
        setVisible(church, churchLabel, churchComboBox);
        setVisible(status, statusLabel, statusComboBox);
        setVisible(reportType == ReportType.USER_ACTIVITY, userLabel, userComboBox);
        setVisible(collectionColumns, collectionColumnsLabel, collectionColumnsBox);
        setVisible(!TOTALS_ROW_DISABLED_REPORT_TYPES.contains(reportType), totalsRow);
    }

    private void updateStatusOptions(ReportType reportType) {
        String currentValue = statusComboBox.getValue();
        List<String> options = switch (reportType) {
            case SUBMISSION_STATUS -> List.of(ALL, "SUBMITTED", "MISSING", "LATE", "ON_TIME");
            case SMS_DELIVERY -> List.of(ALL, "SUCCESS", "SENT", "FAILED", "DELIVERED", "DELIVERY_UNKNOWN",
                    "DELIVERY_FAILED");
            case BACKUP_RESTORE_HISTORY -> List.of(ALL, "SUCCESS", "FAILED");
            case RECEIPT_PRINT_STATUS -> List.of(ALL, "PRINTED", "UNPRINTED");
            default -> List.of(ALL);
        };
        statusComboBox.setItems(FXCollections.observableArrayList(options));
        statusComboBox.setValue(currentValue != null && options.contains(currentValue) ? currentValue : ALL);
    }

    private boolean supportsCollectionColumnSelection(ReportType reportType) {
        return COLLECTION_COLUMN_REPORT_TYPES.contains(reportType);
    }

    private void setAllCollectionColumnsSelected(boolean selected) {
        updatingCollectionColumnSelection = true;
        try {
            collectionAllCheckBox.setSelected(selected);
            offertoryColumnCheckBox.setSelected(selected);
            tithesColumnCheckBox.setSelected(selected);
            otherDonationsColumnCheckBox.setSelected(selected);
            grandTotalColumnCheckBox.setSelected(selected);
        } finally {
            updatingCollectionColumnSelection = false;
        }
    }

    private boolean anyCollectionTypeSelected() {
        return offertoryColumnCheckBox.isSelected()
                || tithesColumnCheckBox.isSelected()
                || otherDonationsColumnCheckBox.isSelected();
    }

    private void updateChurchFilter() {
        Long regionId = selectedRegionId();
        Church allChurches = new Church();
        allChurches.setChurchName(ALL);
        filteredChurches.setAll(allChurches);
        filteredChurches.addAll(churches.stream()
                .filter(church -> regionId == null || regionId.equals(church.getRegionId()))
                .toList());
        churchComboBox.getSelectionModel().selectFirst();
    }

    private void updateTotals(ReportSummaryTotals totals) {
        offertoryTotalLabel.setText(amount(totals.getOffertoryTotal()));
        tithesTotalLabel.setText(amount(totals.getTithesTotal()));
        otherDonationsTotalLabel.setText(amount(totals.getOtherDonationsTotal()));
        grandTotalLabel.setText(amount(totals.getGrandTotal()));
    }

    private void updateWeekEndDate() {
        weekEndDateLabel.setText(dateTimeFormatter.formatDate(WeekUtil.getSundayForMonday(weekStartDatePicker.getValue())));
    }

    private void showRowDetails(ReportTableRow row) {
        if (row == null) {
            return;
        }
        if (row.detailId() != null && selectedReportType() != ReportType.SMS_DELIVERY
                && selectedReportType() != ReportType.USER_ACTIVITY
                && selectedReportType() != ReportType.BACKUP_RESTORE_HISTORY) {
            new ReceiptHistoryController().showReceiptDetailsDialog(row.detailId(), true, true);
            return;
        }
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.INFORMATION));
        alert.setTitle("Report Details");
        alert.setHeaderText(selectedReportType().getDisplayName());
        alert.setContentText(row.columns().entrySet().stream()
                .map(entry -> entry.getKey() + ": " + displayValue(entry.getValue()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse(""));
        alert.showAndWait();
    }

    private Long selectedRegionId() {
        Region region = regionComboBox.getValue();
        return region == null ? null : region.getId();
    }

    private Long selectedChurchId() {
        Church church = churchComboBox.getValue();
        return church == null ? null : church.getId();
    }

    private String regionText(Region region) {
        if (region == null || region.getId() == null) {
            return ALL;
        }
        return region.getRegionCode() + " - " + region.getRegionName();
    }

    private String churchText(Church church) {
        if (church == null || church.getId() == null) {
            return ALL;
        }
        return church.getChurchCode() + " - " + church.getChurchName();
    }

    private String amount(BigDecimal value) {
        return AMOUNT_FORMAT.format(value == null ? BigDecimal.ZERO : value);
    }

    private String displayValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTimeFormatter.formatDateTime(dateTime);
        }
        if (value instanceof LocalDate date) {
            return dateTimeFormatter.formatDate(date);
        }
        if (value instanceof LocalTime time) {
            return dateTimeFormatter.formatTime(time);
        }
        return value.toString();
    }

    private String iconFor(ReportType reportType) {
        return switch (reportType) {
            case WEEKLY_CHURCH_COLLECTION -> "fas-church";
            case WEEKLY_REGION_SUMMARY -> "fas-map-marked-alt";
            case SUBMISSION_STATUS -> "fas-tasks";
            case LATE_SUBMISSION -> "fas-clock";
            case CHURCH_ANNUAL_COLLECTION, REGION_ANNUAL_COLLECTION -> "fas-calendar-alt";
            case CHURCH_MONTHLY_COLLECTION, REGION_MONTHLY_COLLECTION -> "fas-calendar";
            case CHURCH_PROGRESS, REGION_PROGRESS -> "fas-chart-line";
            case CANCELLED_RECEIPT -> "fas-ban";
            case RECEIPT_PRINT_STATUS -> "fas-print";
            case SMS_DELIVERY -> "fas-sms";
            case USER_ACTIVITY -> "fas-user-clock";
            case BACKUP_RESTORE_HISTORY -> "fas-database";
        };
    }

    private void setVisible(boolean visible, Node... nodes) {
        for (Node node : nodes) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private void showPrintResult(PrintResult result) {
        showInfo("Print Report", result.getMessage());
    }

    private void showInfo(String title, Object message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.INFORMATION));
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "" : message.toString());
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
        messageLabel.setText(message);
    }

    private String friendly(Throwable throwable, String fallback) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return throwable == null ? fallback : fallback + " (" + throwable.getClass().getSimpleName() + ")";
    }
}
