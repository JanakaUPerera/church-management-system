package com.churchmanagement.controller;

import com.churchmanagement.dto.finance.JournalEntrySummary;
import com.churchmanagement.dto.finance.JournalSearchCriteria;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.JournalService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.MoneyFormat;
import com.churchmanagement.util.ProcessingDialog;
import com.churchmanagement.util.TablePaginationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.Locale;

public class FinanceJournalController {
    private static final String ALL = "All";

    @FXML private DatePicker fromDatePicker;
    @FXML private DatePicker toDatePicker;
    @FXML private ComboBox<String> statusComboBox;
    @FXML private ComboBox<String> sourceComboBox;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Button newButton;
    @FXML private TableView<JournalEntrySummary> entryTable;
    @FXML private TableColumn<JournalEntrySummary, String> entryNoColumn;
    @FXML private TableColumn<JournalEntrySummary, String> dateColumn;
    @FXML private TableColumn<JournalEntrySummary, String> narrationColumn;
    @FXML private TableColumn<JournalEntrySummary, String> sourceColumn;
    @FXML private TableColumn<JournalEntrySummary, String> totalColumn;
    @FXML private TableColumn<JournalEntrySummary, String> statusColumn;
    @FXML private TableColumn<JournalEntrySummary, String> preparedByColumn;
    @FXML private TableColumn<JournalEntrySummary, String> postedByColumn;
    @FXML private ComboBox<Integer> itemsPerPageComboBox;
    @FXML private Label paginationSummaryLabel;
    @FXML private Pagination entryPagination;

    private final JournalService journalService = new JournalService();
    private final ObservableList<JournalEntrySummary> entries = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        boolean canEnter = AuthContext.getCurrentUser()
                .map(user -> new PermissionGuard(user).can("finance.entry")).orElse(false);
        newButton.setVisible(canEnter);
        newButton.setManaged(canEnter);
        ButtonIconUtil.applyIcon(newButton, "fas-plus");
        ButtonIconUtil.applyIcon(searchButton, "fas-search");
        LocalDate today = LocalDate.now();
        fromDatePicker.setValue(today.withDayOfMonth(1));
        toDatePicker.setValue(today);
        statusComboBox.getItems().setAll(ALL, "Draft", "Posted");
        statusComboBox.setValue(ALL);
        sourceComboBox.getItems().add(ALL);
        for (JournalSourceType type : JournalSourceType.values()) {
            sourceComboBox.getItems().add(label(type.name()));
        }
        sourceComboBox.setValue(ALL);
        configureTable();
        handleSearch();
    }

    @FXML
    private void handleSearch() {
        JournalSearchCriteria criteria = new JournalSearchCriteria(fromDatePicker.getValue(), toDatePicker.getValue(),
                switch (statusComboBox.getValue()) {
                    case "Draft" -> JournalStatus.DRAFT;
                    case "Posted" -> JournalStatus.POSTED;
                    default -> null;
                },
                ALL.equals(sourceComboBox.getValue()) ? null
                        : JournalSourceType.valueOf(sourceComboBox.getValue().toUpperCase(Locale.ROOT).replace(' ', '_')),
                searchField.getText());
        ProcessingDialog.run("General Journal", "Loading entries...",
                () -> journalService.search(criteria),
                results -> entries.setAll(results),
                throwable -> showError(throwable.getMessage()));
    }

    @FXML
    private void handleNew() {
        JournalEntryDialog.show(null, this::handleSearch);
    }

    private void open(JournalEntrySummary summary) {
        try {
            journalService.find(summary.id()).ifPresent(entry -> JournalEntryDialog.show(entry, this::handleSearch));
        } catch (RuntimeException exception) {
            showError(exception.getMessage());
        }
    }

    private void configureTable() {
        TablePaginationUtil.configure(entryTable, entries, entryPagination, itemsPerPageComboBox,
                paginationSummaryLabel, "entries");
        entryNoColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().entryNo() == null ? "Draft" : cell.getValue().entryNo()));
        dateColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().entryDate().toString()));
        narrationColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().narration()));
        sourceColumn.setCellValueFactory(cell -> new SimpleStringProperty(label(cell.getValue().sourceType().name())));
        totalColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyFormat.format(cell.getValue().total())));
        totalColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(label(cell.getValue().status().name())));
        preparedByColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().preparedByName()));
        postedByColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().postedByName() == null ? "" : cell.getValue().postedByName()));
        entryTable.setRowFactory(table -> {
            TableRow<JournalEntrySummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    open(row.getItem());
                }
            });
            return row;
        });
    }

    private static String label(String enumName) {
        String text = enumName.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void showError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("General Journal");
        alert.setHeaderText("General Journal");
        alert.setContentText(message == null ? "Unable to load entries." : message);
        alert.showAndWait();
    }
}
