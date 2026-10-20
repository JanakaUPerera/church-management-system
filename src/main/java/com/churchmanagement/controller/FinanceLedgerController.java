package com.churchmanagement.controller;

import com.churchmanagement.dto.finance.AccountLedger;
import com.churchmanagement.entity.Account;
import com.churchmanagement.service.AccountService;
import com.churchmanagement.service.JournalService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.ComboBoxUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.MoneyFormat;
import com.churchmanagement.util.ProcessingDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

public class FinanceLedgerController {
    @FXML private ComboBox<Account> accountComboBox;
    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private Button showButton;
    @FXML private Label openingLabel;
    @FXML private TableView<AccountLedger.Row> ledgerTable;
    @FXML private TableColumn<AccountLedger.Row, String> dateColumn;
    @FXML private TableColumn<AccountLedger.Row, String> entryNoColumn;
    @FXML private TableColumn<AccountLedger.Row, String> narrationColumn;
    @FXML private TableColumn<AccountLedger.Row, String> memoColumn;
    @FXML private TableColumn<AccountLedger.Row, String> categoryColumn;
    @FXML private TableColumn<AccountLedger.Row, String> branchColumn;
    @FXML private TableColumn<AccountLedger.Row, String> debitColumn;
    @FXML private TableColumn<AccountLedger.Row, String> creditColumn;
    @FXML private TableColumn<AccountLedger.Row, String> balanceColumn;
    @FXML private Label closingLabel;

    private final JournalService journalService = new JournalService();

    @FXML
    private void initialize() {
        List<Account> accounts = new AccountService().postingAccounts();
        accountComboBox.setItems(new FilteredList<>(FXCollections.observableArrayList(accounts), account -> true));
        ComboBoxUtil.makeSearchable(accountComboBox, Account::displayText);
        accountComboBox.setPromptText("Select account...");
        LocalDate today = LocalDate.now();
        fromDatePicker.setValue(LocalDate.of(today.getYear(), 1, 1));
        toDatePicker.setValue(today);
        ButtonIconUtil.applyIcon(showButton, "fas-search");
        configureTable();
    }

    @FXML
    private void handleShow() {
        Account account = accountComboBox.getValue();
        if (account == null) {
            showError("Select an account.");
            return;
        }
        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();
        ProcessingDialog.run("Account Ledger", "Loading ledger...",
                () -> journalService.ledger(account.id(), from, to),
                ledger -> {
                    openingLabel.setText("Opening balance " + MoneyFormat.format(ledger.openingBalance()));
                    ledgerTable.getItems().setAll(ledger.rows());
                    closingLabel.setText("Closing balance " + MoneyFormat.format(ledger.closingBalance()));
                },
                throwable -> showError(throwable.getMessage()));
    }

    private void configureTable() {
        text(dateColumn, row -> row.line().entryDate().toString());
        text(entryNoColumn, row -> row.line().entryNo());
        text(narrationColumn, row -> row.line().narration());
        text(memoColumn, row -> row.line().memo());
        text(categoryColumn, row -> row.line().categoryCode());
        text(branchColumn, row -> row.line().churchCode());
        money(debitColumn, row -> row.line().debit(), true);
        money(creditColumn, row -> row.line().credit(), true);
        money(balanceColumn, AccountLedger.Row::balance, false);
        ledgerTable.setRowFactory(table -> {
            TableRow<AccountLedger.Row> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    journalService.find(row.getItem().line().entryId())
                            .ifPresent(entry -> JournalEntryDialog.show(entry, this::handleShow));
                }
            });
            return row;
        });
    }

    private static void text(TableColumn<AccountLedger.Row, String> column, Function<AccountLedger.Row, String> value) {
        column.setCellValueFactory(cell -> new SimpleStringProperty(
                value.apply(cell.getValue()) == null ? "" : value.apply(cell.getValue())));
    }

    /** Right-aligned amounts; {@code blankZero} leaves the unused debit or credit side empty. */
    private static void money(TableColumn<AccountLedger.Row, String> column,
                              Function<AccountLedger.Row, BigDecimal> value, boolean blankZero) {
        column.setCellValueFactory(cell -> {
            BigDecimal amount = value.apply(cell.getValue());
            boolean blank = amount == null || (blankZero && amount.signum() == 0);
            return new SimpleStringProperty(blank ? "" : MoneyFormat.format(amount));
        });
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
    }

    private void showError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Account Ledger");
        alert.setHeaderText("Account Ledger");
        alert.setContentText(message == null ? "Unable to load the ledger." : message);
        alert.showAndWait();
    }
}
