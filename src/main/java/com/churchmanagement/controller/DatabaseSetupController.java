package com.churchmanagement.controller;

import com.churchmanagement.dto.DatabaseSetupDto;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.service.DatabaseSetupService;
import com.churchmanagement.service.DatabaseSetupService.InitStep;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.ThemePreferenceStore;
import com.churchmanagement.util.ThemeService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
    @FXML private VBox stepsContainer;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator testSpinner;
    @FXML private Button testButton;
    @FXML private Button saveButton;
    @FXML private Button closeButton;

    private final DatabaseSetupService setupService = new DatabaseSetupService();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean passwordVisible;
    private boolean initSucceeded;
    private Runnable onComplete;
    private Runnable onCancel;

    public void setOnComplete(Runnable onComplete) {
        this.onComplete = onComplete;
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    @FXML
    private void initialize() {
        applyPersistedTheme();
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        setPasswordVisible(false);
        ButtonIconUtil.applyIcon(closeButton, "fas-times");
        closeButton.setText("");
        ButtonIconUtil.applyIcon(testButton, "fas-database");
        ButtonIconUtil.applyIcon(saveButton, "fas-save");
        saveButton.setDisable(true);
        testSpinner.setVisible(false);
        testSpinner.setManaged(false);
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
        stepsContainer.setVisible(false);
        stepsContainer.setManaged(false);
        prefillForm();
    }

    @FXML
    private void handleInitialize() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        String validationError = validateForm();
        if (validationError != null) {
            showStatus(validationError, Status.ERROR);
            running.set(false);
            return;
        }

        // Reset previous results
        initSucceeded = false;
        saveButton.setDisable(true);
        clearSteps();
        hideStatus();
        setFormDisabled(true);
        showSpinner(true);

        DatabaseSetupDto dto = buildDto();
        Thread worker = new Thread(() -> {
            try {
                setupService.initializeDatabase(dto, step ->
                        Platform.runLater(() -> addStepRow(step)));
                Platform.runLater(() -> {
                    showStatus("Database initialized successfully.", Status.SUCCESS);
                    initSucceeded = true;
                    saveButton.setDisable(false);
                    showSpinner(false);
                    setFormDisabled(false);
                });
            } catch (DatabaseException e) {
                Platform.runLater(() -> {
                    showStatus(e.getMessage(), Status.ERROR);
                    showSpinner(false);
                    setFormDisabled(false);
                });
            } finally {
                running.set(false);
            }
        }, "db-init");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void handleSave() {
        if (!initSucceeded) {
            showStatus("Please initialize the database first.", Status.ERROR);
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
    private void handleClose() {
        if (onCancel != null) {
            onCancel.run();
        }
    }

    @FXML
    private void togglePasswordVisibility() {
        setPasswordVisible(!passwordVisible);
    }

    // ── Step rows ─────────────────────────────────────────────────────────────

    private void addStepRow(InitStep step) {
        Label icon = new Label();
        Label detail = new Label(step.label() + " — " + step.detail());
        detail.setWrapText(true);
        detail.setMaxWidth(Double.MAX_VALUE);

        switch (step.status()) {
            case SUCCESS -> {
                icon.setText("✓");
                icon.getStyleClass().setAll("startup-check-icon-success");
                detail.getStyleClass().setAll("startup-check-detail");
            }
            case WARNING -> {
                icon.setText("⚠");
                icon.getStyleClass().setAll("startup-check-icon-warning");
                detail.getStyleClass().setAll("startup-check-detail");
            }
            case FAILURE -> {
                icon.setText("✗");
                icon.getStyleClass().setAll("startup-check-icon-error");
                detail.getStyleClass().setAll("startup-check-detail");
            }
        }

        HBox row = new HBox(10, icon, detail);
        row.getStyleClass().add("startup-check-row");
        HBox.setHgrow(detail, javafx.scene.layout.Priority.ALWAYS);
        row.setPadding(new Insets(8, 12, 8, 12));

        stepsContainer.getChildren().add(row);
        if (!stepsContainer.isVisible()) {
            stepsContainer.setVisible(true);
            stepsContainer.setManaged(true);
        }
    }

    private void clearSteps() {
        stepsContainer.getChildren().clear();
        stepsContainer.setVisible(false);
        stepsContainer.setManaged(false);
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
        if (hostField.getText().isBlank()) return "Server host is required.";
        String portText = portField.getText().strip();
        if (portText.isBlank()) return "Port is required.";
        try {
            int port = Integer.parseInt(portText);
            if (port < 1 || port > 65535) return "Port must be between 1 and 65535.";
        } catch (NumberFormatException e) {
            return "Port must be a number.";
        }
        if (databaseNameField.getText().isBlank()) return "Database name is required.";
        if (!databaseNameField.getText().strip().matches("[A-Za-z0-9_]+")) {
            return "Database name must contain only letters, numbers, and underscores.";
        }
        if (usernameField.getText().isBlank()) return "Username is required.";
        if (!usernameField.getText().strip().matches("[A-Za-z0-9_]+")) {
            return "Username must contain only letters, numbers, and underscores.";
        }
        return null;
    }

    /**
     * Builds a DTO from the UI fields.  Admin credentials are loaded from the
     * service (which reads them from {@code application.properties}) so the user
     * never has to type them.
     */
    private DatabaseSetupDto buildDto() {
        // Start from the loaded config to inherit admin credentials.
        DatabaseSetupDto dto = setupService.load();
        // Override with what the user actually typed in the form.
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

    private void hideStatus() {
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
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
