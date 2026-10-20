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
import com.churchmanagement.util.ComboBoxUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.MoneyFormat;
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
    private final JournalService journalService = new JournalService();
    private final List<Account> accounts;
    private final Map<Long, Account> accountsById;
    private final List<CollectionCategory> categories;
    private final List<Church> churches;
    private final List<LineRow> rows = new ArrayList<>();
    private final VBox linesBox = new VBox(6);
    private final Label totalsLabel = new Label();
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

        HBox columnHeader = new HBox(6, fixed("Account", 260), fixed("Debit", 110), fixed("Credit", 110),
                fixed("Category", 130), fixed("Branch", 170), fixed("Memo", 170));
        columnHeader.getStyleClass().add("field-label");
        if (entry == null) {
            addRow(null);
            addRow(null);
        } else {
            entry.lines().forEach(this::addRow);
        }
        Button addLineButton = new Button("Add line");
        addLineButton.setOnAction(event -> addRow(null));
        addLineButton.setVisible(!readOnly);
        addLineButton.setManaged(!readOnly);

        ScrollPane linesScroll = new ScrollPane(linesBox);
        linesScroll.setFitToWidth(true);
        linesScroll.setPrefViewportHeight(280);
        VBox content = new VBox(12, header, columnHeader, linesScroll, new HBox(12, addLineButton, totalsLabel));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(1040);

        ButtonType saveType = new ButtonType("Save draft", ButtonBar.ButtonData.LEFT);
        ButtonType postType = new ButtonType("Post", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteType = new ButtonType("Delete draft", ButtonBar.ButtonData.LEFT);
        ButtonType reverseType = new ButtonType("Reverse…", ButtonBar.ButtonData.OTHER);
        boolean canEnter = AuthContext.getCurrentUser()
                .map(user -> new PermissionGuard(user).can("finance.entry")).orElse(false);
        if (!readOnly && canEnter) {
            dialog.getDialogPane().getButtonTypes().add(saveType);
        }
        if (entry != null && !entry.isPosted() && !readOnly && canEnter) {
            dialog.getDialogPane().getButtonTypes().add(deleteType);
        }
        boolean approvalRequired = new FinanceSettings().approvalRequired();
        if (!readOnly && (entry == null ? canEnter && !approvalRequired : journalService.canPost(entry))) {
            dialog.getDialogPane().getButtonTypes().add(postType);
        }
        if (entry != null && journalService.canReverse(entry)) {
            dialog.getDialogPane().getButtonTypes().add(reverseType);
        }
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
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
        totalsLabel.getStyleClass().remove("error-label");
        if (difference.signum() == 0) {
            totalsLabel.setText("Debits " + MoneyFormat.format(debit) + "   Credits " + MoneyFormat.format(credit)
                    + "   Balanced");
        } else {
            totalsLabel.setText("Debits " + MoneyFormat.format(debit) + "   Credits " + MoneyFormat.format(credit)
                    + "   Out of balance by " + MoneyFormat.format(difference.abs()));
            totalsLabel.getStyleClass().add("error-label");
        }
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
        private final Button remove = new Button("✕");
        private final HBox node;

        private LineRow(JournalLine line) {
            account.setItems(new FilteredList<>(FXCollections.observableArrayList(accounts), item -> true));
            ComboBoxUtil.makeSearchable(account, Account::displayText);
            account.setPrefWidth(260);
            ComboBoxUtil.makeChurchSearchable(branch, FXCollections.observableArrayList(churches));
            branch.setPromptText("(none)");
            branch.setPrefWidth(170);
            category.setPrefWidth(130);
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
            debit.setPrefWidth(110);
            credit.setPrefWidth(110);
            debit.setAlignment(Pos.CENTER_RIGHT);
            credit.setAlignment(Pos.CENTER_RIGHT);
            memo.setPrefWidth(170);
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
