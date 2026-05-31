package com.churchmanagement.controller;

import com.churchmanagement.dto.SmsLogDto;
import com.churchmanagement.dto.SmsLogSearchCriteria;
import com.churchmanagement.dto.SmsResendRequest;
import com.churchmanagement.entity.Church;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.ChurchService;
import com.churchmanagement.service.SmsLogService;
import com.churchmanagement.service.SmsResendService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.ComboBoxUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.TablePaginationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
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
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SmsLogController {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String STATUS_ALL = "ALL";

    private final SmsLogService smsLogService = new SmsLogService();
    private final SmsResendService smsResendService = new SmsResendService();
    private final ChurchService churchService = new ChurchService();
    private final ObservableList<SmsLogDto> smsLogs = FXCollections.observableArrayList();
    private final ObservableList<Church> churches = FXCollections.observableArrayList();
    private boolean canResendSms;

    @FXML private DatePicker dateFromPicker;
    @FXML private DatePicker dateToPicker;
    @FXML private ComboBox<Church> churchComboBox;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private TextField mobileNumberField;
    @FXML private TextField receiptNoField;
    @FXML private Button searchButton;
    @FXML private Button clearButton;
    @FXML private Button refreshButton;
    @FXML private TableView<SmsLogDto> smsLogTable;
    @FXML private TableColumn<SmsLogDto, String> dateTimeColumn;
    @FXML private TableColumn<SmsLogDto, String> receiptNoColumn;
    @FXML private TableColumn<SmsLogDto, String> churchCodeColumn;
    @FXML private TableColumn<SmsLogDto, String> churchNameColumn;
    @FXML private TableColumn<SmsLogDto, String> mobileNumberColumn;
    @FXML private TableColumn<SmsLogDto, String> providerColumn;
    @FXML private TableColumn<SmsLogDto, String> statusColumn;
    @FXML private TableColumn<SmsLogDto, Void> actionColumn;
    @FXML private Pagination smsLogPagination;
    @FXML private ComboBox<Integer> smsLogItemsPerPageComboBox;
    @FXML private Label smsLogPaginationSummaryLabel;
    @FXML private Label messageLabel;

    @FXML
    private void initialize() {
        configureButtonIcons();
        configureFilters();
        configureTable();
        loadChurches();
        loadLatestLogs();
    }

    @FXML
    private void handleSearch() {
        try {
            smsLogs.setAll(smsLogService.searchSmsLogs(buildCriteria(500)));
            setMessage(smsLogs.size() + " SMS log(s) found.");
        } catch (SmsLogService.SmsLogException exception) {
            showError("SMS Logs", exception.getMessage());
        } catch (DatabaseException exception) {
            showError("SMS Logs", "Unable to load SMS logs.");
        }
    }

    @FXML
    private void handleClear() {
        dateFromPicker.setValue(null);
        dateToPicker.setValue(null);
        churchComboBox.getSelectionModel().clearSelection();
        statusComboBox.setValue(STATUS_ALL);
        mobileNumberField.clear();
        receiptNoField.clear();
        loadLatestLogs();
    }

    @FXML
    private void handleRefresh() {
        handleClear();
    }

    private void configureButtonIcons() {
        canResendSms = AuthContext.getCurrentUser()
                .map(user -> new PermissionGuard(user).can("sms.resend"))
                .orElse(false);
        ButtonIconUtil.applyIcon(searchButton, "fas-search");
        ButtonIconUtil.applyIcon(clearButton, "fas-eraser");
        ButtonIconUtil.applyIcon(refreshButton, "fas-sync-alt");
    }

    private void configureFilters() {
        ComboBoxUtil.makeChurchSearchable(churchComboBox, churches);
        statusComboBox.setItems(FXCollections.observableArrayList(STATUS_ALL, "SUCCESS", "FAILED", "SKIPPED"));
        statusComboBox.setValue(STATUS_ALL);
    }

    private void configureTable() {
        TablePaginationUtil.configure(smsLogTable, smsLogs, smsLogPagination, smsLogItemsPerPageComboBox,
                smsLogPaginationSummaryLabel, "SMS logs");
        dateTimeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                formatDateTime(cellData.getValue().getCreatedAt())));
        receiptNoColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getReceiptNo())));
        churchCodeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getChurchCode())));
        churchNameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getChurchName())));
        mobileNumberColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getMobileNumber())));
        providerColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getProvider())));
        statusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getStatus())));

        statusColumn.setCellFactory(column -> new SmsStatusCell());
        actionColumn.setCellFactory(column -> new SmsActionCell());
        smsLogTable.setRowFactory(tableView -> {
            TableRow<SmsLogDto> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showSmsLogDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private TableCell<SmsLogDto, String> tooltipTextCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item);
                setTooltip("-".equals(item) ? null : new Tooltip(item));
            }
        };
    }

    private void loadChurches() {
        try {
            churches.setAll(churchService.findAll().stream()
                    .filter(church -> church.getStatus() == Church.Status.ACTIVE)
                    .toList());
        } catch (RuntimeException exception) {
            showError("SMS Logs", "Unable to load churches right now. Please try again later.");
        }
    }

    private void loadLatestLogs() {
        try {
            smsLogs.setAll(smsLogService.latestLogs(100));
            setMessage("Latest " + smsLogs.size() + " SMS log(s) loaded.");
        } catch (SmsLogService.SmsLogException exception) {
            showError("SMS Logs", exception.getMessage());
        } catch (DatabaseException exception) {
            showError("SMS Logs", "Unable to load SMS logs.");
        }
    }

    private SmsLogSearchCriteria buildCriteria(int limit) {
        SmsLogSearchCriteria criteria = new SmsLogSearchCriteria();
        criteria.setDateFrom(dateFromPicker.getValue());
        criteria.setDateTo(dateToPicker.getValue());
        Church church = churchComboBox.getValue();
        criteria.setChurchId(church == null ? null : church.getId());
        if (statusComboBox.getValue() != null && !STATUS_ALL.equals(statusComboBox.getValue())) {
            criteria.setStatus(SmsLogRepository.SmsStatus.valueOf(statusComboBox.getValue()));
        }
        criteria.setMobileNumber(mobileNumberField.getText());
        criteria.setReceiptNo(receiptNoField.getText());
        criteria.setLimit(limit);
        return criteria;
    }

    private void showSmsLogDetails(SmsLogDto log) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.INFORMATION));
        alert.setTitle("SMS Log Details");
        alert.setHeaderText("SMS Log " + nullToDash(log.getSmsLogUuid()));
        alert.getDialogPane().setContent(detailsContent(log));
        alert.getDialogPane().setPrefWidth(920);
        alert.showAndWait();
    }

    private VBox detailsContent(SmsLogDto log) {
        HBox detailsRow = new HBox(14,
                detailCard("Send Details", sendDetailsGrid(log)),
                detailCard("SMS Details", smsDetailsContent(log)));
        detailsRow.setFillHeight(true);
        VBox content = new VBox(detailsRow);
        content.setPrefWidth(860);
        return content;
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

    private GridPane sendDetailsGrid(SmsLogDto log) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);
        addDetailRow(grid, 0, "Date/Time", formatDateTime(log.getCreatedAt()));
        addDetailRow(grid, 1, "Receipt No", nullToDash(log.getReceiptNo()));
        addDetailRow(grid, 2, "Church", nullToDash(log.getChurchCode()) + " - " + nullToDash(log.getChurchName()));
        addDetailRow(grid, 3, "Mobile Number", nullToDash(log.getMobileNumber()));
        addDetailRow(grid, 4, "Provider", nullToDash(log.getProvider()));
        addDetailRow(grid, 5, "Status", statusBadge(log));
        addDetailRow(grid, 6, "Sent At", formatDateTime(log.getSentAt()));
        addDetailRow(grid, 7, "Log Type", logTypeBadge(log));
        addDetailRow(grid, 8, "Resend Of SMS Log UUID", log.getResendOfSmsLogId() == null
                ? "-"
                : nullToDash(log.getResendOfSmsLogUuid()));
        addDetailRow(grid, 9, "Resent By", nullToDash(log.getResentByUserFullName()));
        addDetailRow(grid, 10, "Resend Reason", nullToDash(log.getResendReason()));
        return grid;
    }

    private VBox smsDetailsContent(SmsLogDto log) {
        TextArea messageArea = readOnlyArea(log.getMessage());
        TextArea errorArea = readOnlyArea(log.getErrorMessage());
        Label messageLabel = detailFieldLabel("Message");
        Label errorLabel = detailFieldLabel("Error Message");
        VBox content = new VBox(8, messageLabel, messageArea, errorLabel, errorArea);
        content.setPrefWidth(380);
        return content;
    }

    private void addDetailRow(GridPane grid, int row, String labelText, String valueText) {
        Label label = detailFieldLabel(labelText);
        Label value = new Label(valueText);
        value.setWrapText(true);
        value.getStyleClass().add("value-label");
        grid.add(label, 0, row);
        grid.add(value, 1, row);
        GridPane.setHgrow(value, Priority.ALWAYS);
    }

    private void addDetailRow(GridPane grid, int row, String labelText, Node valueNode) {
        Label label = detailFieldLabel(labelText);
        grid.add(label, 0, row);
        grid.add(valueNode, 1, row);
        GridPane.setHgrow(valueNode, Priority.ALWAYS);
    }

    private Label detailFieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("receipt-detail-label");
        return label;
    }

    private Label statusBadge(SmsLogDto log) {
        Label badge = new Label(statusText(log));
        badge.getStyleClass().add("status-badge");
        applyStatusStyle(badge, log.getStatus());
        return badge;
    }

    private Label logTypeBadge(SmsLogDto log) {
        boolean resend = log.getResendOfSmsLogId() != null;
        Label badge = new Label(resend ? "RESENT" : "ORIGINAL");
        badge.getStyleClass().add("status-badge");
        badge.getStyleClass().add(resend ? "status-correction-note" : "status-active");
        return badge;
    }

    private String statusText(SmsLogDto log) {
        String status = nullToDash(log.getStatus());
        return log.getResendOfSmsLogId() == null ? status : status;
    }

    private static void applyStatusStyle(Label badge, String status) {
        badge.getStyleClass().removeAll("status-active", "status-inactive", "status-skipped");
        if ("SUCCESS".equals(status)) {
            badge.getStyleClass().add("status-active");
        } else if ("FAILED".equals(status)) {
            badge.getStyleClass().add("status-inactive");
        } else {
            badge.getStyleClass().add("status-skipped");
        }
    }

    private TextArea readOnlyArea(String value) {
        TextArea textArea = new TextArea(nullToDash(value));
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(4);
        return textArea;
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

    private void showError(String title, String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
        setMessage(message);
    }

    private void resendSms(SmsLogDto log) {
        if (!log.isCanResend()) {
            showError("Resend SMS", "SMS resend period has expired. Resend is allowed only within 7 days.");
            return;
        }

        OptionalResendReason reason = promptResendConfirmation(log);
        if (!reason.confirmed()) {
            return;
        }
        if (reason.reason() == null || reason.reason().isBlank()) {
            showError("Resend SMS", "Resend reason is required.");
            return;
        }

        SmsResendRequest request = new SmsResendRequest();
        request.setSmsLogId(log.getId());
        request.setResendReason(reason.reason());
        try {
            smsResendService.resendSms(request);
            setMessage("SMS resent for log " + log.getId() + ".");
            handleSearch();
        } catch (SmsResendService.SmsResendException exception) {
            showError("Resend SMS", exception.getMessage());
        } catch (DatabaseException exception) {
            showError("Resend SMS", "Unable to resend SMS.");
        }
    }

    private OptionalResendReason promptResendConfirmation(SmsLogDto log) {
        Dialog<String> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle("Resend SMS");
        dialog.setHeaderText("Are you sure you want to resend this SMS?");
        ButtonType resendButton = new ButtonType("Resend SMS", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(resendButton, ButtonType.CANCEL);

        GridPane summary = new GridPane();
        summary.setHgap(12);
        summary.setVgap(8);
        addDetailRow(summary, 0, "Mobile Number", nullToDash(log.getMobileNumber()));
        addDetailRow(summary, 1, "Receipt No", nullToDash(log.getReceiptNo()));
        addDetailRow(summary, 2, "Church", nullToDash(log.getChurchCode()) + " - " + nullToDash(log.getChurchName()));

        TextArea messageArea = readOnlyArea(log.getMessage());
        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Resend reason");
        reasonArea.setWrapText(true);
        reasonArea.setPrefRowCount(3);
        VBox content = new VBox(10, summary, DialogStyler.fieldLabel("Message"), messageArea,
                DialogStyler.fieldLabel("Resend Reason"), reasonArea);
        content.setPrefWidth(620);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(buttonType -> buttonType == resendButton ? reasonArea.getText() : null);
        return dialog.showAndWait()
                .map(reason -> new OptionalResendReason(true, reason))
                .orElseGet(() -> new OptionalResendReason(false, null));
    }

    private static class SmsStatusCell extends TableCell<SmsLogDto, String> {
        private final Label badge = new Label();
        private final Label resendBadge = new Label("R");
        private final HBox container = new HBox(6, badge, resendBadge);

        private SmsStatusCell() {
            getStyleClass().add("centered-table-cell");
            setAlignment(Pos.CENTER);
            container.setAlignment(Pos.CENTER);
            badge.getStyleClass().add("status-badge");
            resendBadge.getStyleClass().add("status-correction-note");
        }

        @Override
        protected void updateItem(String status, boolean empty) {
            super.updateItem(status, empty);
            if (empty || status == null) {
                setGraphic(null);
                return;
            }

            badge.setText(status);
            boolean resent = false;
            if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                SmsLogDto log = getTableView().getItems().get(getIndex());
                resent = log.getResendOfSmsLogId() != null;
            }
            resendBadge.setVisible(resent);
            resendBadge.setManaged(resent);
            applyStatusStyle(badge, status);
            setGraphic(container);
        }
    }

    private class SmsActionCell extends TableCell<SmsLogDto, Void> {
        private final Button viewButton = new Button();
        private final Button resendButton = new Button();
        private final HBox actionBox = new HBox(6, viewButton, resendButton);

        private SmsActionCell() {
            getStyleClass().add("receipt-action-cell");
            actionBox.setAlignment(Pos.CENTER);
            viewButton.getStyleClass().add("table-action-button");
            resendButton.getStyleClass().add("table-action-button");
            ButtonIconUtil.applyTableActionIcon(viewButton, "fas-eye", "View SMS log");
            ButtonIconUtil.applyTableActionIcon(resendButton, "fas-paper-plane", "Resend SMS");
            viewButton.setOnAction(event -> {
                if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                    showSmsLogDetails(getTableView().getItems().get(getIndex()));
                }
            });
            resendButton.setOnAction(event -> {
                if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                    resendSms(getTableView().getItems().get(getIndex()));
                }
            });
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getIndex() >= getTableView().getItems().size()) {
                setGraphic(null);
                return;
            }
            SmsLogDto log = getTableView().getItems().get(getIndex());
            resendButton.setVisible(canResendSms);
            resendButton.setManaged(canResendSms);
            resendButton.setDisable(!log.isCanResend());
            resendButton.setTooltip(log.isCanResend()
                    ? new Tooltip("Resend SMS")
                    : new Tooltip("SMS resend period has expired. Resend is allowed only within 7 days."));
            setGraphic(actionBox);
        }
    }

    private record OptionalResendReason(boolean confirmed, String reason) {
    }
}
