package com.churchmanagement.controller;

import com.churchmanagement.dto.OfficialSmsRecipient;
import com.churchmanagement.dto.OfficialSmsResult;
import com.churchmanagement.navigation.UnsavedChangesAware;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.OfficialSmsService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ProcessingDialog;
import com.churchmanagement.util.SmsLengthCalculator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SendMessageController implements UnsavedChangesAware {
    private static final String ALL_REGIONS = "All regions";
    private static final String ERROR_STYLE = "error-label";
    private static final String WARNING_STYLE = "warning-label";

    @FXML private ComboBox<String> regionComboBox;
    @FXML private TextField searchField;
    @FXML private Button selectAllButton;
    @FXML private Button clearButton;
    @FXML private Label selectionSummaryLabel;
    @FXML private TableView<RecipientRow> recipientTable;
    @FXML private TableColumn<RecipientRow, Boolean> selectColumn;
    @FXML private TableColumn<RecipientRow, String> codeColumn;
    @FXML private TableColumn<RecipientRow, String> nameColumn;
    @FXML private TableColumn<RecipientRow, String> regionColumn;
    @FXML private TableColumn<RecipientRow, String> mobileColumn;
    @FXML private TableColumn<RecipientRow, String> statusColumn;
    @FXML private TextArea messageTextArea;
    @FXML private Label footerPreviewLabel;
    @FXML private Label counterLabel;
    @FXML private Label totalLabel;
    @FXML private Button sendButton;

    private final OfficialSmsService officialSmsService = new OfficialSmsService();
    private final ObservableList<RecipientRow> allRows = FXCollections.observableArrayList();
    private final FilteredList<RecipientRow> shownRows = new FilteredList<>(allRows, row -> true);
    private boolean canSend;
    private boolean messageValid;
    private int partCount;

    @FXML
    private void initialize() {
        canSend = AuthContext.getCurrentUser()
                .map(user -> new PermissionGuard(user).can(OfficialSmsService.SEND_PERMISSION))
                .orElse(false);
        ButtonIconUtil.applyIcon(sendButton, "fas-paper-plane");
        configureTable();
        regionComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        messageTextArea.textProperty().addListener((observable, oldValue, newValue) -> refreshMessageState());
        showFooterPreview();
        loadRecipients();
        refreshMessageState();
    }

    @Override
    public boolean hasUnsavedChanges() {
        return messageTextArea.getText() != null && !messageTextArea.getText().isBlank();
    }

    @FXML
    private void handleSelectAll() {
        shownRows.stream().filter(row -> row.recipient().isSendable()).forEach(row -> row.selected().set(true));
    }

    @FXML
    private void handleClear() {
        allRows.forEach(row -> row.selected().set(false));
    }

    @FXML
    private void handleSend() {
        List<Long> churchIds = selectedChurchIds();
        int smsCount = churchIds.size() * partCount;
        Alert confirmation = DialogStyler.apply(new Alert(Alert.AlertType.CONFIRMATION));
        confirmation.setTitle("Send Message");
        confirmation.setHeaderText("Send Message");
        confirmation.setContentText("Send this message to " + churchIds.size() + " branch"
                + (churchIds.size() == 1 ? "" : "es") + " (" + smsCount + " SMS)?");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        String body = messageTextArea.getText();
        ProcessingDialog.run("Send Message", "Queuing messages...",
                () -> officialSmsService.sendOfficialMessage(body, churchIds),
                this::showSent,
                throwable -> showError(throwable instanceof OfficialSmsService.OfficialSmsException
                        ? throwable.getMessage()
                        : "Unable to queue the messages. Please try again."));
    }

    private void configureTable() {
        selectColumn.setCellValueFactory(cellData -> cellData.getValue().selected());
        selectColumn.setCellFactory(column -> new CheckBoxTableCell<>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                RecipientRow row = empty || getTableRow() == null ? null : getTableRow().getItem();
                setDisable(row == null || !row.recipient().isSendable());
            }
        });
        selectColumn.setEditable(true);
        codeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().recipient().churchCode()));
        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().recipient().churchName()));
        regionColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().recipient().regionName()));
        mobileColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().recipient().mobileNumber()));
        statusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(statusText(cellData.getValue().recipient())));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                getStyleClass().remove(WARNING_STYLE);
                RecipientRow row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row != null && !row.recipient().isSendable()) {
                    getStyleClass().add(WARNING_STYLE);
                }
            }
        });
        recipientTable.setItems(shownRows);
    }

    private void loadRecipients() {
        ProcessingDialog.run("Send Message", "Loading branches...",
                officialSmsService::loadRecipients,
                recipients -> {
                    allRows.setAll(recipients.stream().map(RecipientRow::new).toList());
                    allRows.forEach(row -> row.selected().addListener((observable, oldValue, newValue) ->
                            refreshMessageState()));
                    List<String> regions = recipients.stream().map(OfficialSmsRecipient::regionName)
                            .filter(Objects::nonNull).distinct().sorted().toList();
                    regionComboBox.getItems().setAll(ALL_REGIONS);
                    regionComboBox.getItems().addAll(regions);
                    regionComboBox.setValue(ALL_REGIONS);
                    refreshMessageState();
                },
                throwable -> showError("Unable to load branches."));
    }

    private void applyFilter() {
        String region = regionComboBox.getValue();
        String term = searchField.getText() == null ? "" : searchField.getText().strip().toLowerCase(Locale.ROOT);
        shownRows.setPredicate(row -> {
            OfficialSmsRecipient recipient = row.recipient();
            boolean regionMatches = region == null || ALL_REGIONS.equals(region)
                    || region.equals(recipient.regionName());
            boolean termMatches = term.isEmpty()
                    || recipient.churchCode().toLowerCase(Locale.ROOT).contains(term)
                    || recipient.churchName().toLowerCase(Locale.ROOT).contains(term);
            return regionMatches && termMatches;
        });
        refreshMessageState();
    }

    private void refreshMessageState() {
        String message = officialSmsService.composeMessage(messageTextArea.getText());
        String unsupported = SmsLengthCalculator.firstUnsupportedCharacter(message);
        counterLabel.getStyleClass().remove(ERROR_STYLE);
        if (unsupported != null) {
            messageValid = false;
            partCount = 0;
            counterLabel.setText("Unsupported character: '" + unsupported + "'");
            counterLabel.getStyleClass().add(ERROR_STYLE);
        } else {
            int septets = SmsLengthCalculator.septetLength(message);
            partCount = SmsLengthCalculator.partCount(message);
            messageValid = septets <= SmsLengthCalculator.MAX_SEPTETS;
            counterLabel.setText(septets + " / " + SmsLengthCalculator.MAX_SEPTETS + " characters · "
                    + partCount + " SMS part" + (partCount == 1 ? "" : "s"));
            if (!messageValid) {
                counterLabel.getStyleClass().add(ERROR_STYLE);
            }
        }

        int selected = selectedChurchIds().size();
        long shownWithoutNumber = shownRows.stream().filter(row -> !row.recipient().isSendable()).count();
        selectionSummaryLabel.setText(selected + " selected · " + shownWithoutNumber
                + " shown without a valid number");
        totalLabel.setText((messageValid ? selected * partCount : 0) + " SMS will be sent");
        boolean blank = messageTextArea.getText() == null || messageTextArea.getText().isBlank();
        sendButton.setDisable(!canSend || selected == 0 || blank || !messageValid);
    }

    private void showFooterPreview() {
        String footer = officialSmsService.footer();
        footerPreviewLabel.setText(footer.isEmpty() ? "" : "Footer added: " + footer);
        footerPreviewLabel.setVisible(!footer.isEmpty());
        footerPreviewLabel.setManaged(!footer.isEmpty());
    }

    private List<Long> selectedChurchIds() {
        return allRows.stream().filter(row -> row.selected().get()).map(row -> row.recipient().churchId()).toList();
    }

    private void showSent(OfficialSmsResult result) {
        StringBuilder text = new StringBuilder(result.queuedCount() + " message"
                + (result.queuedCount() == 1 ? "" : "s") + " queued.");
        if (!result.skipped().isEmpty()) {
            text.append(' ').append(result.skipped().size()).append(" skipped (no valid number).");
        }
        text.append(" Track delivery in SMS Logs.");
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.INFORMATION));
        alert.setTitle("Send Message");
        alert.setHeaderText("Messages queued");
        alert.setContentText(text.toString());
        alert.showAndWait();
        messageTextArea.clear();
        handleClear();
    }

    private void showError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Send Message");
        alert.setHeaderText("Send Message");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static String statusText(OfficialSmsRecipient recipient) {
        return switch (recipient.status()) {
            case OK -> "OK";
            case MISSING -> "No mobile number";
            case INVALID -> "Invalid number";
        };
    }

    private record RecipientRow(OfficialSmsRecipient recipient, BooleanProperty selected) {
        private RecipientRow(OfficialSmsRecipient recipient) {
            this(recipient, new SimpleBooleanProperty(false));
        }
    }
}
