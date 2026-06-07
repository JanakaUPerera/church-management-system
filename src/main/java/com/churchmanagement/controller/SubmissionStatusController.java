package com.churchmanagement.controller;

import com.churchmanagement.dto.SubmissionStatusDto;
import com.churchmanagement.dto.SubmissionSummaryDto;
import com.churchmanagement.dto.SubmissionTotalsDto;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Region;
import com.churchmanagement.service.ChurchService;
import com.churchmanagement.service.RegionService;
import com.churchmanagement.service.SubmissionStatusService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.ComboBoxUtil;
import com.churchmanagement.util.DatePickerUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.SystemDateTimeFormatter;
import com.churchmanagement.util.TablePaginationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class SubmissionStatusController {
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");
    private static final String ALL_OPTION_TEXT = "ALL";

    private final SubmissionStatusService submissionStatusService = new SubmissionStatusService();
    private final SystemDateTimeFormatter dateTimeFormatter = new SystemDateTimeFormatter();
    private final RegionService regionService = new RegionService();
    private final ChurchService churchService = new ChurchService();
    private final ObservableList<SubmissionStatusDto> statusRows = FXCollections.observableArrayList();
    private final ObservableList<SubmissionStatusDto> filteredStatusRows = FXCollections.observableArrayList();
    private final ObservableList<Region> regions = FXCollections.observableArrayList();
    private final ObservableList<Church> churches = FXCollections.observableArrayList();
    private final ObservableList<Church> filteredChurches = FXCollections.observableArrayList();

    @FXML private DatePicker weekStartDatePicker;
    @FXML private Button previousWeekButton;
    @FXML private Button nextWeekButton;
    @FXML private ComboBox<Region> regionComboBox;
    @FXML private ComboBox<Church> churchComboBox;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private TextField searchField;
    @FXML private Button refreshButton;
    @FXML private Label submittedChurchesLabel;
    @FXML private Label pendingChurchesLabel;
    @FXML private Label cancelledReceiptsLabel;
    @FXML private Label totalChurchesLabel;
    @FXML private Label lateSubmissionsLabel;
    @FXML private Label totalCollectionAmountLabel;
    @FXML private Label smsFailedCountLabel;
    @FXML private Label unprintedReceiptsCountLabel;
    @FXML private ProgressBar submissionProgressBar;
    @FXML private Label submissionProgressLabel;
    @FXML private TableView<SubmissionStatusDto> submissionStatusTable;
    @FXML private TableColumn<SubmissionStatusDto, String> churchCodeColumn;
    @FXML private TableColumn<SubmissionStatusDto, String> churchNameColumn;
    @FXML private TableColumn<SubmissionStatusDto, String> regionColumn;
    @FXML private TableColumn<SubmissionStatusDto, String> totalAmountColumn;
    @FXML private TableColumn<SubmissionStatusDto, String> statusColumn;
    @FXML private TableColumn<SubmissionStatusDto, String> receiptNoColumn;
    @FXML private TableColumn<SubmissionStatusDto, String> submittedDateColumn;
    @FXML private TableColumn<SubmissionStatusDto, Void> actionColumn;
    @FXML private Label totalsOffertoryLabel;
    @FXML private Label totalsTithesLabel;
    @FXML private Label totalsOtherDonationsLabel;
    @FXML private Label totalsGrandTotalLabel;
    @FXML private ComboBox<Integer> itemsPerPageComboBox;
    @FXML private Label paginationSummaryLabel;
    @FXML private Pagination submissionPagination;

    @FXML
    private void initialize() {
        configureButtons();
        configureFilters();
        configureTable();
        loadRegions();
        loadChurches();
        weekStartDatePicker.setValue(submissionStatusService.defaultWeekStart());
        refreshDashboard(false);
    }

    @FXML
    private void handlePreviousWeek() {
        LocalDate current = weekStartDatePicker.getValue();
        weekStartDatePicker.setValue((current == null ? submissionStatusService.defaultWeekStart() : current).minusWeeks(1));
    }

    @FXML
    private void handleNextWeek() {
        LocalDate current = weekStartDatePicker.getValue();
        weekStartDatePicker.setValue((current == null ? submissionStatusService.defaultWeekStart() : current).plusWeeks(1));
    }

    @FXML
    private void handleRefresh() {
        refreshDashboard(true);
    }

    private void configureButtons() {
        ButtonIconUtil.applyIcon(previousWeekButton, "fas-chevron-left");
        ButtonIconUtil.applyIcon(nextWeekButton, "fas-chevron-right");
        ButtonIconUtil.applyIcon(refreshButton, "fas-sync-alt");
    }

    private void configureFilters() {
        DatePickerUtil.enableMondaysOnly(weekStartDatePicker);
        regionComboBox.setItems(regions);
        ComboBoxUtil.makeSearchable(regionComboBox, this::regionDisplayText);
        churchComboBox.setItems(filteredChurches);
        ComboBoxUtil.makeSearchable(churchComboBox, this::churchDisplayText);
        statusComboBox.setItems(FXCollections.observableArrayList(
                SubmissionStatusService.STATUS_ALL,
                SubmissionStatusService.STATUS_SUBMITTED,
                SubmissionStatusService.STATUS_PENDING,
                SubmissionStatusService.STATUS_CANCELLED));
        statusComboBox.setValue(SubmissionStatusService.STATUS_ALL);
        weekStartDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.equals(oldValue)) {
                refreshDashboard(true);
            }
        });
        regionComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            updateChurchFilter();
            refreshDashboard(true);
        });
        churchComboBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshDashboard(true));
        statusComboBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshDashboard(true));
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyTableSearch());
    }

    private void configureTable() {
        TablePaginationUtil.configure(submissionStatusTable, filteredStatusRows, submissionPagination,
                itemsPerPageComboBox, paginationSummaryLabel, "churches");
        submissionStatusTable.getStyleClass().add("receipt-history-table");
        churchCodeColumn.setCellValueFactory(cellData -> text(cellData.getValue().getChurchCode()));
        churchNameColumn.setCellValueFactory(cellData -> text(cellData.getValue().getChurchName()));
        regionColumn.setCellValueFactory(cellData -> text(cellData.getValue().getRegionName()));
        totalAmountColumn.setCellValueFactory(cellData -> amountText(cellData.getValue(), cellData.getValue().getTotalAmount()));
        statusColumn.setCellValueFactory(cellData -> text(cellData.getValue().getStatus()));
        receiptNoColumn.setCellValueFactory(cellData -> text(nullToDash(cellData.getValue().getReceiptNo())));
        submittedDateColumn.setCellValueFactory(cellData -> text(formatDateTime(cellData.getValue().getSubmittedDate())));
        actionColumn.setCellFactory(column -> new ViewButtonCell());

        totalAmountColumn.setCellFactory(column -> alignedTextCell("receipt-right-cell"));
        statusColumn.setCellFactory(column -> new StatusBadgeCell());
        actionColumn.setMinWidth(80);
        actionColumn.setPrefWidth(90);
        actionColumn.setResizable(false);
        submissionStatusTable.setRowFactory(table -> {
            TableRow<SubmissionStatusDto> row = new TableRow<>();
            row.itemProperty().addListener((observable, oldValue, newValue) -> {
                row.getStyleClass().remove("late-submission-row");
                if (newValue != null && newValue.isLateSubmission()) {
                    row.getStyleClass().add("late-submission-row");
                }
            });
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showSubmissionDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private void loadRegions() {
        try {
            Region allRegions = new Region();
            allRegions.setRegionName(ALL_OPTION_TEXT);
            regions.setAll(allRegions);
            regions.addAll(regionService.findAll());
            regionComboBox.getSelectionModel().select(allRegions);
        } catch (RuntimeException exception) {
            showError("Submission Status", "Unable to load regions right now. Please try again later.");
        }
    }

    private void loadChurches() {
        try {
            churches.setAll(churchService.findAll());
            updateChurchFilter();
            churchComboBox.getSelectionModel().selectFirst();
        } catch (RuntimeException exception) {
            showError("Submission Status", "Unable to load churches right now. Please try again later.");
        }
    }

    private void updateChurchFilter() {
        Region selectedRegion = regionComboBox.getValue();
        Church selectedChurch = churchComboBox.getValue();
        Church allChurches = new Church();
        allChurches.setChurchName(ALL_OPTION_TEXT);
        filteredChurches.setAll(allChurches);
        filteredChurches.addAll(churches.stream()
                .filter(church -> selectedRegionId(selectedRegion) == null
                        || church.getRegionId().equals(selectedRegionId(selectedRegion)))
                .toList());
        if (selectedChurch == null || selectedChurchId(selectedChurch) == null
                || filteredChurches.stream().noneMatch(church -> selectedChurchId(selectedChurch).equals(church.getId()))) {
            churchComboBox.getSelectionModel().selectFirst();
        }
    }

    private void refreshDashboard(boolean logFilterChange) {
        try {
            LocalDate weekStart = weekStartDatePicker.getValue();
            Region region = regionComboBox.getValue();
            Church church = churchComboBox.getValue();
            String status = statusComboBox.getValue();
            if (logFilterChange) {
                submissionStatusService.logFilterChanged(weekStart, selectedRegionId(region),
                        region == null ? ALL_OPTION_TEXT : region.getRegionName(), status);
            }
            statusRows.setAll(submissionStatusService.loadWeeklyStatus(
                    weekStart, selectedRegionId(region), selectedChurchId(church), status));
            applyTableSearch();
            updateSummary(submissionStatusService.loadWeeklySummary(
                    weekStart, selectedRegionId(region), selectedChurchId(church)));
            updateTotals(submissionStatusService.loadSubmissionTotals(
                    weekStart, selectedRegionId(region), selectedChurchId(church)));
        } catch (SubmissionStatusService.SubmissionStatusException exception) {
            showError("Submission Status", exception.getMessage());
        } catch (RuntimeException exception) {
            showError("Submission Status", "Unable to load submission status right now. Please try again later.");
        }
    }

    private void applyTableSearch() {
        String term = searchField == null || searchField.getText() == null
                ? ""
                : searchField.getText().strip().toLowerCase();
        if (term.isBlank()) {
            filteredStatusRows.setAll(statusRows);
            return;
        }
        filteredStatusRows.setAll(statusRows.stream()
                .filter(row -> contains(row.getChurchCode(), term)
                        || contains(row.getChurchName(), term)
                        || contains(row.getRegionName(), term)
                        || contains(row.getStatus(), term)
                        || contains(row.getReceiptNo(), term)
                        || contains(formatDateTime(row.getSubmittedDate()), term))
                .toList());
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase().contains(term);
    }

    private Long selectedRegionId(Region region) {
        return region == null ? null : region.getId();
    }

    private Long selectedChurchId(Church church) {
        return church == null ? null : church.getId();
    }

    private String regionDisplayText(Region region) {
        if (region == null) {
            return "";
        }
        if (region.getId() == null) {
            return ALL_OPTION_TEXT;
        }
        return region.getRegionCode() + " - " + region.getRegionName();
    }

    private String churchDisplayText(Church church) {
        if (church == null) {
            return "";
        }
        if (church.getId() == null) {
            return ALL_OPTION_TEXT;
        }
        return church.getChurchCode() + " - " + church.getChurchName();
    }

    private void updateSummary(SubmissionSummaryDto summary) {
        submittedChurchesLabel.setText(String.valueOf(summary.getSubmittedChurches()));
        pendingChurchesLabel.setText(String.valueOf(summary.getPendingChurches()));
        cancelledReceiptsLabel.setText(String.valueOf(summary.getCancelledReceipts()));
        totalChurchesLabel.setText(String.valueOf(summary.getTotalChurches()));
        lateSubmissionsLabel.setText(String.valueOf(summary.getLateSubmissions()));
        totalCollectionAmountLabel.setText(formatAmount(summary.getTotalCollectionAmount()));
        smsFailedCountLabel.setText(String.valueOf(summary.getSmsFailedCount()));
        unprintedReceiptsCountLabel.setText(String.valueOf(summary.getUnprintedReceiptsCount()));
        submissionProgressBar.setProgress(summary.getProgress());
        long percent = Math.round(summary.getProgress() * 100);
        submissionProgressLabel.setText(summary.getSubmittedChurches() + " / " + summary.getTotalChurches()
                + "     " + percent + "%");
    }

    private void updateTotals(SubmissionTotalsDto totals) {
        totalsOffertoryLabel.setText(formatAmount(totals.getTotalOffertory()));
        totalsTithesLabel.setText(formatAmount(totals.getTotalTithes()));
        totalsOtherDonationsLabel.setText(formatAmount(totals.getTotalOtherDonations()));
        totalsGrandTotalLabel.setText(formatAmount(totals.getGrandTotal()));
    }

    private SimpleStringProperty text(String value) {
        return new SimpleStringProperty(value == null ? "" : value);
    }

    private SimpleStringProperty amountText(SubmissionStatusDto row, BigDecimal amount) {
        return new SimpleStringProperty(row.isPending() ? "-" : formatAmount(amount));
    }

    private TableCell<SubmissionStatusDto, String> alignedTextCell(String styleClass) {
        return new TableCell<>() {
            {
                getStyleClass().add(styleClass);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        };
    }

    private void showSubmissionDetails(SubmissionStatusDto row) {
        if (row == null || row.getReceiptId() == null) {
            return;
        }

        try {
            new ReceiptHistoryController().showReceiptDetailsDialog(row.getReceiptId(), true, true);
        } catch (RuntimeException exception) {
            showError("Submission Details", exception.getMessage() == null
                    ? "Unable to load submission details."
                    : exception.getMessage());
        }
    }

    private String formatAmount(BigDecimal amount) {
        return AMOUNT_FORMAT.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTimeFormatter.formatDateTime(dateTime);
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void showError(String title, String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private class ViewButtonCell extends TableCell<SubmissionStatusDto, Void> {
        private final Button viewButton = new Button();
        private final HBox actionBox = new HBox(viewButton);

        private ViewButtonCell() {
            getStyleClass().add("receipt-action-cell");
            actionBox.setAlignment(Pos.CENTER);
            viewButton.getStyleClass().add("table-action-button");
            ButtonIconUtil.applyTableActionIcon(viewButton, "fas-eye", "View submission details");
            viewButton.setOnAction(event -> showSubmissionDetails(getTableView().getItems().get(getIndex())));
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getIndex() >= getTableView().getItems().size()) {
                setGraphic(null);
                return;
            }
            SubmissionStatusDto row = getTableView().getItems().get(getIndex());
            viewButton.setDisable(row == null || row.getReceiptId() == null);
            setGraphic(actionBox);
        }
    }

    private static class StatusBadgeCell extends TableCell<SubmissionStatusDto, String> {
        private final Label badge = new Label();
        private final Label lateBadge = new Label("L");
        private final HBox container = new HBox(6, badge, lateBadge);

        private StatusBadgeCell() {
            getStyleClass().add("centered-table-cell");
            setAlignment(Pos.CENTER);
            container.setAlignment(Pos.CENTER);
            badge.getStyleClass().add("status-badge");
            lateBadge.getStyleClass().add("status-correction-note");
            lateBadge.setTooltip(new Tooltip("Late submission"));
        }

        @Override
        protected void updateItem(String status, boolean empty) {
            super.updateItem(status, empty);
            if (empty || status == null) {
                setGraphic(null);
                return;
            }
            badge.setText(status);
            badge.getStyleClass().removeAll("status-active", "status-inactive", "status-warning");
            badge.getStyleClass().add(switch (status) {
                case "SUBMITTED" -> "status-active";
                case "PENDING" -> "status-warning";
                default -> "status-inactive";
            });
            SubmissionStatusDto row = getTableView().getItems().get(getIndex());
            boolean lateSubmission = row != null && row.isLateSubmission();
            lateBadge.setVisible(lateSubmission);
            lateBadge.setManaged(lateSubmission);
            setGraphic(container);
        }
    }

    private static class RegionListCell extends ListCell<Region> {
        @Override
        protected void updateItem(Region region, boolean empty) {
            super.updateItem(region, empty);
            if (empty || region == null) {
                setText(null);
            } else if (region.getId() == null) {
                setText(ALL_OPTION_TEXT);
            } else {
                setText(region.getRegionCode() + " - " + region.getRegionName());
            }
        }
    }

    private static class ChurchListCell extends ListCell<Church> {
        @Override
        protected void updateItem(Church church, boolean empty) {
            super.updateItem(church, empty);
            if (empty || church == null) {
                setText(null);
            } else if (church.getId() == null) {
                setText(ALL_OPTION_TEXT);
            } else {
                setText(church.getChurchCode() + " - " + church.getChurchName());
            }
        }
    }
}
