package com.churchmanagement.controller;

import com.churchmanagement.dto.AtCommandResult;
import com.churchmanagement.dto.ComPortDto;
import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.dto.SystemSettingDto;
import com.churchmanagement.dto.UpdateSystemSettingRequest;
import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.enums.SmsSendStatus;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.repository.SmsSettingsRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.ActivityLogService;
import com.churchmanagement.service.AtCommandService;
import com.churchmanagement.service.SerialPortService;
import com.churchmanagement.service.SmsServiceFactory;
import com.churchmanagement.service.SystemSettingService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ProcessingDialog;
import com.churchmanagement.util.ThemeService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class SettingsController {
    private static final int MODEM_DETECTION_TIMEOUT_MILLIS = 10_000;
    private static final int MODEM_PORT_PROBE_TIMEOUT_MILLIS = 1_500;
    private static final int MODEM_TEST_TIMEOUT_MILLIS = 10_000;

    private final SystemSettingService systemSettingService = new SystemSettingService();
    private final SmsSettingsRepository smsSettingsRepository = new SmsSettingsRepository();
    private final SerialPortService serialPortService = new SerialPortService();
    private final AtCommandService atCommandService = new AtCommandService(serialPortService);
    private final ActivityLogService activityLogService = new ActivityLogService();
    private final SmsLogRepository smsLogRepository = new SmsLogRepository();
    private final SmsServiceFactory smsServiceFactory = new SmsServiceFactory();

    private AuthenticatedUser currentUser;

    @FXML private TabPane settingsTabPane;
    @FXML private TextField organizationNameField;
    @FXML private TextArea organizationAddressArea;
    @FXML private TextField organizationPhoneField;
    @FXML private Label organizationNameErrorLabel;
    @FXML private TextField receiptPrefixField;
    @FXML private TextField receiptPaddingField;
    @FXML private CheckBox receiptAllowBackWeekCheckBox;
    @FXML private ComboBox<String> receiptLanguageComboBox;
    @FXML private Label receiptPaddingErrorLabel;
    @FXML private Label receiptLanguageErrorLabel;
    @FXML private CheckBox smsEnabledCheckBox;
    @FXML private ComboBox<SmsSettings.GatewayType> gatewayTypeComboBox;
    @FXML private CheckBox smsRetryEnabledCheckBox;
    @FXML private TextField smsRetryMaxAttemptsField;
    @FXML private Label smsRetryMaxAttemptsErrorLabel;
    @FXML private ComboBox<ComPortDto> comPortComboBox;
    @FXML private ComboBox<Integer> baudRateComboBox;
    @FXML private TextArea modemResponseTextArea;
    @FXML private Button saveSmsSettingsButton;
    @FXML private Button detectComPortsButton;
    @FXML private Button testModemButton;
    @FXML private TextField testSmsMobileNumberField;
    @FXML private TextArea testSmsMessageTextArea;
    @FXML private Button sendTestSmsButton;
    @FXML private TextField dateFormatField;
    @FXML private TextField timeFormatField;
    @FXML private TextField reportExportFolderField;
    @FXML private Button browseReportExportFolderButton;
    @FXML private CheckBox pdfReportChartsEnabledCheckBox;
    @FXML private ComboBox<String> themeComboBox;
    @FXML private Label themeErrorLabel;

    @FXML
    private void initialize() {
        currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("Please sign in to manage settings."));
        new PermissionGuard(currentUser).require("settings.menu.view");

        receiptLanguageComboBox.getItems().setAll("ENGLISH", "SINHALA", "TAMIL");
        themeComboBox.getItems().setAll("LIGHT", "DARK");
        gatewayTypeComboBox.getItems().setAll(SmsSettings.GatewayType.values());
        baudRateComboBox.getItems().setAll(9600, 19200, 38400, 115200);
        configureButtonIcons();
        comPortComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ComPortDto port) {
                return port == null ? "" : port.displayName();
            }

            @Override
            public ComPortDto fromString(String value) {
                return null;
            }
        });
        loadSettings();
        loadSmsGatewaySettings();
        testSmsMessageTextArea.setText("Test SMS from Church Management System.");
    }

    @FXML
    private void saveGeneralSettings() {
        saveSettings(List.of(
                request("organization.name", organizationNameField.getText()),
                request("organization.address", organizationAddressArea.getText()),
                request("organization.phone", organizationPhoneField.getText())
        ));
    }

    @FXML
    private void saveReceiptSettings() {
        saveSettings(List.of(
                request("receipt.number.prefix", receiptPrefixField.getText()),
                request("receipt.sequence.padding", receiptPaddingField.getText()),
                request("receipt.allow.back.week", String.valueOf(receiptAllowBackWeekCheckBox.isSelected())),
                request("receipt.default.language", receiptLanguageComboBox.getValue())
        ));
    }

    @FXML
    private void saveSmsSettings() {
        Integer baudRate = parseBaudRate();
        if (baudRate == null) {
            return;
        }

        ProcessingDialog.run("Save Settings", "Saving SMS settings...",
                () -> {
                    List<SystemSettingDto> saved = systemSettingService.updateSettings(List.of(
                            request("sms.enabled", String.valueOf(smsEnabledCheckBox.isSelected())),
                            request("sms.gateway.type", gatewayTypeComboBox.getValue().name()),
                            request("sms.retry.enabled", String.valueOf(smsRetryEnabledCheckBox.isSelected())),
                            request("sms.retry.max.attempts", smsRetryMaxAttemptsField.getText())
                    ));
                    SmsSettings settings = smsSettingsRepository.saveSettings(
                            smsEnabledCheckBox.isSelected(),
                            gatewayTypeComboBox.getValue(),
                            selectedComPortName(),
                            baudRate
                    );
                    activityLogService.logSmsSettingsUpdated(currentUser.getUserId(), settings.isSmsEnabled(),
                            settings.getGatewayType().name());
                    return saved;
                },
                settings -> {
                    applySettings(settings);
                    applyThemeToScene();
                    clearErrors();
                    showInfo("Settings updated", "Settings updated successfully.");
                },
                throwable -> showValidationError(processingMessage(throwable)));
    }

    @FXML
    private void saveSystemSettings() {
        saveSettings(List.of(
                request("system.date.format", dateFormatField.getText()),
                request("system.time.format", timeFormatField.getText()),
                request("reports.export.folder", reportExportFolderField.getText()),
                request("reports.pdf.charts.enabled", String.valueOf(pdfReportChartsEnabledCheckBox.isSelected())),
                request("system.theme", themeComboBox.getValue())
        ));
    }

    @FXML
    private void browseReportExportFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Report Export Folder");
        String currentPath = reportExportFolderField.getText();
        if (currentPath != null && !currentPath.isBlank()) {
            File currentFolder = new File(currentPath);
            if (currentFolder.isDirectory()) {
                chooser.setInitialDirectory(currentFolder.getAbsoluteFile());
            }
        }
        File selected = chooser.showDialog(window());
        if (selected != null) {
            reportExportFolderField.setText(selected.getAbsolutePath());
        }
    }

    @FXML
    private void detectComPorts() {
        Integer baudRate = parseBaudRate();
        if (baudRate == null) {
            return;
        }

        ProcessingDialog.run("Detect COM Ports", "Detecting SIM dongle modem ports...",
                () -> {
                    List<ComPortDto> availablePorts = serialPortService.listAvailablePorts();
                    ComPortDetectionResult detectionResult = detectModemPorts(availablePorts, baudRate);
                    activityLogService.logSmsComPortsDetected(currentUser.getUserId(),
                            detectionResult.modemPorts().size());
                    return detectionResult;
                },
                result -> {
                    comPortComboBox.getItems().setAll(result.modemPorts());
                    if (result.availablePortCount() == 0) {
                        modemResponseTextArea.setText("No COM ports found.");
                        showError("No COM ports found", "Plug in the SIM dongle and try detecting COM ports again.");
                        return;
                    }
                    if (result.modemPorts().isEmpty()) {
                        modemResponseTextArea.setText("Modem not responding.");
                        showError("Modem not responding", "No detected COM port responded to the AT command.");
                        return;
                    }
                    comPortComboBox.getSelectionModel().selectFirst();
                    String timeoutNote = result.timedOut()
                            ? "Detection stopped after 10 seconds." + System.lineSeparator()
                            : "";
                    modemResponseTextArea.setText(timeoutNote + formatPorts(result.modemPorts()));
                },
                throwable -> showError("COM port detection failed", processingMessage(throwable)));
    }

    @FXML
    private void testModem() {
        String comPort = selectedComPortName();
        Integer baudRate = parseBaudRate();
        if (baudRate == null) {
            return;
        }

        ProcessingDialog.run("Test Modem", "Testing SIM dongle with AT commands...",
                () -> {
                    AtCommandResult result = testModemWithTimeout(comPort, baudRate);
                    if (result.isModemDetected()) {
                        activityLogService.logSmsModemTestSuccess(currentUser.getUserId(), comPort, baudRate);
                    } else {
                        activityLogService.logSmsModemTestFailed(currentUser.getUserId(), comPort, baudRate,
                                result.getMessage());
                    }
                    return result;
                },
                result -> {
                    modemResponseTextArea.setText(result.getResponse() == null || result.getResponse().isBlank()
                            ? result.getMessage()
                            : result.getResponse());
                    if (result.isModemDetected()) {
                        showInfo("Modem detected", result.getMessage());
                    } else {
                        showError("Modem test failed", result.getMessage());
                    }
                },
                throwable -> showError("Modem test failed", processingMessage(throwable)));
    }

    @FXML
    private void testSms() {
        String mobileNumber = testSmsMobileNumberField.getText();
        String message = testSmsMessageTextArea.getText();
        Integer baudRate = parseBaudRate();
        if (baudRate == null) {
            return;
        }

        ProcessingDialog.run("Test SMS", "Sending test SMS...",
                () -> {
                    smsSettingsRepository.saveSettings(
                            smsEnabledCheckBox.isSelected(),
                            gatewayTypeComboBox.getValue(),
                            selectedComPortName(),
                            baudRate
                    );
                    SmsResult result = smsServiceFactory.createRoutingSmsService().sendSms(mobileNumber, message);
                    LocalDateTime now = LocalDateTime.now();
                    smsLogRepository.insertSmsLog(null, null, mobileNumber, message, result.getProvider(),
                            result.isSuccess() ? result.getSendStatus() : SmsSendStatus.FAILED,
                            result.isSuccess() ? result.getDeliveryStatus() : SmsDeliveryStatus.FAILED,
                            result.getModemMessageReference(), result.getModemRawResponse(), null,
                            result.getErrorCode(), result.isSuccess() ? null : result.getErrorMessage(),
                            result.getAttemptCount(), now, result.getSentAt(), now);
                    if (result.isSuccess()) {
                        activityLogService.logSmsTestSent(currentUser.getUserId(), mobileNumber, result.getProvider());
                    } else {
                        activityLogService.logSmsTestFailed(currentUser.getUserId(), mobileNumber, result.getMessage());
                    }
                    return result;
                },
                result -> {
                    if (result.isSuccess()) {
                        showInfo("Test SMS sent", testSmsResultMessage(result));
                    } else {
                        showError("Test SMS failed", testSmsResultMessage(result));
                    }
                },
                throwable -> showError("Test SMS failed", processingMessage(throwable)));
    }

    private void loadSettings() {
        applySettings(systemSettingService.loadSettings());
    }

    private void applySettings(List<SystemSettingDto> settings) {
        Map<String, String> values = settings.stream()
                .collect(Collectors.toMap(SystemSettingDto::getSettingKey,
                        setting -> nullToBlank(setting.getSettingValue())));
        organizationNameField.setText(values.get("organization.name"));
        organizationAddressArea.setText(values.get("organization.address"));
        organizationPhoneField.setText(values.get("organization.phone"));
        receiptPrefixField.setText(values.get("receipt.number.prefix"));
        receiptPaddingField.setText(values.get("receipt.sequence.padding"));
        receiptAllowBackWeekCheckBox.setSelected(Boolean.parseBoolean(values.get("receipt.allow.back.week")));
        receiptLanguageComboBox.setValue(defaultValue(values.get("receipt.default.language"), "ENGLISH"));
        smsEnabledCheckBox.setSelected(Boolean.parseBoolean(values.get("sms.enabled")));
        gatewayTypeComboBox.setValue(parseGatewayType(values.get("sms.gateway.type")));
        smsRetryEnabledCheckBox.setSelected(Boolean.parseBoolean(values.get("sms.retry.enabled")));
        smsRetryMaxAttemptsField.setText(values.get("sms.retry.max.attempts"));
        dateFormatField.setText(values.get("system.date.format"));
        timeFormatField.setText(values.get("system.time.format"));
        reportExportFolderField.setText(defaultValue(values.get("reports.export.folder"), "./reports"));
        pdfReportChartsEnabledCheckBox.setSelected(Boolean.parseBoolean(defaultValue(
                values.get("reports.pdf.charts.enabled"), "true")));
        themeComboBox.setValue(defaultValue(values.get("system.theme"), "LIGHT"));
    }

    private void configureButtonIcons() {
        ButtonIconUtil.applyIcon(browseReportExportFolderButton, "fas-folder-open");
    }

    private void loadSmsGatewaySettings() {
        SmsSettings settings = smsSettingsRepository.getSettings();
        if (settings.getComPort() != null && !settings.getComPort().isBlank()) {
            comPortComboBox.getItems().setAll(new ComPortDto(settings.getComPort(), "Saved COM port",
                    settings.getComPort()));
            comPortComboBox.getSelectionModel().selectFirst();
        }
        baudRateComboBox.setValue(settings.getBaudRate() == null ? 9600 : settings.getBaudRate());
    }

    private void saveSettings(List<UpdateSystemSettingRequest> requests) {
        ProcessingDialog.run("Save Settings", "Saving settings...",
                () -> systemSettingService.updateSettings(requests),
                settings -> {
                    applySettings(settings);
                    applyThemeToScene();
                    clearErrors();
                    showInfo("Settings updated", "Settings updated successfully.");
                },
                throwable -> showValidationError(processingMessage(throwable)));
    }

    private UpdateSystemSettingRequest request(String key, String value) {
        return new UpdateSystemSettingRequest(key, value);
    }

    private void showValidationError(String message) {
        clearErrors();
        if (message.contains("Organization name")) {
            organizationNameErrorLabel.setText(message);
        } else if (message.contains("padding")) {
            receiptPaddingErrorLabel.setText(message);
        } else if (message.contains("retry max attempts")) {
            smsRetryMaxAttemptsErrorLabel.setText(message);
        } else if (message.contains("language")) {
            receiptLanguageErrorLabel.setText(message);
        } else if (message.contains("theme")) {
            themeErrorLabel.setText(message);
        }
        showError("Settings update failed", message);
    }

    private void clearErrors() {
        organizationNameErrorLabel.setText("");
        receiptPaddingErrorLabel.setText("");
        receiptLanguageErrorLabel.setText("");
        smsRetryMaxAttemptsErrorLabel.setText("");
        themeErrorLabel.setText("");
    }

    private void applyThemeToScene() {
        if (settingsTabPane != null && settingsTabPane.getScene() != null) {
            new ThemeService().applyConfiguredTheme(settingsTabPane.getScene().getRoot());
        }
    }

    private Window window() {
        return settingsTabPane.getScene().getWindow();
    }

    private Integer parseBaudRate() {
        Integer baudRate = baudRateComboBox.getValue();
        if (baudRate == null) {
            showError("Invalid baud rate", "Select a baud rate.");
            return null;
        }
        return baudRate;
    }

    private String selectedComPortName() {
        ComPortDto selected = comPortComboBox.getValue();
        return selected == null ? null : selected.getSystemPortName();
    }

    private String formatPorts(List<ComPortDto> ports) {
        StringBuilder builder = new StringBuilder("Detected COM ports:");
        for (ComPortDto port : ports) {
            builder.append(System.lineSeparator())
                    .append("- Port name: ").append(blankToDash(port.getPortName()))
                    .append(", Description: ").append(blankToDash(port.getDescription()))
                    .append(", System port: ").append(blankToDash(port.getSystemPortName()));
        }
        return builder.toString();
    }

    private SmsSettings.GatewayType parseGatewayType(String value) {
        try {
            return value == null || value.isBlank()
                    ? SmsSettings.GatewayType.MOCK
                    : SmsSettings.GatewayType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return SmsSettings.GatewayType.MOCK;
        }
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
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

    private String testSmsResultMessage(SmsResult result) {
        StringBuilder message = new StringBuilder(result.getMessage());
        message.append(System.lineSeparator()).append("Send status: ").append(result.getSendStatus());
        message.append(System.lineSeparator()).append("Delivery status: ").append(result.getDeliveryStatus());
        message.append(System.lineSeparator()).append("Modem ref: ").append(blankToDash(result.getModemMessageReference()));
        if (!result.isSuccess()) {
            message.append(System.lineSeparator()).append("Error: ")
                    .append(blankToDash(result.getErrorMessage()));
        }
        return message.toString();
    }

    private ComPortDetectionResult detectModemPorts(List<ComPortDto> availablePorts, int baudRate) {
        long deadline = System.currentTimeMillis() + MODEM_DETECTION_TIMEOUT_MILLIS;
        List<ComPortDto> modemPorts = new ArrayList<>();
        ExecutorService probeExecutor = Executors.newCachedThreadPool(daemonThreadFactory("sms-modem-probe"));
        boolean timedOut = false;
        try {
            for (ComPortDto port : availablePorts) {
                if (System.currentTimeMillis() >= deadline) {
                    timedOut = true;
                    break;
                }
                if (probePort(probeExecutor, port, baudRate, deadline)) {
                    modemPorts.add(port);
                }
            }
        } finally {
            probeExecutor.shutdownNow();
        }
        return new ComPortDetectionResult(availablePorts.size(), modemPorts, timedOut);
    }

    private AtCommandResult testModemWithTimeout(String comPort, int baudRate) {
        ExecutorService testExecutor = Executors.newSingleThreadExecutor(daemonThreadFactory("sms-modem-test"));
        Future<AtCommandResult> test = testExecutor.submit((Callable<AtCommandResult>) () ->
                atCommandService.testModem(comPort, baudRate));
        try {
            return test.get(MODEM_TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new AtCommandResult(false, "AT command timeout.", "", AtCommandService.TEST_COMMANDS);
        } catch (ExecutionException exception) {
            return new AtCommandResult(false, processingMessage(exception), "", AtCommandService.TEST_COMMANDS);
        } catch (TimeoutException exception) {
            test.cancel(true);
            return new AtCommandResult(false, "AT command timeout.", "", AtCommandService.TEST_COMMANDS);
        } finally {
            testExecutor.shutdownNow();
        }
    }

    private boolean probePort(ExecutorService probeExecutor, ComPortDto port, int baudRate, long deadline) {
        int timeoutMillis = probeTimeout(deadline);
        Future<Boolean> probe = probeExecutor.submit((Callable<Boolean>) () ->
                atCommandService.isModemPort(port.getSystemPortName(), baudRate, timeoutMillis));
        try {
            return probe.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException exception) {
            probe.cancel(true);
            return false;
        }
    }

    private ThreadFactory daemonThreadFactory(String namePrefix) {
        return runnable -> {
            Thread thread = new Thread(runnable, namePrefix + "-" + System.nanoTime());
            thread.setDaemon(true);
            return thread;
        };
    }

    private int probeTimeout(long deadline) {
        long remaining = deadline - System.currentTimeMillis();
        return (int) Math.min(MODEM_PORT_PROBE_TIMEOUT_MILLIS, Math.max(250, remaining));
    }

    private record ComPortDetectionResult(int availablePortCount, List<ComPortDto> modemPorts, boolean timedOut) {
    }
}
