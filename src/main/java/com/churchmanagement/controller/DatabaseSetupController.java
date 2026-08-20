package com.churchmanagement.controller;

import com.churchmanagement.dto.DatabaseSetupDto;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.service.DatabaseSetupService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.ThemePreferenceStore;
import com.churchmanagement.util.ThemeService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.concurrent.atomic.AtomicBoolean;

public class DatabaseSetupController {

    @FXML private StackPane setupRoot;
    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField databaseNameField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private Button togglePasswordButton;
    @FXML private CheckBox runMigrationsCheckBox;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator testSpinner;
    @FXML private Button testButton;
    @FXML private Button saveButton;

    @FXML private Button closeButton;
    @FXML private Label rawDetailLabel;

    private final DatabaseSetupService setupService = new DatabaseSetupService();
    private final AtomicBoolean testing = new AtomicBoolean(false);
    private boolean passwordVisible;
    private boolean testPassed;
    private Runnable onComplete;
    private Runnable onCancel;

    /** Called by MainApplication after the scene is shown. */
    public void setOnComplete(Runnable onComplete) {
        this.onComplete = onComplete;
    }

    /** Called by MainApplication; invoked when the user closes the wizard without saving. */
    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    @FXML
    private void handleClose() {
        if (onCancel != null) {
            onCancel.run();
        }
    }

    @FXML
    private void initialize() {
        applyPersistedTheme();
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        setPasswordVisible(false);
        ButtonIconUtil.applyIcon(closeButton, "fas-times");
        closeButton.setText("");
        ButtonIconUtil.applyIcon(testButton, "fas-plug");
        ButtonIconUtil.applyIcon(saveButton, "fas-save");
        saveButton.setDisable(true);
        testSpinner.setVisible(false);
        testSpinner.setManaged(false);
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
        rawDetailLabel.setVisible(false);
        rawDetailLabel.setManaged(false);
        prefillForm();
    }

    @FXML
    private void handleTest() {
        if (!testing.compareAndSet(false, true)) {
            return;
        }
        String validationError = validateForm();
        if (validationError != null) {
            showStatus(validationError, Status.ERROR);
            testing.set(false);
            return;
        }

        testPassed = false;
        saveButton.setDisable(true);
        showStatus("Testing connection…", Status.INFO);
        setFormDisabled(true);
        showSpinner(true);

        DatabaseSetupDto dto = buildDto();
        Thread worker = new Thread(() -> {
            try {
                setupService.testConnection(dto);
                Platform.runLater(() -> {
                    showStatus("Connection successful.", Status.SUCCESS);
                    hideRawDetail();
                    testPassed = true;
                    saveButton.setDisable(false);
                    showSpinner(false);
                    setFormDisabled(false);
                });
            } catch (DatabaseException e) {
                String rawDetail = extractRawDetail(e);
                Platform.runLater(() -> {
                    showStatus(e.getMessage(), Status.ERROR);
                    if (rawDetail != null) {
                        showRawDetail(rawDetail);
                    } else {
                        hideRawDetail();
                    }
                    showSpinner(false);
                    setFormDisabled(false);
                });
            } finally {
                testing.set(false);
            }
        }, "db-setup-test");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleSave() {
        if (!testPassed) {
            showStatus("Please test the connection before saving.", Status.ERROR);
            return;
        }
        try {
            setupService.save(buildDto());
            if (onComplete != null) {
                onComplete.run();
            }
        } catch (DatabaseException e) {
            showStatus("Save failed: " + e.getMessage(), Status.ERROR);
        }
    }

    @FXML
    private void togglePasswordVisibility() {
        setPasswordVisible(!passwordVisible);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void applyPersistedTheme() {
        String theme = ThemePreferenceStore.load();
        if ("DARK".equalsIgnoreCase(theme)) {
            setupRoot.getStyleClass().add(ThemeService.DARK_THEME_CLASS);
        } else if ("ORCHID".equalsIgnoreCase(theme)) {
            setupRoot.getStyleClass().add(ThemeService.ORCHID_THEME_CLASS);
        }
    }

    private void prefillForm() {
        DatabaseSetupDto dto = setupService.load();
        hostField.setText(dto.getHost());
        portField.setText(String.valueOf(dto.getPort()));
        databaseNameField.setText(dto.getDatabaseName());
        usernameField.setText(dto.getUsername());
        passwordField.setText(dto.getPassword());
        runMigrationsCheckBox.setSelected(dto.isRunMigrations());
    }

    private String validateForm() {
        if (hostField.getText().isBlank()) {
            return "Server host is required.";
        }
        String portText = portField.getText().strip();
        if (portText.isBlank()) {
            return "Port is required.";
        }
        try {
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) {
                return "Port must be between 1 and 65535.";
            }
        } catch (NumberFormatException e) {
            return "Port must be a number.";
        }
        if (databaseNameField.getText().isBlank()) {
            return "Database name is required.";
        }
        if (usernameField.getText().isBlank()) {
            return "Username is required.";
        }
        return null;
    }

    private DatabaseSetupDto buildDto() {
        DatabaseSetupDto dto = new DatabaseSetupDto();
        dto.setHost(hostField.getText().strip());
        dto.setPort(Integer.parseInt(portField.getText().strip()));
        dto.setDatabaseName(databaseNameField.getText().strip());
        dto.setUsername(usernameField.getText().strip());
        dto.setPassword(passwordField.getText());
        dto.setRunMigrations(runMigrationsCheckBox.isSelected());
        return dto;
    }

    private void showStatus(String message, Status status) {
        statusLabel.setText(message);
        statusLabel.getStyleClass().removeAll(
                "db-setup-status-success", "db-setup-status-error", "db-setup-status-info");
        statusLabel.getStyleClass().add(switch (status) {
            case SUCCESS -> "db-setup-status-success";
            case ERROR   -> "db-setup-status-error";
            case INFO    -> "db-setup-status-info";
        });
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    private void showRawDetail(String detail) {
        rawDetailLabel.setText(detail);
        rawDetailLabel.setVisible(true);
        rawDetailLabel.setManaged(true);
    }

    private void hideRawDetail() {
        rawDetailLabel.setVisible(false);
        rawDetailLabel.setManaged(false);
    }

    /**
     * Walks the exception cause chain to find the deepest non-null message
     * that differs from the friendly top-level message.  Returns null when
     * there is nothing more specific to show.
     */
    private String extractRawDetail(DatabaseException e) {
        String friendly = e.getMessage();
        Throwable cause = e.getCause();
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && !msg.isBlank() && !msg.equals(friendly)) {
                return msg;
            }
            cause = cause.getCause();
        }
        return null;
    }

    private void showSpinner(boolean visible) {
        testSpinner.setVisible(visible);
        testSpinner.setManaged(visible);
        testButton.setDisable(visible);
    }

    private void setFormDisabled(boolean disabled) {
        hostField.setDisable(disabled);
        portField.setDisable(disabled);
        databaseNameField.setDisable(disabled);
        usernameField.setDisable(disabled);
        passwordField.setDisable(disabled);
        visiblePasswordField.setDisable(disabled);
    }

    private void setPasswordVisible(boolean visible) {
        passwordVisible = visible;
        visiblePasswordField.setVisible(visible);
        visiblePasswordField.setManaged(visible);
        passwordField.setVisible(!visible);
        passwordField.setManaged(!visible);
        togglePasswordButton.setGraphic(createPasswordIcon(visible ? "fas-eye-slash" : "fas-eye"));
    }

    private FontIcon createPasswordIcon(String iconLiteral) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("password-toggle-icon");
        return icon;
    }

    private enum Status { SUCCESS, ERROR, INFO }
}
