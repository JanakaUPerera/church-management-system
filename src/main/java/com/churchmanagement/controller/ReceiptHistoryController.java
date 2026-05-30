package com.churchmanagement.controller;

import com.churchmanagement.dto.CancelReceiptRequest;
import com.churchmanagement.dto.ReceiptItemDto;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Region;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.ReceiptRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.ChurchService;
import com.churchmanagement.service.ReceiptCancellationService;
import com.churchmanagement.service.RegionService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.ComboBoxUtil;
import com.churchmanagement.util.DatePickerUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.TablePaginationUtil;
import com.churchmanagement.util.WeekUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class ReceiptHistoryController {
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Duration CANCELLATION_WINDOW = Duration.ofDays(7);

    private final ReceiptRepository receiptRepository = new ReceiptRepository();
    private final ReceiptCancellationService receiptCancellationService = new ReceiptCancellationService();
    private final ChurchService churchService = new ChurchService();
    private final RegionService regionService = new RegionService();
    private final ObservableList<ReceiptResponseDto> receipts = FXCollections.observableArrayList();
    private final ObservableList<Church> churches = FXCollections.observableArrayList();
    private final ObservableList<Region> regions = FXCollections.observableArrayList();

    private PermissionGuard permissionGuard;

    @FXML private ComboBox<Church> churchComboBox;
    @FXML private ComboBox<Region> regionComboBox;
    @FXML private DatePicker weekStartDatePicker;
    @FXML private Label weekEndDateLabel;
    @FXML private TextField receiptNoField;
    @FXML private ComboBox<ReceiptStatus> statusComboBox;
    @FXML private TableView<ReceiptResponseDto> receiptTable;
    @FXML private Pagination receiptPagination;
    @FXML private ComboBox<Integer> receiptItemsPerPageComboBox;
    @FXML private Label receiptPaginationSummaryLabel;
    @FXML private TableColumn<ReceiptResponseDto, String> receiptNoColumn;
    @FXML private TableColumn<ReceiptResponseDto, String> churchColumn;
    @FXML private TableColumn<ReceiptResponseDto, String> regionColumn;
    @FXML private TableColumn<ReceiptResponseDto, String> weekColumn;
    @FXML private TableColumn<ReceiptResponseDto, String> statusColumn;
    @FXML private TableColumn<ReceiptResponseDto, String> totalColumn;
    @FXML private TableColumn<ReceiptResponseDto, Void> actionColumn;
    @FXML private Button searchButton;
    @FXML private Button clearButton;
    @FXML private Label messageLabel;

    @FXML
    private void initialize() {
        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty()) {
            setMessage("Please sign in to view receipt history.");
            return;
        }

        permissionGuard = new PermissionGuard(user.get());
        if (!permissionGuard.can("receipt.view")) {
            setMessage("You do not have permission to view receipt history.");
            return;
        }

        configureButtonIcons();
        configureFilters();
        configureTable();
        loadFilters();
        handleSearch();
    }

    @FXML
    private void handleSearch() {
        try {
            Church church = churchComboBox.getValue();
            Region region = regionComboBox.getValue();
            receipts.setAll(receiptRepository.searchReceipts(
                    church == null ? null : church.getId(),
                    region == null ? null : region.getId(),
                    weekStartDatePicker.getValue(),
                    receiptNoField.getText(),
                    statusComboBox.getValue()));
            setMessage(receipts.size() + " receipt(s) found.");
        } catch (DatabaseException exception) {
            showError("Receipt History", "Unable to search receipts right now. Please try again later.");
        }
    }

    @FXML
    private void handleClear() {
        churchComboBox.getSelectionModel().clearSelection();
        regionComboBox.getSelectionModel().clearSelection();
        weekStartDatePicker.setValue(null);
        receiptNoField.clear();
        statusComboBox.getSelectionModel().clearSelection();
        handleSearch();
    }

    private void configureFilters() {
        ComboBoxUtil.makeChurchSearchable(churchComboBox, churches);
        regionComboBox.setItems(regions);
        regionComboBox.setCellFactory(listView -> new RegionListCell());
        regionComboBox.setButtonCell(new RegionListCell());
        statusComboBox.setItems(FXCollections.observableArrayList(ReceiptStatus.ACTIVE, ReceiptStatus.CANCELLED));
        DatePickerUtil.enableMondaysOnly(weekStartDatePicker);
        weekStartDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> updateWeekEndDate());
        updateWeekEndDate();
    }

    private void configureTable() {
        receiptTable.getStyleClass().add("receipt-history-table");
        TablePaginationUtil.configure(receiptTable, receipts, receiptPagination, receiptItemsPerPageComboBox,
                receiptPaginationSummaryLabel, "receipts");
        receiptNoColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getReceiptNo()));
        churchColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getChurchCode() + " - " + cellData.getValue().getChurchName()));
        regionColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRegionCode()));
        weekColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatWeek(cellData.getValue())));
        statusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus().name()));
        totalColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatAmount(cellData.getValue().getTotalAmount())));
        actionColumn.setMinWidth(90);
        actionColumn.setPrefWidth(100);
        actionColumn.setMaxWidth(110);
        actionColumn.setResizable(false);
        actionColumn.setCellFactory(column -> new ViewButtonCell());
        regionColumn.setCellFactory(column -> alignedTextCell("receipt-center-cell"));
        weekColumn.setMinWidth(230);
        weekColumn.setPrefWidth(245);
        weekColumn.setCellFactory(column -> new WeekCell());
        statusColumn.setCellFactory(column -> new StatusBadgeCell());
        totalColumn.setCellFactory(column -> alignedTextCell("receipt-right-cell"));
        receiptTable.setRowFactory(tableView -> {
            TableRow<ReceiptResponseDto> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showReceiptDetailsDialog(row.getItem());
                }
            });
            return row;
        });
    }

    private TableCell<ReceiptResponseDto, String> alignedTextCell(String styleClass) {
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

    private void configureButtonIcons() {
        ButtonIconUtil.applyIcon(searchButton, "fas-search");
        ButtonIconUtil.applyIcon(clearButton, "fas-eraser");
    }

    private String formatWeek(ReceiptResponseDto receipt) {
        return receipt.getWeekStartDate() + " to " + receipt.getWeekEndDate();
    }

    private void loadFilters() {
        try {
            churches.setAll(churchService.findAll());
            regions.setAll(regionService.findAll());
        } catch (RuntimeException exception) {
            showError("Receipt History", "Unable to load filters right now. Please try again later.");
        }
    }

    private void showReceiptDetailsDialog(ReceiptResponseDto receipt) {
        if (receipt == null) {
            return;
        }

        try {
            ReceiptResponseDto details = receiptRepository.findReceiptDetailsById(receipt.getId()).orElse(receipt);
            Dialog<ButtonType> dialog = DialogStyler.apply(new Dialog<>());
            dialog.setTitle("Receipt Details");
            dialog.setHeaderText("Receipt " + details.getReceiptNo());
            ButtonType closeButton = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
            ButtonType cancelReceiptButton = new ButtonType("Cancel Receipt", ButtonBar.ButtonData.LEFT);
            ButtonType recreateButton = new ButtonType("Re-create Receipt", ButtonBar.ButtonData.LEFT);
            if (canCancelReceipt(details)) {
                dialog.getDialogPane().getButtonTypes().add(cancelReceiptButton);
            }
            if (canRecreateReceipt(details)) {
                dialog.getDialogPane().getButtonTypes().add(recreateButton);
            }
            dialog.getDialogPane().getButtonTypes().add(closeButton);
            dialog.getDialogPane().setContent(detailsContent(details));
            dialog.getDialogPane().setPrefWidth(980);

            Optional<ButtonType> result = dialog.showAndWait();
            if (result.filter(cancelReceiptButton::equals).isPresent()) {
                cancelReceipt(details);
            } else if (result.filter(recreateButton::equals).isPresent()) {
                recreateReceipt(details);
            }
        } catch (DatabaseException exception) {
            showError("Receipt Details", "Unable to load receipt details right now. Please try again later.");
        }
    }

    private VBox detailsContent(ReceiptResponseDto receipt) {
        VBox container = new VBox(14);
        container.setPrefWidth(920);
        HBox detailsRow = new HBox(14,
                detailCard("Receipt Information", receiptInformationGrid(receipt)),
                detailCard("Submission Details", submissionDetailsGrid(receipt)));
        detailsRow.setFillHeight(true);
        container.getChildren().addAll(
                detailsRow,
                detailCard("Cancellation Details", cancellationDetailsGrid(receipt)),
                detailCard("Items", itemsGrid(receipt)));
        return container;
    }

    private VBox detailCard(String title, Node content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("receipt-detail-card-title");
        HBox header = new HBox(titleLabel);
        header.getStyleClass().add("receipt-detail-card-header");
        VBox body = new VBox(content);
        body.getStyleClass().add("receipt-detail-card-body");
        VBox card = new VBox(header, body);
        card.getStyleClass().add("receipt-detail-card");
        card.setFillWidth(true);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private GridPane receiptInformationGrid(ReceiptResponseDto receipt) {
        GridPane grid = detailGrid();
        addDetailRow(grid, 0, "Receipt No", receipt.getReceiptNo());
        addDetailRow(grid, 1, "Status", statusBadge(receipt.getStatus()));
        addDetailRow(grid, 2, "Church", receipt.getChurchCode() + " - " + receipt.getChurchName());
        addDetailRow(grid, 3, "Region", receipt.getRegionCode() + " - " + receipt.getRegionName());
        addDetailRow(grid, 4, "Total", formatAmount(receipt.getTotalAmount()));
        return grid;
    }

    private GridPane submissionDetailsGrid(ReceiptResponseDto receipt) {
        GridPane grid = detailGrid();
        addDetailRow(grid, 0, "Week", receipt.getWeekStartDate() + " to " + receipt.getWeekEndDate());
        addDetailRow(grid, 1, "Receipt Date", formatDateTime(receipt.getReceiptDateTime()));
        addDetailRow(grid, 2, "Late Submission", lateSubmissionBadge(receipt.isLateSubmission()));
        addDetailRow(grid, 3, "Submitted By", nullToDash(receipt.getSubmittedByName()));
        addDetailRow(grid, 4, "Issued By", nullToDash(receipt.getIssuedByFullName()));
        addDetailRow(grid, 5, "Correction", correctionNode(receipt));
        return grid;
    }

    private GridPane cancellationDetailsGrid(ReceiptResponseDto receipt) {
        GridPane grid = detailGrid();
        addDetailRow(grid, 0, "Cancellation", formatCancellation(receipt));
        addDetailRow(grid, 1, "Cancel Window", cancellationWindowText(receipt));
        return grid;
    }

    private GridPane detailGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(120);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setMinWidth(190);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);
        return grid;
    }

    private void addDetailRow(GridPane grid, int row, String labelText, String valueText) {
        Label label = new Label(labelText);
        label.getStyleClass().add("receipt-detail-label");
        grid.add(label, 0, row);

        Label valueLabel = detailValueLabel(valueText);
        grid.add(valueLabel, 1, row);
    }

    private void addDetailRow(GridPane grid, int row, String labelText, Node valueNode) {
        Label label = new Label(labelText);
        label.getStyleClass().add("receipt-detail-label");
        grid.add(label, 0, row);
        GridPane.setHgrow(valueNode, Priority.ALWAYS);
        grid.add(valueNode, 1, row);
    }

    private Label statusBadge(ReceiptStatus status) {
        Label badge = detailBadge(status.name(), "status-badge");
        badge.getStyleClass().add(status == ReceiptStatus.ACTIVE ? "status-active" : "status-inactive");
        return badge;
    }

    private Label lateSubmissionBadge(boolean lateSubmission) {
        Label badge = detailBadge(lateSubmission ? "YES" : "NO", "status-badge");
        badge.getStyleClass().add(lateSubmission ? "status-inactive" : "status-active");
        return badge;
    }

    private Node correctionNode(ReceiptResponseDto receipt) {
        String correction = formatCorrection(receipt);
        if ("-".equals(correction)) {
            return detailValueLabel(correction);
        }
        return detailBadge(correction, "status-correction-note");
    }

    private Label detailBadge(String text, String styleClass) {
        Label badge = new Label(text);
        badge.getStyleClass().add(styleClass);
        badge.setTooltip(new Tooltip(text));
        return badge;
    }

    private Label detailValueLabel(String text) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setWrapText(true);
        label.getStyleClass().add("value-label");
        if (text != null && !text.isBlank() && !"-".equals(text)) {
            label.setTooltip(new Tooltip(text));
        }
        GridPane.setHgrow(label, Priority.ALWAYS);
        return label;
    }

    private GridPane itemsGrid(ReceiptResponseDto receipt) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);

        ColumnConstraints itemColumn = new ColumnConstraints();
        itemColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints amountColumn = new ColumnConstraints();
        amountColumn.setMinWidth(120);
        ColumnConstraints noteColumn = new ColumnConstraints();
        noteColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(itemColumn, amountColumn, noteColumn);

        Label itemHeader = headerLabel("Item");
        Label amountHeader = headerLabel("Amount");
        amountHeader.setAlignment(Pos.CENTER_RIGHT);
        Label noteHeader = headerLabel("Note");
        grid.add(itemHeader, 0, 0);
        grid.add(amountHeader, 1, 0);
        grid.add(noteHeader, 2, 0);

        int row = 1;
        for (ReceiptItemDto item : receipt.getItems()) {
            Label itemNameLabel = new Label(item.getCollectionType().getDisplayLabel());
            itemNameLabel.getStyleClass().add("value-label");
            grid.add(itemNameLabel, 0, row);
            Label amountLabel = new Label(formatAmount(item.getAmount()));
            amountLabel.setMaxWidth(Double.MAX_VALUE);
            amountLabel.setAlignment(Pos.CENTER_RIGHT);
            amountLabel.getStyleClass().add("value-label");
            grid.add(amountLabel, 1, row);
            Label noteLabel = new Label(nullToDash(item.getNote()));
            noteLabel.setMaxWidth(Double.MAX_VALUE);
            noteLabel.setWrapText(true);
            noteLabel.getStyleClass().add("value-label");
            if (item.getNote() != null && !item.getNote().isBlank()) {
                noteLabel.setTooltip(new Tooltip(item.getNote()));
            }
            grid.add(noteLabel, 2, row);
            row++;
        }

        Label totalLabel = headerLabel("Total");
        Label totalAmountLabel = headerLabel(formatAmount(receipt.getTotalAmount()));
        totalAmountLabel.setAlignment(Pos.CENTER_RIGHT);
        grid.add(totalLabel, 0, row);
        grid.add(totalAmountLabel, 1, row);
        return grid;
    }

    private Label headerLabel(String text) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private void cancelReceipt(ReceiptResponseDto receipt) {
        Optional<String> reason = promptCancellationReason(receipt);
        if (reason.isEmpty()) {
            return;
        }

        CancelReceiptRequest request = new CancelReceiptRequest();
        request.setReceiptId(receipt.getId());
        request.setCancelReason(reason.get());

        try {
            ReceiptResponseDto cancelled = receiptCancellationService.cancelReceipt(request);
            handleSearch();
            receiptTable.getSelectionModel().select(receipts.stream()
                    .filter(item -> item.getId().equals(cancelled.getId()))
                    .findFirst()
                    .orElse(null));
            setMessage("Receipt " + cancelled.getReceiptNo() + " cancelled.");
        } catch (ReceiptCancellationService.ReceiptCancellationException | SecurityException exception) {
            showError("Cancel Receipt", exception.getMessage());
        }
    }

    private void recreateReceipt(ReceiptResponseDto receipt) {
        DashboardController.openReceiptCorrection(receipt.getId());
    }

    private boolean canRecreateReceipt(ReceiptResponseDto receipt) {
        return receipt != null
                && receipt.getStatus() == ReceiptStatus.CANCELLED
                && receipt.getCorrectionReceiptNo() == null;
    }

    private Optional<String> promptCancellationReason(ReceiptResponseDto selected) {
        Dialog<String> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle("Cancel Receipt");
        dialog.setHeaderText("Cancel receipt " + selected.getReceiptNo());
        ButtonType cancelButton = new ButtonType("Cancel Receipt", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelButton, ButtonType.CANCEL);

        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Cancellation reason");
        reasonArea.setWrapText(true);
        reasonArea.setPrefRowCount(4);
        reasonArea.setPrefWidth(420);
        VBox content = new VBox(8);
        content.getChildren().addAll(new Label("Cancellation Reason"), reasonArea);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(buttonType -> buttonType == cancelButton ? reasonArea.getText() : null);

        Optional<String> reason = dialog.showAndWait().map(String::strip);
        if (reason.isPresent() && reason.get().isBlank()) {
            showError("Cancel Receipt", "Cancellation reason is required.");
            return Optional.empty();
        }
        return reason;
    }

    private boolean canCancelReceipt(ReceiptResponseDto receipt) {
        return permissionGuard != null
                && permissionGuard.can("receipt.cancel")
                && receipt != null
                && receipt.getStatus() == ReceiptStatus.ACTIVE
                && isCancellationWindowOpen(receipt);
    }

    private boolean isCancellationWindowOpen(ReceiptResponseDto receipt) {
        LocalDateTime receiptTime = receipt.getReceiptDateTime();
        return receiptTime != null && !receiptTime.plus(CANCELLATION_WINDOW).isBefore(LocalDateTime.now());
    }

    private String cancellationWindowText(ReceiptResponseDto receipt) {
        if (receipt.getStatus() == ReceiptStatus.CANCELLED) {
            return "Closed";
        }
        if (isCancellationWindowOpen(receipt)) {
            return "Open until " + receipt.getReceiptDateTime().plus(CANCELLATION_WINDOW).format(DATE_TIME_FORMAT);
        }
        return "Expired";
    }

    private String formatCorrection(ReceiptResponseDto receipt) {
        if (receipt.getCorrectedFromReceiptNo() != null) {
            return "Corrected from " + receipt.getCorrectedFromReceiptNo();
        }
        if (receipt.getCorrectionReceiptNo() != null) {
            return "Corrected by " + receipt.getCorrectionReceiptNo();
        }
        return "-";
    }

    private String formatCancellation(ReceiptResponseDto receipt) {
        if (receipt.getCancelReason() == null || receipt.getCancelReason().isBlank()) {
            return "-";
        }
        String cancelledAt = receipt.getCancelledAt() == null ? "" : " on " + receipt.getCancelledAt().format(DATE_TIME_FORMAT);
        return receipt.getCancelReason() + " - by " + nullToDash(receipt.getCancelledByFullName()) + cancelledAt;
    }

    private String formatAmount(BigDecimal amount) {
        return AMOUNT_FORMAT.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.format(DATE_TIME_FORMAT);
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void setMessage(String message) {
        if (messageLabel != null) {
            messageLabel.setText(message);
        }
    }

    private void updateWeekEndDate() {
        LocalDate weekEnd = WeekUtil.getSundayForMonday(weekStartDatePicker.getValue());
        weekEndDateLabel.setText(weekEnd == null ? "-" : weekEnd.toString());
    }

    private void showError(String title, String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
        setMessage(message);
    }

    private class ViewButtonCell extends TableCell<ReceiptResponseDto, Void> {
        private final Button viewButton = new Button();
        private final Button recreateButton = new Button();
        private final HBox actionBox = new HBox(6, viewButton, recreateButton);

        private ViewButtonCell() {
            getStyleClass().add("receipt-action-cell");
            actionBox.setAlignment(Pos.CENTER);
            viewButton.getStyleClass().add("table-action-button");
            recreateButton.getStyleClass().add("table-action-button");
            ButtonIconUtil.applyTableActionIcon(viewButton, "fas-eye", "View receipt");
            ButtonIconUtil.applyTableActionIcon(recreateButton, "fas-redo", "Re-create receipt");
            viewButton.setOnAction(event -> showReceiptDetailsDialog(getTableView().getItems().get(getIndex())));
            recreateButton.setOnAction(event -> recreateReceipt(getTableView().getItems().get(getIndex())));
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getIndex() >= getTableView().getItems().size()) {
                setGraphic(null);
                return;
            }

            ReceiptResponseDto receipt = getTableView().getItems().get(getIndex());
            boolean canRecreate = canRecreateReceipt(receipt);
            recreateButton.setVisible(canRecreate);
            recreateButton.setManaged(canRecreate);
            setGraphic(actionBox);
        }
    }

    private static class StatusBadgeCell extends TableCell<ReceiptResponseDto, String> {
        private final Label badge = new Label();
        private final Label correctionLabel = new Label("RC");
        private final HBox container = new HBox(6, badge, correctionLabel);

        private StatusBadgeCell() {
            getStyleClass().add("centered-table-cell");
            setAlignment(Pos.CENTER);
            container.setAlignment(Pos.CENTER);
            badge.getStyleClass().add("status-badge");
            correctionLabel.getStyleClass().add("status-correction-note");
        }

        @Override
        protected void updateItem(String status, boolean empty) {
            super.updateItem(status, empty);
            if (empty || status == null) {
                setGraphic(null);
                return;
            }

            badge.setText(status);
            badge.getStyleClass().removeAll("status-active", "status-inactive");
            badge.getStyleClass().add("ACTIVE".equals(status) ? "status-active" : "status-inactive");
            ReceiptResponseDto receipt = getTableView().getItems().get(getIndex());
            boolean correctedReceipt = receipt != null && receipt.getCorrectedFromReceiptId() != null;
            correctionLabel.setVisible(correctedReceipt);
            correctionLabel.setManaged(correctedReceipt);
            setGraphic(container);
        }
    }

    private class WeekCell extends TableCell<ReceiptResponseDto, String> {
        private final Label weekLabel = new Label();
        private final Label lateLabel = new Label("L");
        private final HBox container = new HBox(6, weekLabel, lateLabel);

        private WeekCell() {
            getStyleClass().add("receipt-center-cell");
            setAlignment(Pos.CENTER);
            container.setAlignment(Pos.CENTER);
            weekLabel.getStyleClass().add("value-label");
            lateLabel.getStyleClass().add("status-correction-note");
        }

        @Override
        protected void updateItem(String week, boolean empty) {
            super.updateItem(week, empty);
            if (empty || week == null || getIndex() >= getTableView().getItems().size()) {
                setGraphic(null);
                return;
            }

            ReceiptResponseDto receipt = getTableView().getItems().get(getIndex());
            boolean lateSubmission = receipt != null && receipt.isLateSubmission();
            weekLabel.setText(week);
            lateLabel.setVisible(lateSubmission);
            lateLabel.setManaged(lateSubmission);
            setGraphic(container);
        }
    }

    private static class RegionListCell extends ListCell<Region> {
        @Override
        protected void updateItem(Region region, boolean empty) {
            super.updateItem(region, empty);
            setText(empty || region == null ? null : region.getRegionCode() + " - " + region.getRegionName());
        }
    }
}
