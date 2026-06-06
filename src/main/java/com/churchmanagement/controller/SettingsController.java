package com.churchmanagement.controller;

import com.churchmanagement.dto.AtCommandResult;
import com.churchmanagement.dto.ComPortDto;
import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.repository.SmsSettingsRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.AtCommandService;
import com.churchmanagement.service.ActivityLogService;
import com.churchmanagement.service.MockSmsService;
import com.churchmanagement.service.SerialPortService;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ProcessingDialog;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SettingsController {
    private static final int MODEM_DETECTION_TIMEOUT_MILLIS = 10_000;
    private static final int MODEM_PORT_PROBE_TIMEOUT_MILLIS = 1_500;
    private static final int MODEM_TEST_TIMEOUT_MILLIS = 10_000;

    private final SmsSettingsRepository smsSettingsRepository = new SmsSettingsRepository();
    private final MockSmsService mockSmsService = new MockSmsService();
    private final SerialPortService serialPortService = new SerialPortService();
    private final AtCommandService atCommandService = new AtCommandService(serialPortService);
    private final ActivityLogService activityLogService = new ActivityLogService();

    private AuthenticatedUser currentUser;

    @FXML
    private CheckBox smsEnabledCheckBox;

    @FXML
    private ComboBox<SmsSettings.GatewayType> gatewayTypeComboBox;

    @FXML
    private ComboBox<ComPortDto> comPortComboBox;

    @FXML
    private ComboBox<Integer> baudRateComboBox;

    @FXML
    private TextArea modemResponseTextArea;

    @FXML
    private Button saveSmsSettingsButton;

    @FXML
    private Button testSmsButton;

    @FXML
    private Button detectComPortsButton;

    @FXML
    private Button testModemButton;

    @FXML
    private void initialize() {
        currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("Please sign in to manage settings."));
        new PermissionGuard(currentUser).require("sms.settings.manage");

        gatewayTypeComboBox.getItems().setAll(SmsSettings.GatewayType.values());
        baudRateComboBox.getItems().setAll(9600, 19200, 38400, 115200);
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
        loadSmsSettings();
    }

    @FXML
    private void saveSmsSettings() {
        Integer baudRate = parseBaudRate();
        if (baudRate == null) {
            return;
        }

        ProcessingDialog.run("Save Settings", "Saving SMS settings...",
                () -> {
                    SmsSettings settings = smsSettingsRepository.saveSettings(
                            smsEnabledCheckBox.isSelected(),
                            gatewayTypeComboBox.getValue(),
                            selectedComPortName(),
                            baudRate
                    );
                    activityLogService.logSmsSettingsUpdated(currentUser.getUserId(), settings.isSmsEnabled(),
                            settings.getGatewayType().name());
                    return settings;
                },
                settings -> showInfo("SMS settings saved", "SMS gateway settings were saved."),
                throwable -> showError("SMS settings failed", processingMessage(throwable)));
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
        Dialog<String> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle("Test SMS");
        dialog.setHeaderText("Send test SMS");
        ButtonType sendButton = new ButtonType("Send", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(sendButton, ButtonType.CANCEL);

        TextField mobileNumberField = new TextField();
        mobileNumberField.setPromptText("0771234567 or +94771234567");
        VBox content = new VBox(8, DialogStyler.fieldLabel("Mobile number"), mobileNumberField);
        content.setPrefWidth(360);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(buttonType -> buttonType == sendButton ? mobileNumberField.getText() : null);

        Optional<String> mobileNumber = dialog.showAndWait();
        if (mobileNumber.isEmpty()) {
            return;
        }

        ProcessingDialog.run("Test SMS", "Sending test SMS...",
                () -> {
                    SmsResult result = mockSmsService.sendSms(mobileNumber.get(),
                            "Test SMS from Church Management System.");
                    if (result.isSuccess()) {
                        activityLogService.logSmsTestSent(currentUser.getUserId(), mobileNumber.get(), result.getProvider());
                    } else {
                        activityLogService.logSmsTestFailed(currentUser.getUserId(), mobileNumber.get(), result.getMessage());
                    }
                    return result;
                },
                result -> {
                    if (result.isSuccess()) {
                        showInfo("Test SMS sent", result.getMessage());
                    } else {
                        showError("Test SMS failed", result.getMessage());
                    }
                },
                throwable -> showError("Test SMS failed", processingMessage(throwable)));
    }

    private void loadSmsSettings() {
        SmsSettings settings = smsSettingsRepository.getSettings();
        smsEnabledCheckBox.setSelected(settings.isSmsEnabled());
        gatewayTypeComboBox.setValue(settings.getGatewayType());
        if (settings.getComPort() != null && !settings.getComPort().isBlank()) {
            comPortComboBox.getItems().setAll(new ComPortDto(settings.getComPort(), "Saved COM port",
                    settings.getComPort()));
            comPortComboBox.getSelectionModel().selectFirst();
        }
        baudRateComboBox.setValue(settings.getBaudRate() == null ? 9600 : settings.getBaudRate());
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

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
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
