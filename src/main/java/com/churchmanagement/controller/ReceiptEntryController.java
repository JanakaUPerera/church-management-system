package com.churchmanagement.controller;

import com.churchmanagement.dto.CreateReceiptRequest;
import com.churchmanagement.dto.ReceiptItemDto;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.entity.Church;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.ChurchService;
import com.churchmanagement.service.ReceiptService;
import com.churchmanagement.util.WeekUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class ReceiptEntryController {
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^\\d+(\\.\\d{1,2})?$");
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");

    private final ChurchService churchService = new ChurchService();
    private final ReceiptService receiptService = new ReceiptService();
    private final ObservableList<Church> activeChurches = FXCollections.observableArrayList();

    private AuthenticatedUser currentUser;
    private PermissionGuard permissionGuard;

    @FXML private ComboBox<Church> churchComboBox;
    @FXML private Label regionLabel;
    @FXML private DatePicker weekStartDatePicker;
    @FXML private Label weekEndDateLabel;
    @FXML private Label lateSubmissionLabel;
    @FXML private Label lateReasonLabel;
    @FXML private TextArea lateSubmissionReasonArea;
    @FXML private TextField submittedByNameField;
    @FXML private TextField offertoryAmountField;
    @FXML private TextField offertoryNoteField;
    @FXML private TextField tithesAmountField;
    @FXML private TextField tithesNoteField;
    @FXML private TextField otherDonationsAmountField;
    @FXML private TextField otherDonationsNoteField;
    @FXML private Button saveButton;
    @FXML private Label totalAmountLabel;
    @FXML private Label messageLabel;

    @FXML
    private void initialize() {
        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty()) {
            setMessage("Please sign in to create receipts.");
            setFormDisabled(true);
            return;
        }

        currentUser = user.get();
        permissionGuard = new PermissionGuard(currentUser);
        if (!permissionGuard.can("receipt.create")) {
            setMessage("You do not have permission to create receipts.");
            setFormDisabled(true);
            return;
        }

        configureControls();
        loadActiveChurches();
        weekStartDatePicker.setValue(WeekUtil.getPreviousWeekMonday(LocalDate.now()));
        updateWeekState();
    }

    @FXML
    private void handleSave() {
        if (!validateAmountFields()) {
            return;
        }

        CreateReceiptRequest request = buildRequest();
        if (!validateRequestBeforeConfirm(request)) {
            return;
        }

        if (!confirmSummary(request)) {
            return;
        }

        try {
            ReceiptResponseDto response = receiptService.createReceipt(request);
            showInfo("Receipt Saved", "Receipt " + response.getReceiptNo() + " was created successfully.");
            setMessage("Receipt " + response.getReceiptNo() + " created successfully.");
            clearForm();
        } catch (ReceiptService.ReceiptException | SecurityException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    @FXML
    private void handleClear() {
        clearForm();
    }

    private void configureControls() {
        churchComboBox.setItems(activeChurches);
        churchComboBox.setCellFactory(listView -> new ChurchListCell());
        churchComboBox.setButtonCell(new ChurchListCell());
        churchComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateSelectedRegion());
        weekStartDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> updateWeekState());
        offertoryAmountField.textProperty().addListener((observable, oldValue, newValue) -> updateTotalAmount());
        tithesAmountField.textProperty().addListener((observable, oldValue, newValue) -> updateTotalAmount());
        otherDonationsAmountField.textProperty().addListener((observable, oldValue, newValue) -> updateTotalAmount());
        updateTotalAmount();
    }

    private void loadActiveChurches() {
        try {
            activeChurches.setAll(churchService.findAll().stream()
                    .filter(church -> church.getStatus() == Church.Status.ACTIVE)
                    .toList());
        } catch (ChurchService.ChurchException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void updateSelectedRegion() {
        Church church = churchComboBox.getValue();
        regionLabel.setText(church == null ? "-" : church.getRegionCode() + " - " + church.getRegionName());
    }

    private void updateWeekState() {
        LocalDate start = weekStartDatePicker.getValue();
        LocalDate end = WeekUtil.getSundayForMonday(start);
        weekEndDateLabel.setText(end == null ? "-" : end.format(DateTimeFormatter.ISO_LOCAL_DATE));

        boolean late = start != null && WeekUtil.isBackWeek(start, LocalDate.now());
        lateSubmissionLabel.setText(late ? "YES" : "NO");
        lateSubmissionLabel.getStyleClass().removeAll("status-active", "status-inactive");
        lateSubmissionLabel.getStyleClass().add(late ? "status-inactive" : "status-active");
        lateReasonLabel.setVisible(late);
        lateReasonLabel.setManaged(late);
        lateSubmissionReasonArea.setVisible(late);
        lateSubmissionReasonArea.setManaged(late);
        lateSubmissionReasonArea.setDisable(!late);
        if (!late) {
            lateSubmissionReasonArea.clear();
        }
    }

    private CreateReceiptRequest buildRequest() {
        CreateReceiptRequest request = new CreateReceiptRequest();
        Church selectedChurch = churchComboBox.getValue();
        LocalDate weekStart = weekStartDatePicker.getValue();
        request.setChurchId(selectedChurch == null ? null : selectedChurch.getId());
        request.setWeekStartDate(weekStart);
        request.setWeekEndDate(WeekUtil.getSundayForMonday(weekStart));
        request.setSubmittedByName(submittedByNameField.getText());
        request.setLateSubmissionReason(lateSubmissionReasonArea.getText());
        request.setItems(buildItems());
        return request;
    }

    private List<ReceiptItemDto> buildItems() {
        List<ReceiptItemDto> items = new ArrayList<>();
        addItemIfAmountAvailable(items, CollectionType.OFFERTORY, offertoryAmountField, offertoryNoteField);
        addItemIfAmountAvailable(items, CollectionType.TITHES, tithesAmountField, tithesNoteField);
        addItemIfAmountAvailable(items, CollectionType.OTHER_DONATIONS, otherDonationsAmountField, otherDonationsNoteField);
        return items;
    }

    private void addItemIfAmountAvailable(List<ReceiptItemDto> items, CollectionType collectionType,
                                          TextField amountField, TextField noteField) {
        String amountText = amountField.getText() == null ? "" : amountField.getText().strip();
        if (amountText.isBlank()) {
            return;
        }

        BigDecimal amount = new BigDecimal(amountText);
        items.add(new ReceiptItemDto(collectionType, amount, noteField.getText()));
    }

    private boolean validateAmountFields() {
        List<String> errors = new ArrayList<>();
        validateAmountField("Offertory", offertoryAmountField, errors);
        validateAmountField("Tithes", tithesAmountField, errors);
        validateAmountField("Other Donations", otherDonationsAmountField, errors);

        if (!errors.isEmpty()) {
            showFriendlyError(String.join("\n", errors));
            return false;
        }
        return true;
    }

    private void validateAmountField(String label, TextField amountField, List<String> errors) {
        String amountText = amountField.getText() == null ? "" : amountField.getText().strip();
        if (amountText.isBlank()) {
            return;
        }

        if (!AMOUNT_PATTERN.matcher(amountText).matches()
                || new BigDecimal(amountText).compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(label + " amount must be a positive number with up to two decimal places.");
        }
    }

    private void updateTotalAmount() {
        totalAmountLabel.setText(formatAmount(readAmountForTotal(offertoryAmountField)
                .add(readAmountForTotal(tithesAmountField))
                .add(readAmountForTotal(otherDonationsAmountField))));
    }

    private BigDecimal readAmountForTotal(TextField amountField) {
        String amountText = amountField.getText() == null ? "" : amountField.getText().strip();
        if (amountText.isBlank() || !AMOUNT_PATTERN.matcher(amountText).matches()) {
            return BigDecimal.ZERO;
        }

        BigDecimal amount = new BigDecimal(amountText);
        return amount.compareTo(BigDecimal.ZERO) > 0 ? amount : BigDecimal.ZERO;
    }

    private boolean confirmSummary(CreateReceiptRequest request) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Save Receipt");
        alert.setHeaderText("Confirm receipt submission");
        alert.getDialogPane().setContent(summaryGrid(request));
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private boolean validateRequestBeforeConfirm(CreateReceiptRequest request) {
        try {
            receiptService.validateReceiptBeforeConfirmation(request);
            return true;
        } catch (ReceiptService.ReceiptException | SecurityException exception) {
            showFriendlyError(exception.getMessage());
            return false;
        }
    }

    private VBox summaryGrid(CreateReceiptRequest request) {
        Church church = churchComboBox.getValue();
        BigDecimal total = request.getItems().stream()
                .map(ReceiptItemDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        VBox container = new VBox(10);
        container.setPrefWidth(430);
        container.getChildren().addAll(
                new Label("Church: " + (church == null ? "" : church.getChurchCode() + " - " + church.getChurchName())),
                new Label("Week: " + request.getWeekStartDate() + " to " + request.getWeekEndDate()),
                itemGrid(request, total),
                new Label("Late submission: " + (WeekUtil.isBackWeek(request.getWeekStartDate(), LocalDate.now()) ? "YES" : "NO"))
        );
        return container;
    }

    private GridPane itemGrid(CreateReceiptRequest request, BigDecimal total) {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);

        ColumnConstraints itemColumn = new ColumnConstraints();
        itemColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints amountColumn = new ColumnConstraints();
        amountColumn.setMinWidth(120);
        grid.getColumnConstraints().addAll(itemColumn, amountColumn);

        Label itemHeader = new Label("Item");
        itemHeader.setStyle("-fx-font-weight: bold;");
        Label amountHeader = amountLabel("Amount");
        amountHeader.setStyle("-fx-font-weight: bold;");
        grid.add(itemHeader, 0, 0);
        grid.add(amountHeader, 1, 0);

        int row = 1;
        for (ReceiptItemDto item : request.getItems()) {
            grid.add(new Label(item.getCollectionType().getDisplayLabel()), 0, row);
            grid.add(amountLabel(formatAmount(item.getAmount())), 1, row);
            row++;
        }

        Label totalLabel = new Label("Total");
        totalLabel.setStyle("-fx-font-weight: bold;");
        Label totalAmountLabel = amountLabel(formatAmount(total));
        totalAmountLabel.setStyle("-fx-font-weight: bold;");
        grid.add(totalLabel, 0, row);
        grid.add(totalAmountLabel, 1, row);
        return grid;
    }

    private Label amountLabel(String text) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER_RIGHT);
        return label;
    }

    private String formatAmount(BigDecimal amount) {
        return AMOUNT_FORMAT.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private void clearForm() {
        churchComboBox.getSelectionModel().clearSelection();
        updateSelectedRegion();
        weekStartDatePicker.setValue(WeekUtil.getPreviousWeekMonday(LocalDate.now()));
        submittedByNameField.clear();
        lateSubmissionReasonArea.clear();
        offertoryAmountField.clear();
        offertoryNoteField.clear();
        tithesAmountField.clear();
        tithesNoteField.clear();
        otherDonationsAmountField.clear();
        otherDonationsNoteField.clear();
        updateTotalAmount();
        updateWeekState();
    }

    private void setFormDisabled(boolean disabled) {
        churchComboBox.setDisable(disabled);
        weekStartDatePicker.setDisable(disabled);
        submittedByNameField.setDisable(disabled);
        lateSubmissionReasonArea.setDisable(disabled);
        offertoryAmountField.setDisable(disabled);
        offertoryNoteField.setDisable(disabled);
        tithesAmountField.setDisable(disabled);
        tithesNoteField.setDisable(disabled);
        otherDonationsAmountField.setDisable(disabled);
        otherDonationsNoteField.setDisable(disabled);
        saveButton.setDisable(disabled);
    }

    private void setMessage(String message) {
        messageLabel.setText(message);
    }

    private void showFriendlyError(String message) {
        setMessage(message);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Receipt Submission");
        alert.setHeaderText("Unable to save receipt");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static class ChurchListCell extends ListCell<Church> {
        @Override
        protected void updateItem(Church church, boolean empty) {
            super.updateItem(church, empty);
            setText(empty || church == null ? null : church.getChurchCode() + " - " + church.getChurchName());
        }
    }
}
