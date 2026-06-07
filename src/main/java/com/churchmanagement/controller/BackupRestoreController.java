package com.churchmanagement.controller;

import com.churchmanagement.dto.BackupLogDto;
import com.churchmanagement.dto.BackupScheduleDto;
import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.dto.RestoreLogDto;
import com.churchmanagement.dto.RestoreRequest;
import com.churchmanagement.repository.BackupRepository;
import com.churchmanagement.repository.RestoreRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.BackupService;
import com.churchmanagement.service.BackupScheduleService;
import com.churchmanagement.service.BackupSettingsService;
import com.churchmanagement.service.AutoBackupScheduler;
import com.churchmanagement.service.RestoreService;
import com.churchmanagement.service.WindowsTaskSchedulerScriptService;
import com.churchmanagement.util.ApplicationRestartUtil;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DatePickerUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ProcessingDialog;
import com.churchmanagement.util.SystemDateTimeFormatter;
import com.churchmanagement.util.TablePaginationUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class BackupRestoreController {
    private static final String FILTER_ALL = "ALL";

    private final BackupService backupService = new BackupService();
    private final RestoreService restoreService = new RestoreService();
    private final BackupSettingsService backupSettingsService = new BackupSettingsService();
    private final BackupScheduleService backupScheduleService = new BackupScheduleService();
    private final WindowsTaskSchedulerScriptService taskSchedulerScriptService = new WindowsTaskSchedulerScriptService();
    private final BackupRepository backupRepository = new BackupRepository();
    private final RestoreRepository restoreRepository = new RestoreRepository();
    private final SystemDateTimeFormatter dateTimeFormatter = new SystemDateTimeFormatter();
    private final ObservableList<BackupLogDto> allBackupLogs = FXCollections.observableArrayList();
    private final ObservableList<BackupLogDto> filteredBackupLogs = FXCollections.observableArrayList();
    private final ObservableList<BackupScheduleDto> backupSchedules = FXCollections.observableArrayList();
    private final ObservableList<RestoreLogDto> allRestoreLogs = FXCollections.observableArrayList();
    private final ObservableList<RestoreLogDto> filteredRestoreLogs = FXCollections.observableArrayList();

    private AuthenticatedUser currentUser;
    private PermissionGuard permissionGuard;
    private boolean canManageBackupSettings;

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
    @FXML private TextField scheduleNameField;
    @FXML private Spinner<Integer> scheduleHourSpinner;
    @FXML private Spinner<Integer> scheduleMinuteSpinner;
    @FXML private ComboBox<String> scheduleAmPmComboBox;
    @FXML private CheckBox scheduleEnabledCheckBox;
    @FXML private Button addScheduleButton;
    @FXML private Button updateScheduleButton;
    @FXML private Button clearScheduleButton;
    @FXML private Button toggleScheduleButton;
    @FXML private Button deleteScheduleButton;
    @FXML private TableView<BackupScheduleDto> backupScheduleTable;
    @FXML private TableColumn<BackupScheduleDto, String> scheduleNameColumn;
    @FXML private TableColumn<BackupScheduleDto, String> scheduleTimeColumn;
    @FXML private TableColumn<BackupScheduleDto, String> scheduleEnabledColumn;
    @FXML private TextField autoBackupFolderField;
    @FXML private Button browseAutoBackupFolderButton;
    @FXML private TextField retentionDaysField;
    @FXML private TextField mysqldumpPathField;
    @FXML private TextField mysqlClientPathField;
    @FXML private Button saveSettingsButton;
    @FXML private Button generateAutoBackupScriptButton;
    @FXML private Label autoBackupScriptPathLabel;
    @FXML private TextArea autoBackupSchedulerCommandArea;
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
        configureScheduleTimeInputs();
        configureTables();
        loadSettings();
        loadSchedules();
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
    private void browseAutoBackupFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Automatic Backup Folder");
        File selected = chooser.showDialog(window());
        if (selected != null) {
            autoBackupFolderField.setText(selected.getAbsolutePath());
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
                    showInfo("Restore completed", "Restore completed. The application will restart now.");
                    ApplicationRestartUtil.logoutAndRestart();
                    Platform.exit();
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
                    showInfo("Settings saved", "Backup settings were saved.");
                },
                throwable -> showError("Settings failed", processingMessage(throwable)));
    }

    @FXML
    private void addBackupSchedule() {
        BackupScheduleDto schedule = readScheduleForm(null);
        if (schedule == null) {
            return;
        }
        try {
            backupScheduleService.addSchedule(schedule);
            loadSchedules();
            AutoBackupScheduler.getInstance().reloadSchedule();
            clearScheduleForm();
        } catch (RuntimeException exception) {
            showError("Schedule failed", processingMessage(exception));
        }
    }

    @FXML
    private void updateBackupSchedule() {
        BackupScheduleDto selected = backupScheduleTable.getSelectionModel().getSelectedItem();
        BackupScheduleDto schedule = readScheduleForm(selected);
        if (schedule == null) {
            return;
        }
        try {
            backupScheduleService.updateSchedule(schedule);
            loadSchedules();
            AutoBackupScheduler.getInstance().reloadSchedule();
            clearScheduleForm();
        } catch (RuntimeException exception) {
            showError("Schedule failed", processingMessage(exception));
        }
    }

    @FXML
    private void toggleBackupSchedule() {
        BackupScheduleDto selected = backupScheduleTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Schedule failed", "Select a schedule to update.");
            return;
        }
        try {
            backupScheduleService.setScheduleEnabled(selected, !selected.isEnabled());
            loadSchedules();
            AutoBackupScheduler.getInstance().reloadSchedule();
            clearScheduleForm();
        } catch (RuntimeException exception) {
            showError("Schedule failed", processingMessage(exception));
        }
    }

    @FXML
    private void clearBackupScheduleForm() {
        clearScheduleForm();
    }

    @FXML
    private void deleteBackupSchedule() {
        BackupScheduleDto selected = backupScheduleTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Schedule failed", "Select a schedule to delete.");
            return;
        }
        Alert confirm = DialogStyler.apply(new Alert(Alert.AlertType.CONFIRMATION));
        confirm.setTitle("Delete schedule");
        confirm.setHeaderText("Delete automatic backup schedule?");
        confirm.setContentText("Schedule: " + selected.getScheduleName());
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }
        try {
            backupScheduleService.deleteSchedule(selected);
            loadSchedules();
            AutoBackupScheduler.getInstance().reloadSchedule();
            clearScheduleForm();
        } catch (RuntimeException exception) {
            showError("Schedule failed", processingMessage(exception));
        }
    }

    @FXML
    private void generateAutoBackupScript() {
        try {
            Path scriptPath = taskSchedulerScriptService.generateBackupBatScript();
            autoBackupScriptPathLabel.setText("Generated: " + scriptPath);
            autoBackupSchedulerCommandArea.setText(String.join(System.lineSeparator(),
                    taskSchedulerScriptService.buildSchtasksCommands()));
            showInfo("Script generated",
                    "Use Windows Task Scheduler to run this file daily at the selected backup time.");
        } catch (RuntimeException exception) {
            showError("Script generation failed", processingMessage(exception));
        }
    }

    private void configureButtonIcons() {
        ButtonIconUtil.applyIcon(browseBackupFolderButton, "fas-folder-open");
        ButtonIconUtil.applyIcon(backupNowButton, "fas-database");
        ButtonIconUtil.applyIcon(addScheduleButton, "fas-plus");
        ButtonIconUtil.applyIcon(updateScheduleButton, "fas-edit");
        ButtonIconUtil.applyIcon(clearScheduleButton, "fas-eraser");
        ButtonIconUtil.applyIcon(toggleScheduleButton, "fas-toggle-on");
        ButtonIconUtil.applyIcon(deleteScheduleButton, "fas-trash-alt");
        ButtonIconUtil.applyIcon(browseAutoBackupFolderButton, "fas-folder-open");
        ButtonIconUtil.applyIcon(saveSettingsButton, "fas-save");
        ButtonIconUtil.applyIcon(generateAutoBackupScriptButton, "fas-file-code");
        ButtonIconUtil.applyIcon(backupSearchButton, "fas-search");
        ButtonIconUtil.applyIcon(backupClearButton, "fas-eraser");
        ButtonIconUtil.applyIcon(backupRefreshButton, "fas-sync-alt");
        ButtonIconUtil.applyIcon(browseRestoreFileButton, "fas-file-upload");
        ButtonIconUtil.applyIcon(restoreButton, "fas-upload");
        ButtonIconUtil.applyIcon(restoreSearchButton, "fas-search");
        ButtonIconUtil.applyIcon(restoreClearButton, "fas-eraser");
        ButtonIconUtil.applyIcon(restoreRefreshButton, "fas-sync-alt");
    }

    private void configureFilters() {
        DatePickerUtil.applySystemDateFormat(backupDateFromPicker);
        DatePickerUtil.applySystemDateFormat(backupDateToPicker);
        DatePickerUtil.applySystemDateFormat(restoreDateFromPicker);
        DatePickerUtil.applySystemDateFormat(restoreDateToPicker);
        backupTypeFilterComboBox.setItems(FXCollections.observableArrayList(FILTER_ALL, "MANUAL", "AUTO", "PRE_RESTORE"));
        backupTypeFilterComboBox.setValue(FILTER_ALL);
        backupStatusFilterComboBox.setItems(FXCollections.observableArrayList(FILTER_ALL, "SUCCESS", "FAILED"));
        backupStatusFilterComboBox.setValue(FILTER_ALL);
        restoreStatusFilterComboBox.setItems(FXCollections.observableArrayList(FILTER_ALL, "SUCCESS", "FAILED"));
        restoreStatusFilterComboBox.setValue(FILTER_ALL);
        scheduleAmPmComboBox.setItems(FXCollections.observableArrayList("AM", "PM"));
        scheduleAmPmComboBox.setValue("AM");
    }

    private void configurePermissions() {
        boolean canCreate = permissionGuard.can("backup.create");
        boolean canRestore = permissionGuard.can("backup.restore");
        boolean canManageSettings = permissionGuard.can("backup.settings.manage");
        boolean canView = permissionGuard.can("backup.view");
        canManageBackupSettings = canManageSettings;

        backupNowButton.setVisible(canCreate);
        backupNowButton.setManaged(canCreate);
        backupNowButton.setDisable(!canCreate);
        restoreButton.setVisible(canRestore);
        restoreButton.setManaged(canRestore);
        restoreButton.setDisable(!canRestore);
        browseRestoreFileButton.setDisable(!canRestore);
        restoreBackupFileField.setDisable(!canRestore);
        restoreConfirmationField.setDisable(!canRestore);

        scheduleNameField.setDisable(!canManageSettings);
        scheduleHourSpinner.setDisable(!canManageSettings);
        scheduleMinuteSpinner.setDisable(!canManageSettings);
        scheduleAmPmComboBox.setDisable(!canManageSettings);
        scheduleEnabledCheckBox.setDisable(!canManageSettings);
        updateScheduleModeButtons();
        autoBackupFolderField.setDisable(!canManageSettings);
        browseAutoBackupFolderButton.setDisable(!canManageSettings);
        retentionDaysField.setDisable(!canManageSettings);
        mysqldumpPathField.setDisable(!canManageSettings);
        mysqlClientPathField.setDisable(!canManageSettings);
        saveSettingsButton.setVisible(canManageSettings);
        saveSettingsButton.setManaged(canManageSettings);
        generateAutoBackupScriptButton.setVisible(false);
        generateAutoBackupScriptButton.setManaged(false);
        generateAutoBackupScriptButton.setDisable(true);
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

        backupScheduleTable.setItems(backupSchedules);
        scheduleNameColumn.setCellValueFactory(data -> value(data.getValue().getScheduleName()));
        scheduleTimeColumn.setCellValueFactory(data -> value(formatTime12Hour(data.getValue().getBackupTime())));
        scheduleEnabledColumn.setCellValueFactory(data -> value(data.getValue().isEnabled() ? "Enabled" : "Disabled"));
        scheduleEnabledColumn.setCellFactory(column -> scheduleStatusBadgeCell());
        backupScheduleTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                scheduleNameField.setText(selected.getScheduleName());
                LocalTime backupTime = selected.getBackupTime();
                if (backupTime != null) {
                    scheduleHourSpinner.getValueFactory().setValue(hour12(backupTime));
                    scheduleMinuteSpinner.getValueFactory().setValue(backupTime.getMinute());
                    scheduleAmPmComboBox.setValue(backupTime.getHour() < 12 ? "AM" : "PM");
                }
                scheduleEnabledCheckBox.setSelected(selected.isEnabled());
                toggleScheduleButton.setText(selected.isEnabled() ? "Disable Schedule" : "Enable Schedule");
            }
            updateScheduleModeButtons();
        });
    }

    private void loadSettings() {
        loadSettings(backupSettingsService.getSettings());
    }

    private void configureScheduleTimeInputs() {
        scheduleHourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12, 8));
        scheduleMinuteSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
        scheduleHourSpinner.setEditable(true);
        scheduleMinuteSpinner.setEditable(true);
    }

    private void loadSettings(BackupSettingsDto settings) {
        backupFolderField.setText(nullToBlank(settings.getBackupFolder()));
        autoBackupFolderField.setText(nullToBlank(settings.getBackupFolder()));
        retentionDaysField.setText(Integer.toString(settings.getRetentionDays()));
        mysqldumpPathField.setText(nullToBlank(settings.getMysqldumpPath()));
        mysqlClientPathField.setText(nullToBlank(settings.getMysqlClientPath()));
        schedulerTodoLabel.setText("");
        schedulerTodoLabel.setVisible(false);
        schedulerTodoLabel.setManaged(false);
        generateAutoBackupScriptButton.setVisible(false);
        generateAutoBackupScriptButton.setManaged(false);
        autoBackupScriptPathLabel.setVisible(false);
        autoBackupScriptPathLabel.setManaged(false);
        autoBackupSchedulerCommandArea.setVisible(false);
        autoBackupSchedulerCommandArea.setManaged(false);
    }

    private void loadHistory() {
        if (permissionGuard.can("backup.view")) {
            loadBackupLogs();
            loadRestoreLogs();
        }
    }

    private void loadSchedules() {
        backupSchedules.setAll(backupScheduleService.getSchedules());
        clearScheduleForm();
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
        settings.setBackupFolder(autoBackupFolderField.getText());
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

    private BackupScheduleDto readScheduleForm(BackupScheduleDto selected) {
        BackupScheduleDto schedule = new BackupScheduleDto();
        schedule.setId(selected == null ? null : selected.getId());
        schedule.setScheduleName(scheduleNameField.getText());
        try {
            schedule.setBackupTime(readScheduleTime());
        } catch (RuntimeException exception) {
            showError("Invalid schedule time", processingMessage(exception));
            return null;
        }
        schedule.setEnabled(scheduleEnabledCheckBox.isSelected());
        return schedule;
    }

    private LocalTime readScheduleTime() {
        try {
            int hour = spinnerEditorValue(scheduleHourSpinner);
            int minute = spinnerEditorValue(scheduleMinuteSpinner);
            return LocalTime.of(hour24(hour, scheduleAmPmComboBox.getValue()), minute);
        } catch (RuntimeException exception) {
            throw new BackupScheduleService.BackupScheduleException("Backup time is invalid.");
        }
    }

    private int spinnerEditorValue(Spinner<Integer> spinner) {
        Integer value = spinner.getValueFactory().getConverter().fromString(spinner.getEditor().getText());
        spinner.getValueFactory().setValue(value);
        return value;
    }

    private void clearScheduleForm() {
        scheduleNameField.clear();
        scheduleHourSpinner.getValueFactory().setValue(8);
        scheduleMinuteSpinner.getValueFactory().setValue(0);
        scheduleAmPmComboBox.setValue("AM");
        scheduleEnabledCheckBox.setSelected(true);
        toggleScheduleButton.setText("Enable/Disable Schedule");
        backupScheduleTable.getSelectionModel().clearSelection();
        updateScheduleModeButtons();
    }

    private void updateScheduleModeButtons() {
        boolean selected = backupScheduleTable != null
                && backupScheduleTable.getSelectionModel().getSelectedItem() != null;
        addScheduleButton.setDisable(!canManageBackupSettings || selected);
        updateScheduleButton.setDisable(!canManageBackupSettings || !selected);
        clearScheduleButton.setDisable(!canManageBackupSettings);
        toggleScheduleButton.setDisable(!canManageBackupSettings || !selected);
        deleteScheduleButton.setDisable(!canManageBackupSettings || !selected);
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

    private TableCell<BackupScheduleDto, String> scheduleStatusBadgeCell() {
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
                badge.getStyleClass().add("Enabled".equals(status) ? "status-active" : "status-inactive");
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
                        "status-correction-note", "status-auto");
                switch (backupType) {
                    case "MANUAL" -> badge.getStyleClass().add("status-active");
                    case "AUTO" -> badge.getStyleClass().add("status-auto");
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
        return value == null ? "" : dateTimeFormatter.formatDateTime(value);
    }

    private String formatTime12Hour(LocalTime value) {
        return value == null ? "" : dateTimeFormatter.formatTime(value);
    }

    private int hour12(LocalTime value) {
        int hour = value.getHour() % 12;
        return hour == 0 ? 12 : hour;
    }

    private int hour24(int hour12, String amPm) {
        int normalizedHour = hour12 % 12;
        return "PM".equals(amPm) ? normalizedHour + 12 : normalizedHour;
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
