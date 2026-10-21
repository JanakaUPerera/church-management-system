package com.churchmanagement.controller;

import com.churchmanagement.dto.finance.JournalEntryRequest;
import com.churchmanagement.dto.finance.JournalLineRequest;
import com.churchmanagement.entity.Account;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.entity.JournalLine;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.AccountService;
import com.churchmanagement.service.CollectionCategoryService;
import com.churchmanagement.service.FinanceSettings;
import com.churchmanagement.service.JournalService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.ComboBoxUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.MoneyFormat;
import com.churchmanagement.util.StatusBadge;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Create, edit, post, delete and reverse a journal entry. Posted entries open read-only. */
public final class JournalEntryDialog {
    private static final double DIALOG_WIDTH = 1240;
    private static final double ACCOUNT_WIDTH = 300;
    private static final double AMOUNT_WIDTH = 130;
    private static final double CATEGORY_WIDTH = 150;
    private static final double BRANCH_WIDTH = 210;
    private static final double MEMO_WIDTH = 200;

    private final JournalService journalService = new JournalService();
    private final List<Account> accounts;
    private final Map<Long, Account> accountsById;
    private final List<CollectionCategory> categories;
    private final List<Church> churches;
    private final List<LineRow> rows = new ArrayList<>();
    private final VBox linesBox = new VBox(6);
    private final Label totalsLabel = new Label();
    private final Label balanceBadge = StatusBadge.create();
    private final Runnable onChanged;
    private JournalEntry entry;
    private boolean readOnly;
    private DatePicker datePicker;
    private TextField narrationField;

    private JournalEntryDialog(JournalEntry entry, Runnable onChanged) {
        this.entry = entry;
        this.onChanged = onChanged;
        this.accounts = new AccountService().postingAccounts();
        this.accountsById = accounts.stream().collect(Collectors.toMap(Account::id, Function.identity()));
        this.categories = new CollectionCategoryService().findActive();
        this.churches = new ChurchRepository().findOperationalChurches();
        this.readOnly = entry != null && (entry.isPosted() || (entry.sourceType() != JournalSourceType.JOURNAL));
    }

    public static void show(JournalEntry entry, Runnable onChanged) {
        new JournalEntryDialog(entry, onChanged).open();
    }

    private void open() {
        Dialog<ButtonType> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle("Journal entry");
        dialog.setHeaderText(entry == null ? "New journal entry"
                : entry.isPosted() ? entry.entryNo() + " (posted)" : "Draft journal entry");

        datePicker = new DatePicker(entry == null ? LocalDate.now() : entry.entryDate());
        narrationField = new TextField(entry == null ? "" : entry.narration());
        narrationField.setPrefColumnCount(50);
        GridPane header = new GridPane();
        header.setHgap(12);
        header.setVgap(8);
        header.add(DialogStyler.fieldLabel("Date"), 0, 0);
        header.add(datePicker, 1, 0);
        header.add(DialogStyler.fieldLabel("Narration"), 0, 1);
        header.add(narrationField, 1, 1);

        HBox columnHeader = new HBox(6, fixed("Account", ACCOUNT_WIDTH), fixed("Debit", AMOUNT_WIDTH),
                fixed("Credit", AMOUNT_WIDTH), fixed("Category", CATEGORY_WIDTH), fixed("Branch", BRANCH_WIDTH),
                fixed("Memo", MEMO_WIDTH));
        columnHeader.getStyleClass().add("field-label");
        if (entry == null) {
            addRow(null);
            addRow(null);
        } else {
            entry.lines().forEach(this::addRow);
        }
        Button addLineButton = new Button("Add line");
        ButtonIconUtil.applyIcon(addLineButton, "fas-plus");
        addLineButton.setOnAction(event -> addRow(null));
        // Directly under the last line, right-aligned under the remove buttons.
        Region addLineSpacer = new Region();
        HBox.setHgrow(addLineSpacer, Priority.ALWAYS);
        HBox addLineRow = new HBox(addLineSpacer, addLineButton);
        addLineRow.setAlignment(Pos.CENTER_RIGHT);
        addLineRow.setVisible(!readOnly);
        addLineRow.setManaged(!readOnly);

        ScrollPane linesScroll = new ScrollPane(new VBox(8, linesBox, addLineRow));
        linesScroll.setFitToWidth(true);
        linesScroll.setPrefViewportHeight(280);
        VBox content = new VBox(12, header, columnHeader, linesScroll, totalsRow());
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(DIALOG_WIDTH);

        ButtonType saveType = new ButtonType("Save draft", ButtonBar.ButtonData.OTHER);
        ButtonType postType = new ButtonType("Post", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteType = new ButtonType("Delete draft", ButtonBar.ButtonData.OTHER);
        ButtonType reverseType = new ButtonType("Reverse…", ButtonBar.ButtonData.OTHER);
        boolean canEnter = AuthContext.getCurrentUser()
                .map(user -> new PermissionGuard(user).can("finance.entry")).orElse(false);
        // Added in display order; the button bar keeps that order, right-aligned.
        if (entry != null && !entry.isPosted() && !readOnly && canEnter) {
            dialog.getDialogPane().getButtonTypes().add(deleteType);
        }
        if (!readOnly && canEnter) {
            dialog.getDialogPane().getButtonTypes().add(saveType);
        }
        boolean approvalRequired = new FinanceSettings().approvalRequired();
        if (!readOnly && (entry == null ? canEnter && !approvalRequired : journalService.canPost(entry))) {
            dialog.getDialogPane().getButtonTypes().add(postType);
        }
        if (entry != null && journalService.canReverse(entry)) {
            dialog.getDialogPane().getButtonTypes().add(reverseType);
        }
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        keepButtonsInAddedOrder(dialog);
        dialog.setOnShowing(event -> keepButtonsInAddedOrder(dialog));
        icon(dialog, deleteType, "fas-trash-alt");
        icon(dialog, saveType, "fas-save");
        icon(dialog, postType, "fas-check");
        icon(dialog, reverseType, "fas-undo");
        icon(dialog, ButtonType.CLOSE, "fas-times");
        setEditable(!readOnly);

        action(dialog, saveType, () -> {
            entry = journalService.saveDraft(request());
            dialog.setHeaderText("Draft journal entry (saved)");
        });
        action(dialog, postType, () -> {
            if (canEnter) {
                entry = journalService.saveDraft(request()); // keep the approver's own edits, if any
            }
            entry = journalService.post(entry.id());
            info("Posted as " + entry.entryNo() + ".");
            dialog.close();
        });
        action(dialog, deleteType, () -> {
            journalService.deleteDraft(entry.id());
            dialog.close();
        });
        action(dialog, reverseType, () -> {
            TextInputDialog reasonDialog = DialogStyler.apply(new TextInputDialog());
            reasonDialog.setTitle("Reverse " + entry.entryNo());
            reasonDialog.setHeaderText("The reversal is posted today with debits and credits swapped.");
            reasonDialog.setContentText("Reason:");
            reasonDialog.showAndWait().ifPresent(reason -> {
                JournalEntry reversal = journalService.reverse(entry.id(), LocalDate.now(), reason);
                info("Reversed by " + reversal.entryNo() + ".");
                dialog.close();
            });
        });
        updateTotals();
        dialog.showAndWait();
    }

    /** Right-aligned buttons in the order they were added, instead of the platform's grouping. */
    private static void keepButtonsInAddedOrder(Dialog<ButtonType> dialog) {
        if (dialog.getDialogPane().lookup(".button-bar") instanceof ButtonBar buttonBar) {
            buttonBar.setButtonOrder(ButtonBar.BUTTON_ORDER_NONE);
        }
    }

    private static void icon(Dialog<ButtonType> dialog, ButtonType type, String iconLiteral) {
        if (dialog.getDialogPane().lookupButton(type) instanceof Button button) {
            ButtonIconUtil.applyIcon(button, iconLiteral);
        }
    }

    private void action(Dialog<ButtonType> dialog, ButtonType type, Runnable work) {
        Button button = (Button) dialog.getDialogPane().lookupButton(type);
        if (button == null) {
            return;
        }
        button.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            try {
                work.run();
                onChanged.run();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                error(exception.getMessage());
            } catch (RuntimeException exception) {
                error(exception.getMessage() == null ? "Action failed. Please try again." : exception.getMessage());
            }
        });
    }

    private JournalEntryRequest request() {
        List<JournalLineRequest> lines = new ArrayList<>();
        for (LineRow row : rows) {
            if (row.isBlank()) {
                continue;
            }
            lines.add(new JournalLineRequest(
                    row.account.getValue() == null ? null : row.account.getValue().id(),
                    MoneyFormat.parse(row.debit.getText()), MoneyFormat.parse(row.credit.getText()),
                    row.category.getValue() == null ? null : row.category.getValue().getId(),
                    row.branch.getValue() == null ? null : row.branch.getValue().getId(),
                    row.memo.getText()));
        }
        return new JournalEntryRequest(entry == null ? null : entry.id(), JournalSourceType.JOURNAL,
                datePicker.getValue(), narrationField.getText(), lines);
    }

    private void addRow(JournalLine line) {
        LineRow row = new LineRow(line);
        rows.add(row);
        linesBox.getChildren().add(row.node);
        row.setEditable(!readOnly);
        updateTotals();
    }

    private void setEditable(boolean editable) {
        datePicker.setDisable(!editable);
        narrationField.setEditable(editable);
        rows.forEach(row -> row.setEditable(editable));
    }

    private void updateTotals() {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (LineRow row : rows) {
            debit = debit.add(row.amount(row.debit));
            credit = credit.add(row.amount(row.credit));
        }
        BigDecimal difference = debit.subtract(credit);
        totalsLabel.setText("Debits " + MoneyFormat.format(debit) + "   Credits " + MoneyFormat.format(credit));
        if (difference.signum() == 0) {
            StatusBadge.apply(balanceBadge, "Balanced", StatusBadge.Tone.SUCCESS);
        } else {
            StatusBadge.apply(balanceBadge, "Out of balance by " + MoneyFormat.format(difference.abs()),
                    StatusBadge.Tone.DANGER);
        }
    }

    private HBox totalsRow() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, spacer, totalsLabel, balanceBadge);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label fixed(String text, double width) {
        Label label = new Label(text);
        label.setPrefWidth(width);
        return label;
    }

    private void info(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.INFORMATION));
        alert.setTitle("Journal entry");
        alert.setHeaderText("Journal entry");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void error(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Journal entry");
        alert.setHeaderText("Journal entry");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** One editable line: account, debit, credit, category, branch, memo, remove. */
    private final class LineRow {
        private final ComboBox<Account> account = new ComboBox<>();
        private final TextField debit = new TextField();
        private final TextField credit = new TextField();
        private final ComboBox<CollectionCategory> category = new ComboBox<>(FXCollections.observableArrayList(categories));
        private final ComboBox<Church> branch = new ComboBox<>();
        private final TextField memo = new TextField();
        private final Button remove = new Button();
        private final HBox node;

        private LineRow(JournalLine line) {
            account.setItems(new FilteredList<>(FXCollections.observableArrayList(accounts), item -> true));
            ComboBoxUtil.makeSearchable(account, Account::displayText);
            account.setPrefWidth(ACCOUNT_WIDTH);
            ComboBoxUtil.makeChurchSearchable(branch, FXCollections.observableArrayList(churches));
            branch.setPromptText("(none)");
            branch.setPrefWidth(BRANCH_WIDTH);
            category.setPrefWidth(CATEGORY_WIDTH);
            category.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(CollectionCategory value) {
                    return value == null ? "" : value.displayText();
                }

                @Override
                public CollectionCategory fromString(String text) {
                    return null;
                }
            });
            debit.setPrefWidth(AMOUNT_WIDTH);
            credit.setPrefWidth(AMOUNT_WIDTH);
            debit.setAlignment(Pos.CENTER_RIGHT);
            credit.setAlignment(Pos.CENTER_RIGHT);
            memo.setPrefWidth(MEMO_WIDTH);
            HBox.setHgrow(memo, Priority.ALWAYS);
            if (line != null) {
                account.setValue(accountsById.get(line.accountId()));
                debit.setText(line.debit().signum() > 0 ? MoneyFormat.format(line.debit()) : "");
                credit.setText(line.credit().signum() > 0 ? MoneyFormat.format(line.credit()) : "");
                categories.stream().filter(item -> item.getId().equals(line.categoryId())).findFirst()
                        .ifPresent(category::setValue);
                churches.stream().filter(item -> item.getId().equals(line.churchId())).findFirst()
                        .ifPresent(branch::setValue);
                memo.setText(line.memo() == null ? "" : line.memo());
            }
            account.valueProperty().addListener((observable, oldValue, newValue) -> refreshCategory());
            debit.textProperty().addListener((observable, oldValue, newValue) -> updateTotals());
            credit.textProperty().addListener((observable, oldValue, newValue) -> updateTotals());
            node = new HBox(6, account, debit, credit, category, branch, memo, remove);
            node.setAlignment(Pos.CENTER_LEFT);
            ButtonIconUtil.applyTableActionIcon(remove, "fas-times", "Remove line");
            remove.setMinWidth(34);
            remove.getStyleClass().add("table-action-button");
            remove.setOnAction(event -> {
                rows.remove(this);
                linesBox.getChildren().remove(node);
                updateTotals();
            });
            refreshCategory();
        }

        private void refreshCategory() {
            boolean required = account.getValue() != null && account.getValue().type().requiresCategory();
            category.setPromptText(required ? "Required" : "(none)");
        }

        private BigDecimal amount(TextField field) {
            try {
                BigDecimal value = MoneyFormat.parse(field.getText());
                return value == null ? BigDecimal.ZERO : value;
            } catch (IllegalArgumentException exception) {
                return BigDecimal.ZERO;
            }
        }

        private boolean isBlank() {
            return account.getValue() == null && debit.getText().isBlank() && credit.getText().isBlank();
        }

        private void setEditable(boolean editable) {
            account.setDisable(!editable);
            category.setDisable(!editable);
            branch.setDisable(!editable);
            debit.setEditable(editable);
            credit.setEditable(editable);
            memo.setEditable(editable);
            remove.setVisible(editable);
        }
    }
}
