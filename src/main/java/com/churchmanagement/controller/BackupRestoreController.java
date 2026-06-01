package com.churchmanagement.controller;

import com.churchmanagement.dto.BackupLogDto;
import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.dto.RestoreLogDto;
import com.churchmanagement.dto.RestoreRequest;
import com.churchmanagement.repository.BackupRepository;
import com.churchmanagement.repository.RestoreRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.BackupService;
import com.churchmanagement.service.BackupSettingsService;
import com.churchmanagement.service.AutoBackupScheduler;
import com.churchmanagement.service.RestoreService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ProcessingDialog;
import com.churchmanagement.util.TablePaginationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class BackupRestoreController {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String FILTER_ALL = "ALL";

    private final BackupService backupService = new BackupService();
    private final RestoreService restoreService = new RestoreService();
    private final BackupSettingsService backupSettingsService = new BackupSettingsService();
    private final BackupRepository backupRepository = new BackupRepository();
    private final RestoreRepository restoreRepository = new RestoreRepository();
    private final ObservableList<BackupLogDto> allBackupLogs = FXCollections.observableArrayList();
    private final ObservableList<BackupLogDto> filteredBackupLogs = FXCollections.observableArrayList();
    private final ObservableList<RestoreLogDto> allRestoreLogs = FXCollections.observableArrayList();
    private final ObservableList<RestoreLogDto> filteredRestoreLogs = FXCollections.observableArrayList();

    private AuthenticatedUser currentUser;
    private PermissionGuard permissionGuard;

    @FXML private TextField backupFolderField;
    @FXML private Button browseBackupFolderButton;
    @FXML private Button backupNowButton;
    @FXML private DatePicker backupDateFromPicker;
    @FXML private DatePicker backupDateToPicker;
    @FXML private ComboBox<String> backupTypeFilterComboBox;
    @FXML private ComboBox<String> backupStatusFilterComboBox;
    @FXML private TextField backupSearchField;
    @FXML private Button backupSearchButton;
    @FXML private Button backupClearButton;
    @FXML private Button backupRefreshButton;
    @FXML private CheckBox autoBackupEnabledCheckBox;
    @FXML private TextField autoBackupTimeField;
    @FXML private TextField retentionDaysField;
    @FXML private TextField mysqldumpPathField;
    @FXML private TextField mysqlClientPathField;
    @FXML private Button saveSettingsButton;
    @FXML private TextField restoreBackupFileField;
    @FXML private Button browseRestoreFileButton;
    @FXML private TextField restoreConfirmationField;
    @FXML private Button restoreButton;
    @FXML private Label restoreWarningLabel;
    @FXML private DatePicker restoreDateFromPicker;
    @FXML private DatePicker restoreDateToPicker;
    @FXML private ComboBox<String> restoreStatusFilterComboBox;
    @FXML private TextField restoreSearchField;
    @FXML private Button restoreSearchButton;
    @FXML private Button restoreClearButton;
    @FXML private Button restoreRefreshButton;
    @FXML private TableView<BackupLogDto> backupHistoryTable;
    @FXML private TableColumn<BackupLogDto, String> backupDateColumn;
    @FXML private TableColumn<BackupLogDto, String> backupTypeColumn;
    @FXML private TableColumn<BackupLogDto, String> backupFileNameColumn;
    @FXML private TableColumn<BackupLogDto, String> backupFileSizeColumn;
    @FXML private TableColumn<BackupLogDto, String> backupStatusColumn;
    @FXML private TableColumn<BackupLogDto, String> backupCreatedByColumn;
    @FXML private TableColumn<BackupLogDto, String> backupErrorColumn;
    @FXML private TableView<RestoreLogDto> restoreHistoryTable;
    @FXML private TableColumn<RestoreLogDto, String> restoreDateColumn;
    @FXML private TableColumn<RestoreLogDto, String> restoreFileNameColumn;
    @FXML private TableColumn<RestoreLogDto, String> restoreStatusColumn;
    @FXML private TableColumn<RestoreLogDto, String> restoreRestoredByColumn;
    @FXML private TableColumn<RestoreLogDto, String> restoreErrorColumn;
    @FXML private Label backupMessageLabel;
    @FXML private Label restoreMessageLabel;
    @FXML private Label schedulerTodoLabel;
    @FXML private VBox backupHistorySection;
    @FXML private VBox restoreHistorySection;
    @FXML private Pagination backupLogPagination;
    @FXML private ComboBox<Integer> backupLogItemsPerPageComboBox;
    @FXML private Label backupLogPaginationSummaryLabel;
    @FXML private Pagination restoreLogPagination;
    @FXML private ComboBox<Integer> restoreLogItemsPerPageComboBox;
    @FXML private Label restoreLogPaginationSummaryLabel;

    @FXML
    private void initialize() {
        currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("Please sign in to use Backup & Restore."));
        permissionGuard = new PermissionGuard(currentUser);
        configureButtonIcons();
        configureFilters();
        configurePermissions();
        configureTables();
        loadSettings();
        loadHistory();
    }

    @FXML
    private void browseBackupFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Backup Folder");
        File selected = chooser.showDialog(window());
        if (selected != null) {
            backupFolderField.setText(selected.getAbsolutePath());
        }
    }

    @FXML
    private void createManualBackup() {
        ProcessingDialog.run("Backup", "Creating database backup...",
                () -> backupService.createManualBackup(backupFolderField.getText()),
                log -> {
                    showInfo("Backup created", "Backup file created successfully.");
                    loadBackupLogs();
                },
                throwable -> showError("Backup failed", processingMessage(throwable)));
    }

    @FXML
    private void searchBackupLogs() {
        applyBackupFilters();
    }

    @FXML
    private void clearBackupFilters() {
        backupDateFromPicker.setValue(null);
        backupDateToPicker.setValue(null);
        backupTypeFilterComboBox.setValue(FILTER_ALL);
        backupStatusFilterComboBox.setValue(FILTER_ALL);
        backupSearchField.clear();
        applyBackupFilters();
    }

    @FXML
    private void refreshBackupLogs() {
        loadBackupLogs();
    }

    @FXML
    private void browseRestoreFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select SQL Backup File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQL files", "*.sql"));
        File selected = chooser.showOpenDialog(window());
        if (selected != null) {
            restoreBackupFileField.setText(selected.getAbsolutePath());
        }
    }

    @FXML
    private void restoreBackup() {
        Alert confirm = DialogStyler.apply(new Alert(Alert.AlertType.WARNING));
        confirm.setTitle("Confirm Restore");
        confirm.setHeaderText("Restore database backup?");
        confirm.setContentText("Restore will replace current database data. A pre-restore backup will be created automatically.");
        if (confirm.showAndWait().isEmpty()) {
            return;
        }

        RestoreRequest request = new RestoreRequest();
        request.setBackupFilePath(restoreBackupFileField.getText());
        request.setConfirmRestoreText(restoreConfirmationField.getText());
        ProcessingDialog.run("Restore", "Restoring database backup...",
                () -> restoreService.restoreBackup(request),
                log -> {
                    showInfo("Restore completed", "Restore completed. Please restart the application.");
                    loadHistory();
                },
                throwable -> showError("Restore failed", processingMessage(throwable)));
    }

    @FXML
    private void searchRestoreLogs() {
        applyRestoreFilters();
    }

    @FXML
    private void clearRestoreFilters() {
        restoreDateFromPicker.setValue(null);
        restoreDateToPicker.setValue(null);
        restoreStatusFilterComboBox.setValue(FILTER_ALL);
        restoreSearchField.clear();
        applyRestoreFilters();
    }

    @FXML
    private void refreshRestoreLogs() {
        loadRestoreLogs();
    }

    @FXML
    private void saveSettings() {
        BackupSettingsDto settings = readSettingsForm();
        if (settings == null) {
            return;
        }
        ProcessingDialog.run("Backup Settings", "Saving backup settings...",
                () -> backupSettingsService.updateSettings(settings),
                saved -> {
                    loadSettings(saved);
                    AutoBackupScheduler.getInstance().reloadSchedule();
                    showInfo("Settings saved", "Backup settings were saved.");
                },
                throwable -> showError("Settings failed", processingMessage(throwable)));
    }

    private void configureButtonIcons() {
        ButtonIconUtil.applyIcon(backupSearchButton, "fas-search");
        ButtonIconUtil.applyIcon(backupClearButton, "fas-eraser");
        ButtonIconUtil.applyIcon(backupRefreshButton, "fas-sync-alt");
        ButtonIconUtil.applyIcon(restoreSearchButton, "fas-search");
        ButtonIconUtil.applyIcon(restoreClearButton, "fas-eraser");
        ButtonIconUtil.applyIcon(restoreRefreshButton, "fas-sync-alt");
    }

    private void configureFilters() {
        backupTypeFilterComboBox.setItems(FXCollections.observableArrayList(FILTER_ALL, "MANUAL", "AUTO", "PRE_RESTORE"));
        backupTypeFilterComboBox.setValue(FILTER_ALL);
        backupStatusFilterComboBox.setItems(FXCollections.observableArrayList(FILTER_ALL, "SUCCESS", "FAILED"));
        backupStatusFilterComboBox.setValue(FILTER_ALL);
        restoreStatusFilterComboBox.setItems(FXCollections.observableArrayList(FILTER_ALL, "SUCCESS", "FAILED"));
        restoreStatusFilterComboBox.setValue(FILTER_ALL);
    }

    private void configurePermissions() {
        boolean canCreate = permissionGuard.can("backup.create");
        boolean canRestore = permissionGuard.can("backup.restore");
        boolean canManageSettings = permissionGuard.can("backup.settings.manage");
        boolean canView = permissionGuard.can("backup.view");

        backupNowButton.setVisible(canCreate);
        backupNowButton.setManaged(canCreate);
        backupNowButton.setDisable(!canCreate);
        restoreButton.setVisible(canRestore);
        restoreButton.setManaged(canRestore);
        restoreButton.setDisable(!canRestore);
        browseRestoreFileButton.setDisable(!canRestore);
        restoreBackupFileField.setDisable(!canRestore);
        restoreConfirmationField.setDisable(!canRestore);

        autoBackupEnabledCheckBox.setDisable(!canManageSettings);
        autoBackupTimeField.setDisable(!canManageSettings);
        retentionDaysField.setDisable(!canManageSettings);
        mysqldumpPathField.setDisable(!canManageSettings);
        mysqlClientPathField.setDisable(!canManageSettings);
        saveSettingsButton.setVisible(canManageSettings);
        saveSettingsButton.setManaged(canManageSettings);
        browseBackupFolderButton.setDisable(!canCreate && !canManageSettings);
        backupSearchButton.setDisable(!canView);
        backupClearButton.setDisable(!canView);
        backupRefreshButton.setDisable(!canView);
        restoreSearchButton.setDisable(!canView);
        restoreClearButton.setDisable(!canView);
        restoreRefreshButton.setDisable(!canView);
        backupHistorySection.setVisible(canView);
        backupHistorySection.setManaged(canView);
        restoreHistorySection.setVisible(canView);
        restoreHistorySection.setManaged(canView);
    }

    private void configureTables() {
        TablePaginationUtil.configure(backupHistoryTable, filteredBackupLogs, backupLogPagination,
                backupLogItemsPerPageComboBox, backupLogPaginationSummaryLabel, "backup logs");
        TablePaginationUtil.configure(restoreHistoryTable, filteredRestoreLogs, restoreLogPagination,
                restoreLogItemsPerPageComboBox, restoreLogPaginationSummaryLabel, "restore logs");
        backupDateColumn.setCellValueFactory(data -> value(formatDateTime(data.getValue().getCreatedAt())));
        backupTypeColumn.setCellValueFactory(data -> value(data.getValue().getBackupType()));
        backupTypeColumn.setCellFactory(column -> backupTypeBadgeCell());
        backupFileNameColumn.setCellValueFactory(data -> value(data.getValue().getFileName()));
        backupFileSizeColumn.setCellValueFactory(data -> value(formatFileSize(data.getValue().getFileSizeBytes())));
        backupStatusColumn.setCellValueFactory(data -> value(data.getValue().getStatus()));
        backupStatusColumn.setCellFactory(column -> statusBadgeCell());
        backupCreatedByColumn.setCellValueFactory(data -> value(data.getValue().getCreatedByFullName()));
        backupErrorColumn.setCellValueFactory(data -> value(data.getValue().getErrorMessage()));

        restoreDateColumn.setCellValueFactory(data -> value(formatDateTime(data.getValue().getRestoredAt())));
        restoreFileNameColumn.setCellValueFactory(data -> value(data.getValue().getBackupFileName()));
        restoreStatusColumn.setCellValueFactory(data -> value(data.getValue().getStatus()));
        restoreStatusColumn.setCellFactory(column -> statusBadgeCell());
        restoreRestoredByColumn.setCellValueFactory(data -> value(data.getValue().getRestoredByFullName()));
        restoreErrorColumn.setCellValueFactory(data -> value(data.getValue().getErrorMessage()));
    }

    private void loadSettings() {
        loadSettings(backupSettingsService.getSettings());
    }

    private void loadSettings(BackupSettingsDto settings) {
        backupFolderField.setText(nullToBlank(settings.getBackupFolder()));
        autoBackupEnabledCheckBox.setSelected(settings.isAutoBackupEnabled());
        autoBackupTimeField.setText(settings.getAutoBackupTime() == null ? "" : settings.getAutoBackupTime().toString());
        retentionDaysField.setText(Integer.toString(settings.getRetentionDays()));
        mysqldumpPathField.setText(nullToBlank(settings.getMysqldumpPath()));
        mysqlClientPathField.setText(nullToBlank(settings.getMysqlClientPath()));
        // TODO Phase later: create Windows Task Scheduler script generation.
        schedulerTodoLabel.setText("Phase later: create Windows Task Scheduler script generation.");
    }

    private void loadHistory() {
        if (permissionGuard.can("backup.view")) {
            loadBackupLogs();
            loadRestoreLogs();
        }
    }

    private void loadBackupLogs() {
        allBackupLogs.setAll(backupRepository.searchBackupLogs(500));
        applyBackupFilters();
    }

    private void loadRestoreLogs() {
        allRestoreLogs.setAll(restoreRepository.searchRestoreLogs(500));
        applyRestoreFilters();
    }

    private void applyBackupFilters() {
        filteredBackupLogs.setAll(allBackupLogs.stream()
                .filter(log -> withinDateRange(log.getCreatedAt(), backupDateFromPicker, backupDateToPicker))
                .filter(log -> filterMatches(backupTypeFilterComboBox.getValue(), log.getBackupType()))
                .filter(log -> filterMatches(backupStatusFilterComboBox.getValue(), log.getStatus()))
                .filter(log -> textMatches(backupSearchField.getText(), log.getFileName(), log.getFilePath(),
                        log.getCreatedByFullName(), log.getErrorMessage()))
                .toList());
        backupMessageLabel.setText(filteredBackupLogs.size() + " backup log(s) found.");
    }

    private void applyRestoreFilters() {
        filteredRestoreLogs.setAll(allRestoreLogs.stream()
                .filter(log -> withinDateRange(log.getRestoredAt(), restoreDateFromPicker, restoreDateToPicker))
                .filter(log -> filterMatches(restoreStatusFilterComboBox.getValue(), log.getStatus()))
                .filter(log -> textMatches(restoreSearchField.getText(), log.getBackupFileName(),
                        log.getBackupFilePath(), log.getRestoredByFullName(), log.getErrorMessage()))
                .toList());
        restoreMessageLabel.setText(filteredRestoreLogs.size() + " restore log(s) found.");
    }

    private BackupSettingsDto readSettingsForm() {
        BackupSettingsDto settings = new BackupSettingsDto();
        settings.setBackupFolder(backupFolderField.getText());
        settings.setAutoBackupEnabled(autoBackupEnabledCheckBox.isSelected());
        LocalTime backupTime = parseBackupTime();
        if (backupTime == null && autoBackupTimeField.getText() != null && !autoBackupTimeField.getText().isBlank()) {
            return null;
        }
        settings.setAutoBackupTime(backupTime);
        try {
            settings.setRetentionDays(Integer.parseInt(retentionDaysField.getText().strip()));
        } catch (RuntimeException exception) {
            showError("Invalid retention", "Retention days must be a number.");
            return null;
        }
        settings.setMysqldumpPath(mysqldumpPathField.getText());
        settings.setMysqlClientPath(mysqlClientPathField.getText());
        return settings;
    }

    private LocalTime parseBackupTime() {
        String value = autoBackupTimeField.getText();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.strip());
        } catch (RuntimeException exception) {
            showError("Invalid backup time", "Backup time must use HH:mm format.");
            return null;
        }
    }

    private SimpleStringProperty value(Object value) {
        return new SimpleStringProperty(value == null ? "" : value.toString());
    }

    private <T> TableCell<T, String> statusBadgeCell() {
        return new TableCell<>() {
            private final Label badge = new Label();

            {
                badge.getStyleClass().add("status-badge");
            }

            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null || status.isBlank()) {
                    setGraphic(null);
                    return;
                }
                badge.setText(status);
                badge.getStyleClass().removeAll("status-active", "status-inactive", "status-skipped");
                badge.getStyleClass().add("SUCCESS".equals(status) ? "status-active" : "status-inactive");
                setGraphic(badge);
            }
        };
    }

    private TableCell<BackupLogDto, String> backupTypeBadgeCell() {
        return new TableCell<>() {
            private final Label badge = new Label();

            {
                badge.getStyleClass().add("status-badge");
            }

            @Override
            protected void updateItem(String backupType, boolean empty) {
                super.updateItem(backupType, empty);
                if (empty || backupType == null || backupType.isBlank()) {
                    setGraphic(null);
                    return;
                }
                badge.setText(backupType);
                badge.getStyleClass().removeAll("status-active", "status-inactive", "status-skipped",
                        "status-correction-note");
                switch (backupType) {
                    case "MANUAL" -> badge.getStyleClass().add("status-active");
                    case "AUTO" -> badge.getStyleClass().add("status-skipped");
                    case "PRE_RESTORE" -> badge.getStyleClass().add("status-correction-note");
                    default -> badge.getStyleClass().add("status-skipped");
                }
                setGraphic(badge);
            }
        };
    }

    private boolean withinDateRange(LocalDateTime value, DatePicker fromPicker, DatePicker toPicker) {
        if (value == null) {
            return fromPicker.getValue() == null && toPicker.getValue() == null;
        }
        if (fromPicker.getValue() != null && value.toLocalDate().isBefore(fromPicker.getValue())) {
            return false;
        }
        return toPicker.getValue() == null || !value.toLocalDate().isAfter(toPicker.getValue());
    }

    private boolean filterMatches(String selectedFilter, Object value) {
        return selectedFilter == null || FILTER_ALL.equals(selectedFilter)
                || (value != null && selectedFilter.equals(value.toString()));
    }

    private boolean textMatches(String searchText, String... values) {
        if (searchText == null || searchText.isBlank()) {
            return true;
        }
        String needle = searchText.strip().toLowerCase();
        for (String value : values) {
            if (value != null && value.toLowerCase().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMAT);
    }

    private String formatFileSize(Long bytes) {
        if (bytes == null) {
            return "";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        return String.format("%.1f KB", bytes / 1024.0);
    }

    private Window window() {
        return backupFolderField.getScene().getWindow();
    }

    private void showInfo(String title, String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.INFORMATION));
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String processingMessage(Throwable throwable) {
        return throwable.getMessage() == null ? "Action failed. Please try again." : throwable.getMessage();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
