package com.churchmanagement.controller;

import com.churchmanagement.dto.finance.AccountNode;
import com.churchmanagement.dto.finance.AccountRequest;
import com.churchmanagement.entity.Account;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.AccountService;
import com.churchmanagement.service.FinanceException;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.MoneyFormat;
import com.churchmanagement.util.ProcessingDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.GridPane;

import java.util.List;

public class FinanceAccountsController {
    @FXML private CheckBox showInactiveCheckBox;
    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button statusButton;
    @FXML private Button deleteButton;
    @FXML private Button refreshButton;
    @FXML private TreeTableView<AccountNode> accountTree;
    @FXML private TreeTableColumn<AccountNode, String> codeColumn;
    @FXML private TreeTableColumn<AccountNode, String> nameColumn;
    @FXML private TreeTableColumn<AccountNode, String> typeColumn;
    @FXML private TreeTableColumn<AccountNode, String> cashFlowColumn;
    @FXML private TreeTableColumn<AccountNode, String> balanceColumn;
    @FXML private TreeTableColumn<AccountNode, String> statusColumn;

    private final AccountService accountService = new AccountService();
    private boolean canManage;

    @FXML
    private void initialize() {
        canManage = AuthContext.getCurrentUser()
                .map(user -> new PermissionGuard(user).can("finance.accounts.manage"))
                .orElse(false);
        for (Button button : List.of(addButton, editButton, statusButton, deleteButton)) {
            button.setVisible(canManage);
            button.setManaged(canManage);
        }
        ButtonIconUtil.applyIcon(addButton, "fas-plus");
        ButtonIconUtil.applyIcon(refreshButton, "fas-sync-alt");
        configureColumns();
        accountTree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
                updateButtons());
        accountTree.setRowFactory(tree -> {
            TreeTableRow<AccountNode> row = new TreeTableRow<>() {
                @Override
                protected void updateItem(AccountNode item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("muted-label", "account-group-row");
                    if (!empty && item != null) {
                        if (item.account().group()) {
                            getStyleClass().add("account-group-row");
                        }
                        if (!item.account().isActive()) {
                            getStyleClass().add("muted-label");
                        }
                    }
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty() && canManage) {
                    handleEdit();
                }
            });
            return row;
        });
        handleRefresh();
    }

    @FXML
    private void handleRefresh() {
        boolean includeInactive = showInactiveCheckBox.isSelected();
        ProcessingDialog.run("Chart of Accounts", "Loading accounts...",
                () -> accountService.loadChart(includeInactive),
                nodes -> {
                    TreeItem<AccountNode> root = new TreeItem<>();
                    nodes.forEach(node -> root.getChildren().add(treeItem(node)));
                    accountTree.setRoot(root);
                    updateButtons();
                },
                throwable -> showError(throwable.getMessage()));
    }

    /** Opens a blank form; choosing a group fills in the next free code. */
    @FXML
    private void handleAdd() {
        showAccountDialog(null, null);
    }

    @FXML
    private void handleEdit() {
        AccountNode selected = selectedNode();
        if (selected != null) {
            showAccountDialog(selected.account(), findParent(selected));
        }
    }

    @FXML
    private void handleToggleStatus() {
        AccountNode selected = selectedNode();
        if (selected == null) {
            return;
        }
        Account account = selected.account();
        run(account.isActive() ? "Deactivating account..." : "Activating account...", () -> {
            if (account.isActive()) {
                accountService.deactivate(account.id());
            } else {
                accountService.activate(account.id());
            }
        });
    }

    @FXML
    private void handleDelete() {
        AccountNode selected = selectedNode();
        if (selected == null) {
            return;
        }
        Alert confirm = DialogStyler.apply(new Alert(Alert.AlertType.CONFIRMATION));
        confirm.setTitle("Delete account");
        confirm.setHeaderText("Delete " + selected.account().displayText() + "?");
        confirm.setContentText("Only accounts that have never been used can be deleted.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            run("Deleting account...", () -> accountService.delete(selected.account().id()));
        }
    }

    private void showAccountDialog(Account existing, Account parent) {
        boolean editing = existing != null;
        Dialog<ButtonType> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle(editing ? "Edit account" : "Add account");
        dialog.setHeaderText(editing ? existing.displayText() : "New account");
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        ComboBox<Account> groupCombo = new ComboBox<>();
        groupCombo.getItems().setAll(accountService.groups());
        groupCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Account account) {
                return account == null ? "" : account.displayText() + "  (" + AccountService.rangeText(account) + ")";
            }

            @Override
            public Account fromString(String text) {
                return null;
            }
        });
        groupCombo.setMaxWidth(Double.MAX_VALUE);
        if (parent != null) {
            groupCombo.getItems().stream().filter(group -> group.id().equals(parent.id())).findFirst()
                    .ifPresent(groupCombo::setValue);
        }
        CheckBox groupCheckBox = new CheckBox("This is a group (it holds other accounts)");
        TextField codeField = new TextField(editing ? existing.code() : "");
        codeField.setPromptText("4 digits, e.g. 1201");
        TextField nameField = new TextField(editing ? existing.name() : "");
        ComboBox<CashFlowOption> cashFlowCombo = new ComboBox<>();
        cashFlowCombo.setMaxWidth(Double.MAX_VALUE);
        Label cashFlowHint = new Label("Used by the Cash Flow Statement to classify money moving through this account.");
        cashFlowHint.getStyleClass().add("muted-label");
        cashFlowHint.setWrapText(true);
        cashFlowHint.setMaxWidth(380);
        TextField descriptionField = new TextField(editing && existing.description() != null ? existing.description() : "");

        if (editing) {
            groupCheckBox.setSelected(existing.group());
            groupCheckBox.setDisable(true);
            if (existing.isTopGroup()) {
                groupCombo.setDisable(true);
                codeField.setDisable(true);
            }
        }

        Runnable refreshCashFlow = () -> {
            CashFlowOption current = cashFlowCombo.getValue();
            Account group = groupCombo.getValue();
            CashFlowClass inherited = group == null ? null : group.cashFlowClass();
            List<CashFlowOption> options = new java.util.ArrayList<>();
            options.add(new CashFlowOption(null, inherited == null
                    ? "Same as group" : "Same as group (" + label(inherited.name()) + ")"));
            for (CashFlowClass value : CashFlowClass.values()) {
                options.add(new CashFlowOption(value, label(value.name())));
            }
            cashFlowCombo.getItems().setAll(options);
            CashFlowClass wanted = current != null ? current.value()
                    : editing && (inherited == null || existing.cashFlowClass() != inherited) ? existing.cashFlowClass()
                    : null;
            cashFlowCombo.setValue(options.stream().filter(option -> option.value() == wanted).findFirst()
                    .orElse(options.getFirst()));
        };
        refreshCashFlow.run();
        groupCombo.valueProperty().addListener((observable, oldValue, newValue) -> refreshCashFlow.run());

        if (!editing) {
            // Suggest the next free code; a code the user typed is never overwritten.
            String[] lastSuggestion = {""};
            Runnable suggestCode = () -> {
                Account group = groupCombo.getValue();
                String current = codeField.getText() == null ? "" : codeField.getText().strip();
                if (group == null || !(current.isEmpty() || current.equals(lastSuggestion[0]))) {
                    return;
                }
                String suggestion = accountService.suggestNextCode(group.id(), groupCheckBox.isSelected());
                codeField.setText(suggestion);
                lastSuggestion[0] = suggestion;
            };
            groupCombo.valueProperty().addListener((observable, oldValue, newValue) -> suggestCode.run());
            groupCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> suggestCode.run());
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setAlignment(Pos.CENTER_LEFT);
        grid.add(DialogStyler.fieldLabel("Group"), 0, 0);
        grid.add(groupCombo, 1, 0);
        grid.add(groupCheckBox, 1, 1);
        grid.add(DialogStyler.fieldLabel("Code"), 0, 2);
        grid.add(codeField, 1, 2);
        grid.add(DialogStyler.fieldLabel("Name"), 0, 3);
        grid.add(nameField, 1, 3);
        grid.add(DialogStyler.fieldLabel("Cash flow"), 0, 4);
        grid.add(cashFlowCombo, 1, 4);
        grid.add(cashFlowHint, 1, 5);
        grid.add(DialogStyler.fieldLabel("Description"), 0, 6);
        grid.add(descriptionField, 1, 6);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(560);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            Account group = groupCombo.getValue();
            CashFlowOption cashFlow = cashFlowCombo.getValue();
            // "Same as group" resolves to the group's class (a top group keeps its own class when edited).
            CashFlowClass cashFlowClass = cashFlow != null && cashFlow.value() != null ? cashFlow.value()
                    : group != null ? group.cashFlowClass()
                    : editing ? existing.cashFlowClass() : null;
            AccountRequest request = new AccountRequest(codeField.getText(), nameField.getText(),
                    group == null ? null : group.id(), groupCheckBox.isSelected(), cashFlowClass,
                    descriptionField.getText());
            try {
                if (editing) {
                    accountService.update(existing.id(), request);
                } else {
                    accountService.create(request);
                }
                dialog.close();
                handleRefresh();
            } catch (FinanceException exception) {
                showError(exception.getMessage());
            }
        });
        dialog.showAndWait();
    }

    private void configureColumns() {
        codeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getValue().account().code()));
        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getValue().account().name()));
        typeColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getValue().account().group() ? "Group" : label(cell.getValue().getValue().account().type().name())));
        cashFlowColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                label(cell.getValue().getValue().account().cashFlowClass().name())));
        balanceColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                MoneyFormat.format(cell.getValue().getValue().balance())));
        balanceColumn.setCellFactory(column -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getValue().account().isActive() ? "Active" : "Inactive"));
    }

    private TreeItem<AccountNode> treeItem(AccountNode node) {
        TreeItem<AccountNode> item = new TreeItem<>(node);
        item.setExpanded(true);
        node.children().forEach(child -> item.getChildren().add(treeItem(child)));
        return item;
    }

    private void updateButtons() {
        AccountNode selected = selectedNode();
        boolean none = selected == null;
        editButton.setDisable(none);
        deleteButton.setDisable(none || selected.account().isSystem() || selected.account().isTopGroup());
        statusButton.setDisable(none || selected.account().isSystem() || selected.account().isTopGroup());
        statusButton.setText(!none && !selected.account().isActive() ? "Activate" : "Deactivate");
    }

    private AccountNode selectedNode() {
        TreeItem<AccountNode> item = accountTree.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    private Account findParent(AccountNode node) {
        TreeItem<AccountNode> item = accountTree.getSelectionModel().getSelectedItem();
        if (item == null || item.getParent() == null || item.getParent().getValue() == null) {
            return null;
        }
        return item.getParent().getValue().account();
    }

    private void run(String message, Runnable action) {
        ProcessingDialog.run("Chart of Accounts", message, action::run, this::handleRefresh,
                throwable -> showError(throwable.getMessage()));
    }

    /** A cash-flow choice; a null value means "same as the group". */
    private record CashFlowOption(CashFlowClass value, String text) {
        @Override
        public String toString() {
            return text;
        }
    }

    private static String label(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private void showError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Chart of Accounts");
        alert.setHeaderText("Chart of Accounts");
        alert.setContentText(message == null ? "Action failed. Please try again." : message);
        alert.showAndWait();
    }
}
