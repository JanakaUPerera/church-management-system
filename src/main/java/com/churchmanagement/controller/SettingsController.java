package com.churchmanagement.controller;

import com.churchmanagement.dto.SmsResult;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.repository.SmsSettingsRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.ActivityLogService;
import com.churchmanagement.service.MockSmsService;
import com.churchmanagement.util.DialogStyler;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.Optional;

public class SettingsController {
    private final SmsSettingsRepository smsSettingsRepository = new SmsSettingsRepository();
    private final MockSmsService mockSmsService = new MockSmsService();
    private final ActivityLogService activityLogService = new ActivityLogService();

    private AuthenticatedUser currentUser;

    @FXML
    private CheckBox smsEnabledCheckBox;

    @FXML
    private ComboBox<SmsSettings.GatewayType> gatewayTypeComboBox;

    @FXML
    private TextField comPortField;

    @FXML
    private TextField baudRateField;

    @FXML
    private Button saveSmsSettingsButton;

    @FXML
    private Button testSmsButton;

    @FXML
    private void initialize() {
        currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("Please sign in to manage settings."));
        new PermissionGuard(currentUser).require("sms.settings.manage");

        gatewayTypeComboBox.getItems().setAll(SmsSettings.GatewayType.values());
        loadSmsSettings();
    }

    @FXML
    private void saveSmsSettings() {
        Integer baudRate = parseBaudRate();
        if (baudRate == null) {
            return;
        }

        SmsSettings settings = smsSettingsRepository.saveSettings(
                smsEnabledCheckBox.isSelected(),
                gatewayTypeComboBox.getValue(),
                comPortField.getText(),
                baudRate
        );
        activityLogService.logSmsSettingsUpdated(currentUser.getUserId(), settings.isSmsEnabled(),
                settings.getGatewayType().name());
        showInfo("SMS settings saved", "SMS gateway settings were saved.");
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

        SmsResult result = mockSmsService.sendSms(mobileNumber.get(), "Test SMS from Church Management System.");
        if (result.isSuccess()) {
            activityLogService.logSmsTestSent(currentUser.getUserId(), mobileNumber.get(), result.getProvider());
            showInfo("Test SMS sent", result.getMessage());
        } else {
            activityLogService.logSmsTestFailed(currentUser.getUserId(), mobileNumber.get(), result.getMessage());
            showError("Test SMS failed", result.getMessage());
        }
    }

    private void loadSmsSettings() {
        SmsSettings settings = smsSettingsRepository.getSettings();
        smsEnabledCheckBox.setSelected(settings.isSmsEnabled());
        gatewayTypeComboBox.setValue(settings.getGatewayType());
        comPortField.setText(settings.getComPort() == null ? "" : settings.getComPort());
        baudRateField.setText(settings.getBaudRate() == null ? "9600" : settings.getBaudRate().toString());
    }

    private Integer parseBaudRate() {
        String value = baudRateField.getText();
        if (value == null || value.isBlank()) {
            return 9600;
        }
        try {
            int baudRate = Integer.parseInt(value.strip());
            if (baudRate <= 0) {
                showError("Invalid baud rate", "Baud rate must be greater than zero.");
                return null;
            }
            return baudRate;
        } catch (NumberFormatException exception) {
            showError("Invalid baud rate", "Baud rate must be a number.");
            return null;
        }
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
}
