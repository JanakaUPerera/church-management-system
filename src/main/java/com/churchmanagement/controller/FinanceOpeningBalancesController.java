package com.churchmanagement.controller;

import com.churchmanagement.dto.finance.OpeningBalanceForm;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.JournalService;
import com.churchmanagement.service.OpeningBalanceService;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.MoneyFormat;
import com.churchmanagement.util.ProcessingDialog;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class FinanceOpeningBalancesController {
    @FXML private Label statusLabel;
    @FXML private GridPane accountGrid;
    @FXML private GridPane fundGrid;
    @FXML private Label totalsLabel;
    @FXML private Button saveButton;
    @FXML private Button postButton;
    @FXML private Button reverseButton;

    private final OpeningBalanceService openingBalanceService = new OpeningBalanceService();
    private final JournalService journalService = new JournalService();
    private final Map<Account, TextField> accountFields = new LinkedHashMap<>();
    private final Map<Long, TextField> fundFields = new LinkedHashMap<>();
    private OpeningBalanceForm form;
    private boolean canEnter;

    @FXML
    private void initialize() {
        canEnter = AuthContext.getCurrentUser()
                .map(user -> new PermissionGuard(user).can("finance.entry")).orElse(false);
        load();
    }

    @FXML
    private void handleSave() {
        run("Saving opening balances...", () -> openingBalanceService.saveDraft(accountAmounts(), fundAmounts()));
    }

    @FXML
    private void handlePost() {
        run("Posting opening balances...", () -> {
            if (canEnter) {
                openingBalanceService.saveDraft(accountAmounts(), fundAmounts());
            }
            openingBalanceService.post();
        });
    }

    @FXML
    private void handleReverse() {
        JournalEntry entry = form.entry();
        TextInputDialog reasonDialog = DialogStyler.apply(new TextInputDialog());
        reasonDialog.setTitle("Reverse opening balances");
        reasonDialog.setHeaderText("This posts a reversal of " + entry.entryNo()
                + " so the opening balances can be entered again.");
        reasonDialog.setContentText("Reason:");
        reasonDialog.showAndWait().ifPresent(reason ->
                run("Reversing opening balances...", () -> journalService.reverse(entry.id(), entry.entryDate(), reason)));
    }

    private void load() {
        ProcessingDialog.run("Opening Balances", "Loading opening balances...",
                openingBalanceService::load,
                this::show,
                throwable -> showError(throwable.getMessage()));
    }

    private void show(OpeningBalanceForm loaded) {
        form = loaded;
        JournalEntry entry = loaded.entry();
        boolean posted = entry != null && entry.isPosted();
        statusLabel.setText("Go-live date " + loaded.goLiveDate() + " — "
                + (entry == null ? "Not entered" : posted ? "Posted " + entry.entryNo() : "Draft"));

        accountFields.clear();
        accountGrid.getChildren().clear();
        int row = 0;
        for (Account account : loaded.balanceAccounts()) {
            TextField field = amountField(loaded.accountAmounts().get(account.id()), posted);
            accountFields.put(account, field);
            accountGrid.add(new Label(account.displayText()), 0, row);
            accountGrid.add(field, 1, row++);
        }
        fundFields.clear();
        fundGrid.getChildren().clear();
        row = 0;
        for (CollectionCategory category : loaded.categories()) {
            TextField field = amountField(loaded.fundAmounts().get(category.getId()), posted);
            fundFields.put(category.getId(), field);
            fundGrid.add(new Label(category.displayText()), 0, row);
            fundGrid.add(field, 1, row++);
        }

        saveButton.setDisable(posted || !canEnter);
        postButton.setDisable(posted || !(entry == null ? canEnter : journalService.canPost(entry)));
        reverseButton.setVisible(posted && journalService.canReverse(entry));
        reverseButton.setManaged(reverseButton.isVisible());
        updateTotals();
    }

    private TextField amountField(BigDecimal amount, boolean readOnly) {
        TextField field = new TextField(amount == null || amount.signum() == 0 ? "" : MoneyFormat.format(amount));
        field.setPrefWidth(150);
        field.setAlignment(Pos.CENTER_RIGHT);
        field.setEditable(!readOnly);
        field.textProperty().addListener((observable, oldValue, newValue) -> updateTotals());
        return field;
    }

    private void updateTotals() {
        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilities = BigDecimal.ZERO;
        for (Map.Entry<Account, TextField> entry : accountFields.entrySet()) {
            BigDecimal amount = amount(entry.getValue());
            if (entry.getKey().type() == AccountType.ASSET) {
                assets = assets.add(amount);
            } else {
                liabilities = liabilities.add(amount);
            }
        }
        BigDecimal funds = fundFields.values().stream().map(this::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal difference = assets.subtract(liabilities).subtract(funds);
        totalsLabel.getStyleClass().remove("error-label");
        totalsLabel.setText("Assets " + MoneyFormat.format(assets) + "   Liabilities " + MoneyFormat.format(liabilities)
                + "   Funds " + MoneyFormat.format(funds)
                + (difference.signum() == 0 ? "   Balanced" : "   Difference " + MoneyFormat.format(difference)));
        if (difference.signum() != 0) {
            totalsLabel.getStyleClass().add("error-label");
        }
    }

    private BigDecimal amount(TextField field) {
        try {
            BigDecimal value = MoneyFormat.parse(field.getText());
            return value == null ? BigDecimal.ZERO : value;
        } catch (IllegalArgumentException exception) {
            return BigDecimal.ZERO;
        }
    }

    private Map<Long, BigDecimal> accountAmounts() {
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        accountFields.forEach((account, field) -> amounts.put(account.id(), MoneyFormat.parse(field.getText())));
        return amounts;
    }

    private Map<Long, BigDecimal> fundAmounts() {
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        fundFields.forEach((categoryId, field) -> amounts.put(categoryId, MoneyFormat.parse(field.getText())));
        return amounts;
    }

    private void run(String message, Runnable action) {
        ProcessingDialog.run("Opening Balances", message, action::run, this::load,
                throwable -> showError(throwable.getMessage()));
    }

    private void showError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Opening Balances");
        alert.setHeaderText("Opening Balances");
        alert.setContentText(message == null ? "Action failed. Please try again." : message);
        alert.showAndWait();
    }
}
