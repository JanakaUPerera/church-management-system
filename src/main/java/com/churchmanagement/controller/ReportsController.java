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
import com.churchmanagement.util.TablePaginationUtil;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;

public class ReportsController {
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");
    private static final String ALL = "ALL";

    private final ReportService reportService = new ReportService();
    private final RegionService regionService = new RegionService();
    private final ChurchService churchService = new ChurchService();
    private final UserRepository userRepository = new UserRepository();
    private final ObservableList<ReportTableRow> reportRows = FXCollections.observableArrayList();
    private final ObservableList<ReportTableRow> filteredRows = FXCollections.observableArrayList();
    private final ObservableList<Region> regions = FXCollections.observableArrayList();
    private final ObservableList<Church> churches = FXCollections.observableArrayList();
    private final ObservableList<Church> filteredChurches = FXCollections.observableArrayList();
    private final ObservableList<UserRepository.UserSummary> users = FXCollections.observableArrayList();

    @FXML private ListView<ReportType> reportTypeListView;
    @FXML private DatePicker dateFromPicker;
    @FXML private DatePicker dateToPicker;
    @FXML private DatePicker weekStartDatePicker;
    @FXML private ComboBox<Region> regionComboBox;
    @FXML private ComboBox<Church> churchComboBox;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private TextField receiptNoField;
    @FXML private ComboBox<UserRepository.UserSummary> userComboBox;
    @FXML private ComboBox<ReportType> reportTypeComboBox;
    @FXML private ComboBox<String> quickDateComboBox;
    @FXML private Button exportPdfButton;
    @FXML private Button exportExcelButton;
    @FXML private Button printButton;
    @FXML private Button refreshButton;
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
    @FXML private HBox dateRangeBox;
    @FXML private HBox weekStartBox;
    @FXML private HBox regionBox;
    @FXML private HBox churchBox;
    @FXML private HBox statusBox;
    @FXML private HBox receiptNoBox;
    @FXML private HBox userBox;

    @FXML
    private void initialize() {
        configureButtons();
        configureFilters();
        configureTable();
        loadLookupData();
        reportTypeListView.getSelectionModel().select(ReportType.WEEKLY_CHURCH_COLLECTION);
        refreshReport(false);
    }

    @FXML
    private void handleRefresh() {
        refreshReport(true);
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
        tableSearchField.clear();
        refreshReport(true);
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
        ButtonIconUtil.applyIcon(refreshButton, "fas-sync-alt");
        ButtonIconUtil.applyIcon(clearButton, "fas-eraser");
    }

    private void configureFilters() {
        DatePickerUtil.applySystemDateFormat(dateFromPicker);
        DatePickerUtil.applySystemDateFormat(dateToPicker);
        DatePickerUtil.enableMondaysOnly(weekStartDatePicker);
        reportTypeListView.setItems(FXCollections.observableArrayList(ReportType.values()));
        reportTypeComboBox.setItems(FXCollections.observableArrayList(ReportType.values()));
        statusComboBox.setItems(FXCollections.observableArrayList(ALL, "SUBMITTED", "MISSING", "PRINTED", "UNPRINTED",
                "SENT", "FAILED", "DELIVERED", "SUCCESS"));
        statusComboBox.setValue(ALL);
        quickDateComboBox.setItems(FXCollections.observableArrayList("This Week", "Previous Week", "This Month", "Quarter", "Year"));
        quickDateComboBox.setValue("This Month");
        regionComboBox.setItems(regions);
        churchComboBox.setItems(filteredChurches);
        userComboBox.setItems(users);
        ComboBoxUtil.makeSearchable(regionComboBox, this::regionText);
        ComboBoxUtil.makeSearchable(churchComboBox, this::churchText);

        reportTypeListView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                reportTypeComboBox.setValue(newValue);
                applyReportTypeVisibility(newValue);
                handleClear();
            }
        });
        reportTypeComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && newValue != reportTypeListView.getSelectionModel().getSelectedItem()) {
                reportTypeListView.getSelectionModel().select(newValue);
            }
        });
        quickDateComboBox.valueProperty().addListener((obs, oldValue, newValue) -> applyQuickDate(newValue));
        regionComboBox.valueProperty().addListener((obs, oldValue, newValue) -> updateChurchFilter());
        tableSearchField.textProperty().addListener((obs, oldValue, newValue) -> applyTableSearch());
    }

    private void configureTable() {
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
            reportRows.setAll(result.getRows());
            rebuildColumns(result.getRows());
            applyTableSearch();
            updateTotals(result.getTotals());
            messageLabel.setText(result.getRows().isEmpty() ? "No data available." : result.getRows().size() + " row(s) loaded.");
        } catch (ReportService.ReportException exception) {
            showError("Reports", exception.getMessage());
        } catch (RuntimeException exception) {
            showError("Reports", friendly(exception, "Unable to load reports right now. Please try again later."));
        }
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
                    cell.getValue().columns().values().stream().skip(columnIndex).findFirst().orElse("")));
            column.setPrefWidth(Math.max(120, header.length() * 12.0));
            reportTable.getColumns().add(column);
        }
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
        return criteria;
    }

    private ReportType selectedReportType() {
        ReportType selected = reportTypeListView.getSelectionModel().getSelectedItem();
        return selected == null ? ReportType.WEEKLY_CHURCH_COLLECTION : selected;
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
        boolean weekly = reportType == ReportType.WEEKLY_CHURCH_COLLECTION
                || reportType == ReportType.WEEKLY_REGION_SUMMARY
                || reportType == ReportType.SUBMISSION_STATUS
                || reportType == ReportType.LATE_SUBMISSION;
        boolean receipt = reportType == ReportType.CANCELLED_RECEIPT
                || reportType == ReportType.RECEIPT_PRINT_STATUS
                || reportType == ReportType.WEEKLY_CHURCH_COLLECTION;
        setVisible(dateRangeBox, !weekly);
        setVisible(weekStartBox, weekly);
        setVisible(regionBox, reportType != ReportType.SMS_DELIVERY
                && reportType != ReportType.USER_ACTIVITY
                && reportType != ReportType.BACKUP_RESTORE_HISTORY);
        setVisible(churchBox, reportType != ReportType.WEEKLY_REGION_SUMMARY
                && reportType != ReportType.REGION_ANNUAL_COLLECTION
                && reportType != ReportType.REGION_MONTHLY_COLLECTION
                && reportType != ReportType.REGION_PROGRESS
                && reportType != ReportType.USER_ACTIVITY
                && reportType != ReportType.BACKUP_RESTORE_HISTORY);
        setVisible(statusBox, reportType == ReportType.SUBMISSION_STATUS
                || reportType == ReportType.RECEIPT_PRINT_STATUS
                || reportType == ReportType.SMS_DELIVERY
                || reportType == ReportType.BACKUP_RESTORE_HISTORY);
        setVisible(receiptNoBox, receipt || reportType == ReportType.SMS_DELIVERY);
        setVisible(userBox, reportType == ReportType.USER_ACTIVITY);
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
                .map(entry -> entry.getKey() + ": " + entry.getValue())
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

    private void setVisible(HBox box, boolean visible) {
        box.setVisible(visible);
        box.setManaged(visible);
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
        return fallback;
    }
}
