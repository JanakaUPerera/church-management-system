package com.churchmanagement.controller;

import com.churchmanagement.dto.SmsLogDto;
import com.churchmanagement.dto.SmsLogSearchCriteria;
import com.churchmanagement.dto.SmsResendRequest;
import com.churchmanagement.entity.Church;
import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.enums.SmsSendStatus;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.ChurchService;
import com.churchmanagement.service.SmsLogService;
import com.churchmanagement.service.SmsResendService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.ComboBoxUtil;
import com.churchmanagement.util.DatePickerUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ProcessingDialog;
import com.churchmanagement.util.SystemDateTimeFormatter;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SmsLogController {
    private static final String STATUS_ALL = "ALL";

    private final SmsLogService smsLogService = new SmsLogService();
    private final SystemDateTimeFormatter dateTimeFormatter = new SystemDateTimeFormatter();
    private final SmsResendService smsResendService = new SmsResendService();
    private final ChurchService churchService = new ChurchService();
    private final ObservableList<SmsLogDto> smsLogs = FXCollections.observableArrayList();
    private final ObservableList<Church> churches = FXCollections.observableArrayList();
    private boolean canResendSms;

    @FXML private DatePicker dateFromPicker;
    @FXML private DatePicker dateToPicker;
    @FXML private ComboBox<Church> churchComboBox;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private ComboBox<String> deliveryStatusComboBox;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Button refreshButton;
    @FXML private TableView<SmsLogDto> smsLogTable;
    @FXML private TableColumn<SmsLogDto, String> dateTimeColumn;
    @FXML private TableColumn<SmsLogDto, String> receiptNoColumn;
    @FXML private TableColumn<SmsLogDto, String> churchCodeColumn;
    @FXML private TableColumn<SmsLogDto, String> mobileNumberColumn;
    @FXML private TableColumn<SmsLogDto, String> messageColumn;
    @FXML private TableColumn<SmsLogDto, String> sendStatusColumn;
    @FXML private TableColumn<SmsLogDto, String> deliveryStatusColumn;
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
        SmsLogSearchCriteria criteria = buildCriteria(500);
        ProcessingDialog.run("Search SMS Logs", "Searching SMS logs...",
                () -> smsLogService.searchSmsLogs(criteria),
                results -> {
                    smsLogs.setAll(results);
                    setMessage(smsLogs.size() + " SMS log(s) found.");
                },
                throwable -> showError("SMS Logs", friendlySmsLogError(throwable)));
    }

    @FXML
    private void handleRefresh() {
        dateFromPicker.setValue(null);
        dateToPicker.setValue(null);
        churchComboBox.getSelectionModel().clearSelection();
        statusComboBox.setValue(STATUS_ALL);
        deliveryStatusComboBox.setValue(STATUS_ALL);
        searchField.clear();
        loadLatestLogs();
    }

    private void configureButtonIcons() {
        canResendSms = AuthContext.getCurrentUser()
                .map(user -> new PermissionGuard(user).can("sms.resend"))
                .orElse(false);
        ButtonIconUtil.applyIcon(searchButton, "fas-search");
        ButtonIconUtil.applyIcon(refreshButton, "fas-sync-alt");
    }

    private void configureFilters() {
        DatePickerUtil.disableFutureDates(dateFromPicker);
        DatePickerUtil.disableFutureDates(dateToPicker);
        ComboBoxUtil.makeChurchSearchable(churchComboBox, churches);
        statusComboBox.setItems(FXCollections.observableArrayList(enumOptions(SmsSendStatus.values())));
        statusComboBox.setValue(STATUS_ALL);
        deliveryStatusComboBox.setItems(FXCollections.observableArrayList(enumOptions(SmsDeliveryStatus.values())));
        deliveryStatusComboBox.setValue(STATUS_ALL);
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
        mobileNumberColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getMobileNumber())));
        messageColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getMessage())));
        sendStatusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getSendStatus())));
        deliveryStatusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getDeliveryStatus())));

        messageColumn.setCellFactory(column -> tooltipTextCell());
        sendStatusColumn.setCellFactory(column -> new SmsStatusCell());
        deliveryStatusColumn.setCellFactory(column -> new SmsStatusCell());
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
            churches.setAll(churchService.findAll());
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
            criteria.setSendStatus(SmsSendStatus.valueOf(statusComboBox.getValue()));
        }
        if (deliveryStatusComboBox.getValue() != null && !STATUS_ALL.equals(deliveryStatusComboBox.getValue())) {
            criteria.setDeliveryStatus(SmsDeliveryStatus.valueOf(deliveryStatusComboBox.getValue()));
        }
        criteria.setSearchText(searchField.getText());
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
        addDetailRow(grid, 5, "Send Status", statusBadge(log.getSendStatus()));
        addDetailRow(grid, 6, "Delivery Status", statusBadge(log.getDeliveryStatus()));
        addDetailRow(grid, 7, "Modem Ref", nullToDash(log.getModemMessageReference()));
        addDetailRow(grid, 8, "Attempts", String.valueOf(log.getAttemptCount()));
        addDetailRow(grid, 9, "Last Attempt At", formatDateTime(log.getLastAttemptAt()));
        addDetailRow(grid, 10, "Sent At", formatDateTime(log.getSentAt()));
        addDetailRow(grid, 11, "Log Type", logTypeBadge(log));
        addDetailRow(grid, 12, "Resend Of SMS Log UUID", log.getResendOfSmsLogId() == null
                ? "-"
                : nullToDash(log.getResendOfSmsLogUuid()));
        addDetailRow(grid, 13, "Resent By", nullToDash(log.getResentByUserFullName()));
        addDetailRow(grid, 14, "Resend Reason", nullToDash(log.getResendReason()));
        return grid;
    }

    private VBox smsDetailsContent(SmsLogDto log) {
        TextArea messageArea = readOnlyArea(log.getMessage());
        TextArea rawModemArea = readOnlyArea(log.getModemRawResponse());
        TextArea deliveryReportArea = readOnlyArea(log.getDeliveryReportRaw());
        TextArea errorArea = readOnlyArea(log.getErrorMessage());
        Label messageLabel = detailFieldLabel("Message");
        Label rawModemLabel = detailFieldLabel("Raw Modem Response");
        Label deliveryReportLabel = detailFieldLabel("Delivery Report Raw");
        Label errorCodeLabel = detailFieldLabel("Error Code: " + nullToDash(log.getErrorCode()));
        Label errorLabel = detailFieldLabel("Error Message");
        VBox content = new VBox(8, messageLabel, messageArea, rawModemLabel, rawModemArea,
                deliveryReportLabel, deliveryReportArea, errorCodeLabel, errorLabel, errorArea);
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

    private Label statusBadge(String status) {
        Label badge = new Label(nullToDash(status));
        badge.getStyleClass().add("status-badge");
        applyStatusStyle(badge, status);
        return badge;
    }

    private Label logTypeBadge(SmsLogDto log) {
        boolean resend = log.getResendOfSmsLogId() != null;
        Label badge = new Label(resend ? "RESENT" : "ORIGINAL");
        badge.getStyleClass().add("status-badge");
        badge.getStyleClass().add(resend ? "status-correction-note" : "status-active");
        return badge;
    }

    private static void applyStatusStyle(Label badge, String status) {
        badge.getStyleClass().removeAll("status-active", "status-inactive", "status-skipped");
        String normalizedStatus = status == null ? "" : status;
        if (normalizedStatus.startsWith("SUCCESS")
                || normalizedStatus.startsWith("SENT")
                || normalizedStatus.startsWith("DELIVERED")) {
            badge.getStyleClass().add("status-active");
        } else if (normalizedStatus.startsWith("FAILED")) {
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
        return dateTimeFormatter.formatDateTime(dateTime);
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private List<String> enumOptions(Enum<?>[] values) {
        List<String> options = new ArrayList<>();
        options.add(STATUS_ALL);
        options.addAll(Arrays.stream(values).map(Enum::name).toList());
        return options;
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

    private String friendlySmsLogError(Throwable throwable) {
        if (throwable instanceof SmsLogService.SmsLogException) {
            return throwable.getMessage();
        }
        if (throwable instanceof DatabaseException) {
            return "Unable to load SMS logs.";
        }
        return "Unable to search SMS logs.";
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
        ProcessingDialog.run("Resend SMS", "Resending SMS...",
                () -> smsResendService.resendSms(request),
                result -> {
            setMessage("SMS resent for log " + log.getId() + ".");
            handleSearch();
                },
                throwable -> showProcessingError("Resend SMS", throwable));
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
        addDetailRow(summary, 0, "Mobile Number", nullToDash(resendMobileNumber(log)));
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

    private String resendMobileNumber(SmsLogDto log) {
        if (log.getChurchId() == null) {
            return log.getMobileNumber();
        }
        try {
            Church church = churchService.findById(log.getChurchId());
            return church.getSmsMobileNumber();
        } catch (RuntimeException exception) {
            return log.getMobileNumber();
        }
    }

    private static class SmsStatusCell extends TableCell<SmsLogDto, String> {
        private final Label sendBadge = new Label();
        private final Label resendBadge = new Label("R");
        private final HBox container = new HBox(6, sendBadge, resendBadge);

        private SmsStatusCell() {
            getStyleClass().add("centered-table-cell");
            setAlignment(Pos.CENTER);
            container.setAlignment(Pos.CENTER);
            sendBadge.getStyleClass().add("status-badge");
            resendBadge.getStyleClass().add("status-correction-note");
        }

        @Override
        protected void updateItem(String status, boolean empty) {
            super.updateItem(status, empty);
            if (empty || status == null) {
                setGraphic(null);
                return;
            }

            sendBadge.setText(status);
            boolean resent = false;
            if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                SmsLogDto log = getTableView().getItems().get(getIndex());
                resent = log.getResendOfSmsLogId() != null;
            }
            resendBadge.setVisible(resent);
            resendBadge.setManaged(resent);
            applyStatusStyle(sendBadge, status);
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
                    : new Tooltip(nullToDash(log.getResendDisabledReason())));
            setGraphic(actionBox);
        }
    }

    private record OptionalResendReason(boolean confirmed, String reason) {
    }

    private void showProcessingError(String title, Throwable throwable) {
        String message = throwable.getMessage() == null ? "Action failed. Please try again." : throwable.getMessage();
        showError(title, message);
    }
}
