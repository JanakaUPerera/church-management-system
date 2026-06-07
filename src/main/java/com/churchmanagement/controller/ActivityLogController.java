package com.churchmanagement.controller;

import com.churchmanagement.dto.ActivityLogDto;
import com.churchmanagement.dto.ActivityLogSearchCriteria;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.UserRepository;
import com.churchmanagement.service.ActivityLogQueryService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.ComboBoxUtil;
import com.churchmanagement.util.DatePickerUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.SystemDateTimeFormatter;
import com.churchmanagement.util.TablePaginationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ActivityLogController {
    private static final String ALL_ACTIONS = "ALL ACTIONS";
    private static final String ALL_MODULES = "ALL MODULES";

    private final ActivityLogQueryService activityLogQueryService = new ActivityLogQueryService();
    private final SystemDateTimeFormatter dateTimeFormatter = new SystemDateTimeFormatter();
    private final UserRepository userRepository = new UserRepository();
    private final ObservableList<ActivityLogDto> activityLogs = FXCollections.observableArrayList();
    private final ObservableList<UserOption> users = FXCollections.observableArrayList();

    @FXML private DatePicker dateFromPicker;
    @FXML private DatePicker dateToPicker;
    @FXML private ComboBox<UserOption> userComboBox;
    @FXML private ComboBox<String> actionComboBox;
    @FXML private ComboBox<String> moduleComboBox;
    @FXML private TextField keywordField;
    @FXML private Button searchButton;
    @FXML private Button clearButton;
    @FXML private Button viewDetailsButton;
    @FXML private Button refreshButton;
    @FXML private TableView<ActivityLogDto> activityLogTable;
    @FXML private TableColumn<ActivityLogDto, String> dateTimeColumn;
    @FXML private TableColumn<ActivityLogDto, String> userColumn;
    @FXML private TableColumn<ActivityLogDto, String> actionColumn;
    @FXML private TableColumn<ActivityLogDto, String> moduleColumn;
    @FXML private TableColumn<ActivityLogDto, String> recordIdColumn;
    @FXML private TableColumn<ActivityLogDto, String> machineColumn;
    @FXML private TableColumn<ActivityLogDto, String> descriptionColumn;
    @FXML private Pagination activityLogPagination;
    @FXML private ComboBox<Integer> activityLogItemsPerPageComboBox;
    @FXML private Label activityLogPaginationSummaryLabel;
    @FXML private Label messageLabel;

    @FXML
    private void initialize() {
        configureButtonIcons();
        configureDateFilters();
        configureTable();
        loadFilters();
        loadLatestLogs();
    }

    @FXML
    private void handleSearch() {
        try {
            ActivityLogSearchCriteria criteria = buildCriteria(1000);
            activityLogs.setAll(activityLogQueryService.searchLogs(criteria));
            setMessage(activityLogs.size() + " activity log(s) found.");
        } catch (ActivityLogQueryService.ActivityLogException exception) {
            showError("Activity Logs", exception.getMessage());
        } catch (DatabaseException exception) {
            showError("Activity Logs", "Unable to load activity logs.");
        }
    }

    @FXML
    private void handleClear() {
        dateFromPicker.setValue(null);
        dateToPicker.setValue(null);
        userComboBox.setValue(users.isEmpty() ? null : users.getFirst());
        actionComboBox.setValue(ALL_ACTIONS);
        moduleComboBox.setValue(ALL_MODULES);
        keywordField.clear();
        loadLatestLogs();
    }

    @FXML
    private void handleRefresh() {
        handleClear();
    }

    @FXML
    private void handleViewDetails() {
        ActivityLogDto selected = activityLogTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Activity Logs", "Select an activity log to view details.");
            return;
        }
        showActivityLogDetails(selected);
    }

    private void configureButtonIcons() {
        ButtonIconUtil.applyIcon(searchButton, "fas-search");
        ButtonIconUtil.applyIcon(clearButton, "fas-eraser");
        ButtonIconUtil.applyIcon(viewDetailsButton, "fas-eye");
        ButtonIconUtil.applyIcon(refreshButton, "fas-sync-alt");
    }

    private void configureDateFilters() {
        DatePickerUtil.applySystemDateFormat(dateFromPicker);
        DatePickerUtil.applySystemDateFormat(dateToPicker);
    }

    private void configureTable() {
        TablePaginationUtil.configure(activityLogTable, activityLogs, activityLogPagination,
                activityLogItemsPerPageComboBox, activityLogPaginationSummaryLabel, "activity logs");
        dateTimeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                formatDateTime(cellData.getValue().getCreatedAt())));
        userColumn.setCellValueFactory(cellData -> new SimpleStringProperty(formatUser(cellData.getValue())));
        actionColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getAction())));
        moduleColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getModule())));
        recordIdColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getRecordId())));
        machineColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getMachineName())));
        descriptionColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToDash(cellData.getValue().getDescription())));
        descriptionColumn.setCellFactory(column -> tooltipTextCell());

        activityLogTable.setRowFactory(tableView -> {
            TableRow<ActivityLogDto> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showActivityLogDetails(row.getItem());
                }
            });
            return row;
        });
    }

    private javafx.scene.control.TableCell<ActivityLogDto, String> tooltipTextCell() {
        return new javafx.scene.control.TableCell<>() {
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

    private void loadFilters() {
        try {
            List<UserOption> userOptions = new ArrayList<>();
            userOptions.add(UserOption.allUsersOption());
            userOptions.addAll(userRepository.findActiveUserSummaries().stream()
                    .map(summary -> new UserOption(summary.userId(), summary.username(), summary.fullName()))
                    .toList());
            users.setAll(userOptions);
            userComboBox.setItems(users);
            ComboBoxUtil.makeSearchable(userComboBox, UserOption::toString);
            userComboBox.setValue(users.getFirst());

            actionComboBox.setItems(FXCollections.observableArrayList(withAll(ALL_ACTIONS,
                    activityLogQueryService.getActions())));
            ComboBoxUtil.makeSearchable(actionComboBox, value -> value);
            actionComboBox.setValue(ALL_ACTIONS);
            moduleComboBox.setItems(FXCollections.observableArrayList(withAll(ALL_MODULES,
                    activityLogQueryService.getModules())));
            ComboBoxUtil.makeSearchable(moduleComboBox, value -> value);
            moduleComboBox.setValue(ALL_MODULES);
        } catch (ActivityLogQueryService.ActivityLogException exception) {
            showError("Activity Logs", exception.getMessage());
        } catch (DatabaseException exception) {
            showError("Activity Logs", "Unable to load activity logs.");
        }
    }

    private List<String> withAll(String allValue, List<String> values) {
        List<String> options = new ArrayList<>();
        options.add(allValue);
        options.addAll(values);
        return options;
    }

    private void loadLatestLogs() {
        try {
            ActivityLogSearchCriteria criteria = new ActivityLogSearchCriteria();
            criteria.setLimit(100);
            activityLogs.setAll(activityLogQueryService.searchLogs(criteria));
            setMessage("Latest " + activityLogs.size() + " activity log(s) loaded.");
        } catch (ActivityLogQueryService.ActivityLogException exception) {
            showError("Activity Logs", exception.getMessage());
        } catch (DatabaseException exception) {
            showError("Activity Logs", "Unable to load activity logs.");
        }
    }

    private ActivityLogSearchCriteria buildCriteria(int limit) {
        ActivityLogSearchCriteria criteria = new ActivityLogSearchCriteria();
        criteria.setDateFrom(dateFromPicker.getValue());
        criteria.setDateTo(dateToPicker.getValue());
        UserOption selectedUser = userComboBox.getValue();
        criteria.setUserId(selectedUser == null || selectedUser.allUsers() ? null : selectedUser.userId());
        criteria.setAction(ALL_ACTIONS.equals(actionComboBox.getValue()) ? null : actionComboBox.getValue());
        criteria.setModule(ALL_MODULES.equals(moduleComboBox.getValue()) ? null : moduleComboBox.getValue());
        criteria.setKeyword(keywordField.getText());
        criteria.setLimit(limit);
        return criteria;
    }

    private void showActivityLogDetails(ActivityLogDto selectedLog) {
        try {
            ActivityLogDto log = activityLogQueryService.getLogDetails(selectedLog.getId());
            Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.INFORMATION));
            alert.setTitle("Activity Log Details");
            alert.setHeaderText("Activity Log");
            alert.getDialogPane().setContent(detailsContent(log));
            alert.getDialogPane().setPrefWidth(900);
            alert.showAndWait();
        } catch (ActivityLogQueryService.ActivityLogException exception) {
            showError("Activity Logs", exception.getMessage());
        } catch (DatabaseException exception) {
            showError("Activity Logs", "Unable to load activity logs.");
        }
    }

    private VBox detailsContent(ActivityLogDto log) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(130);
        labelColumn.setPrefWidth(150);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(labelColumn, valueColumn);

        addDetailRow(grid, 0, "Date/Time", formatDateTime(log.getCreatedAt()));
        addDetailRow(grid, 1, "User", formatUser(log));
        addDetailRow(grid, 2, "Action", nullToDash(log.getAction()));
        addDetailRow(grid, 3, "Module", nullToDash(log.getModule()));
        addDetailRow(grid, 4, "Record ID", nullToDash(log.getRecordId()));
        addDetailRow(grid, 5, "IP Address", nullToDash(log.getIpAddress()));
        addDetailRow(grid, 6, "Machine Name", nullToDash(log.getMachineName()));

        VBox content = new VBox(10, grid,
                DialogStyler.fieldLabel("Description"), readOnlyArea(log.getDescription(), 3),
                DialogStyler.fieldLabel("Old Value"), readOnlyKeyValueArea(displayOldValue(log), 5),
                DialogStyler.fieldLabel("New Value"), readOnlyKeyValueArea(displayNewValue(log), 5));
        content.setPrefWidth(820);
        return content;
    }

    private void addDetailRow(GridPane grid, int row, String labelText, String valueText) {
        Label label = DialogStyler.fieldLabel(labelText);
        label.setMinWidth(130);
        Label value = new Label(valueText);
        value.setWrapText(true);
        value.setMaxWidth(Double.MAX_VALUE);
        value.getStyleClass().add("value-label");
        grid.add(label, 0, row);
        grid.add(value, 1, row);
        GridPane.setHgrow(value, Priority.ALWAYS);
    }

    private TextArea readOnlyArea(String value, int rows) {
        TextArea textArea = new TextArea(nullToDash(value));
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(rows);
        return textArea;
    }

    private TextArea readOnlyKeyValueArea(String value, int rows) {
        TextArea textArea = readOnlyArea(value, rows);
        textArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 13px;");
        return textArea;
    }

    private String displayOldValue(ActivityLogDto log) {
        if (log.getOldValue() != null && !log.getOldValue().isBlank()) {
            return formatKeyValuePairs(log.getOldValue());
        }
        return formatKeyValuePairs(parsedChangeValue(log.getDescription(), true));
    }

    private String displayNewValue(ActivityLogDto log) {
        if (log.getNewValue() != null && !log.getNewValue().isBlank()) {
            return formatKeyValuePairs(log.getNewValue());
        }
        return formatKeyValuePairs(parsedChangeValue(log.getDescription(), false));
    }

    private String formatKeyValuePairs(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String[] parts = value.split(",\\s*");
        List<KeyValueRow> rows = new ArrayList<>();
        int maxLabelLength = 0;
        for (String part : parts) {
            int separator = part.indexOf(':');
            if (separator < 0) {
                return value;
            }
            String rawLabel = part.substring(0, separator);
            String label = formalLabel(rawLabel);
            String fieldValue = part.substring(separator + 1).strip();
            rows.add(new KeyValueRow(label, displayKeyValue(rawLabel, fieldValue)));
            maxLabelLength = Math.max(maxLabelLength, label.length());
        }

        StringBuilder formatted = new StringBuilder();
        for (KeyValueRow row : rows) {
            if (!formatted.isEmpty()) {
                formatted.append(System.lineSeparator());
            }
            formatted.append(String.format("%-" + maxLabelLength + "s : %s", row.label(), row.value()));
        }
        return formatted.toString();
    }

    private String formalLabel(String value) {
        String[] words = value.strip().replace('_', ' ').split("\\s+");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                label.append(word.substring(1).toLowerCase());
            }
        }
        return label.toString();
    }

    private String parsedChangeValue(String description, boolean oldValue) {
        if (description == null || !description.contains(" -> ")) {
            return null;
        }
        String change = description.substring(description.lastIndexOf(',') + 1).strip();
        int separator = change.indexOf(" -> ");
        if (separator < 0) {
            return null;
        }
        String prefix = "";
        String left = change.substring(0, separator).strip();
        int labelSeparator = left.indexOf(':');
        if (labelSeparator >= 0) {
            prefix = left.substring(0, labelSeparator + 1) + " ";
            left = left.substring(labelSeparator + 1).strip();
        }
        String right = change.substring(separator + 4).strip();
        return prefix + (oldValue ? left : right);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTimeFormatter.formatDateTime(dateTime);
    }

    private String displayKeyValue(String key, String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        if (isDateTimeKey(key)) {
            try {
                return dateTimeFormatter.formatDateTime(LocalDateTime.parse(value.strip()));
            } catch (java.time.format.DateTimeParseException ignored) {
                try {
                    return dateTimeFormatter.formatDate(java.time.LocalDate.parse(value.strip()));
                } catch (java.time.format.DateTimeParseException ignoredAgain) {
                    return value;
                }
            }
        }
        return value;
    }

    private boolean isDateTimeKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.strip().toLowerCase();
        return normalized.contains("date")
                || normalized.contains("time")
                || normalized.endsWith("_at")
                || normalized.endsWith(" at");
    }

    private String formatUser(ActivityLogDto log) {
        if (log.getUsername() == null || log.getUsername().isBlank()) {
            return "-";
        }
        if (log.getUserFullName() == null || log.getUserFullName().isBlank()) {
            return log.getUsername();
        }
        return log.getUsername() + " - " + log.getUserFullName();
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

    private record UserOption(Long userId, String username, String fullName, boolean allUsers) {
        private static UserOption allUsersOption() {
            return new UserOption(null, "ALL USERS", "", true);
        }

        private UserOption(Long userId, String username, String fullName) {
            this(userId, username, fullName, false);
        }

        @Override
        public String toString() {
            return allUsers ? "ALL USERS" : username + " - " + fullName;
        }
    }

    private record KeyValueRow(String label, String value) {
    }
}
