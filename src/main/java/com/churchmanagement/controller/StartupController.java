package com.churchmanagement.controller;

import com.churchmanagement.config.MigrationRunner;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.repository.SmsSettingsRepository;
import com.churchmanagement.service.AtCommandService;
import com.churchmanagement.service.SerialPortService;
import com.churchmanagement.service.SystemConfigurationCache;
import com.churchmanagement.util.ThemePreferenceStore;
import com.churchmanagement.util.ThemeService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.concurrent.atomic.AtomicBoolean;

public class StartupController {

    @FXML private StackPane startupRoot;
    @FXML private VBox startupCard;
    @FXML private ProgressIndicator spinner;
    @FXML private Label statusLabel;
    @FXML private VBox checkContainer;
    @FXML private Label dbIcon;
    @FXML private Label dbDetail;
    @FXML private Label modemIcon;
    @FXML private Label modemDetail;
    @FXML private Label errorLabel;
    @FXML private HBox buttonBar;
    @FXML private Button checkNowButton;
    @FXML private Button exitButton;

    private Runnable onReady;

    /** True while a check sequence is running — blocks duplicate runs. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Signals the in-flight sleep to abort the auto-transition. */
    private final AtomicBoolean cancelTransition = new AtomicBoolean(false);

    @FXML
    private void initialize() {
        // Prevent StackPane from stretching the card to full screen height.
        startupCard.setMaxHeight(Region.USE_PREF_SIZE);
        // Apply the last-used theme from OS preferences so the startup screen
        // already matches the user's choice before the database is connected.
        applyPersistedTheme();
    }

    private void applyPersistedTheme() {
        String theme = ThemePreferenceStore.load();
        if ("DARK".equalsIgnoreCase(theme)) {
            startupRoot.getStyleClass().add(ThemeService.DARK_THEME_CLASS);
        } else if ("ORCHID".equalsIgnoreCase(theme)) {
            startupRoot.getStyleClass().add(ThemeService.ORCHID_THEME_CLASS);
        }
    }

    public void setOnReady(Runnable onReady) {
        this.onReady = onReady;
    }

    /** Called by MainApplication after the stage is shown. */
    public void startChecks() {
        runChecksAsync();
    }

    @FXML
    private void handleCheckNow() {
        cancelTransition.set(true);
        runChecksAsync();
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }

    // ── Check orchestration ───────────────────────────────────────────────────

    private void runChecksAsync() {
        if (!running.compareAndSet(false, true)) {
            return; // already in progress
        }
        cancelTransition.set(false);

        Platform.runLater(this::resetToLoadingState);

        Thread worker = new Thread(() -> {
            try {
                runChecks();
            } finally {
                running.set(false);
            }
        }, "startup-checker");
        worker.setDaemon(true);
        worker.start();
    }

    private void runChecks() {
        // ── Step 1: database (mandatory) ─────────────────────────────────────
        setStatus("Connecting to database...");
        boolean dbOk = checkDatabase();

        if (!dbOk) {
            return; // error state already shown on FX thread
        }

        // ── Step 2: modem (optional) ──────────────────────────────────────────
        setStatus("Checking modem connection...");
        checkModem();

        // ── Done: reveal buttons and auto-transition after a brief pause ──────
        setStatus("All services checked.");
        Platform.runLater(this::revealButtons);

        // Wait so the user can read the results, then transition unless
        // the user clicked "Check Now" again.
        sleep(1500);
        if (!cancelTransition.get() && onReady != null) {
            Platform.runLater(onReady);
        }
    }

    // ── Individual checks ─────────────────────────────────────────────────────

    private boolean checkDatabase() {
        try {
            MigrationRunner.runMigrations();
            SystemConfigurationCache.getInstance().loadSettings();
            Platform.runLater(() -> {
                revealChecks();
                markSuccess(dbIcon, dbDetail, "Connected");
            });
            return true;
        } catch (Exception e) {
            String hint = friendlyDbMessage(e);
            Platform.runLater(() -> {
                revealChecks();
                markError(dbIcon, dbDetail, "Connection failed");
                showFatalError(
                        "Cannot connect to the database.\n"
                        + "Make sure MySQL is running and the credentials in "
                        + "application.properties are correct.\n\n" + hint);
            });
            return false;
        }
    }

    private void checkModem() {
        try {
            SmsSettings sms = new SmsSettingsRepository().getSettings();

            if (!sms.isSmsEnabled()) {
                Platform.runLater(() -> markNeutral(modemIcon, modemDetail, "SMS is disabled"));
                return;
            }
            if (sms.getGatewayType() == SmsSettings.GatewayType.MOCK) {
                Platform.runLater(() -> markNeutral(modemIcon, modemDetail, "Using simulation gateway"));
                return;
            }

            String port = sms.getComPort();
            if (port == null || port.isBlank()) {
                Platform.runLater(() -> markWarning(modemIcon, modemDetail, "No COM port configured"));
                return;
            }

            boolean ok = new AtCommandService(new SerialPortService()).isModemPort(port, sms.getBaudRate());
            if (ok) {
                Platform.runLater(() -> markSuccess(modemIcon, modemDetail, "Connected on " + port));
            } else {
                Platform.runLater(() -> markWarning(modemIcon, modemDetail, "Not responding on " + port));
            }
        } catch (Exception e) {
            Platform.runLater(() -> markWarning(modemIcon, modemDetail, "Could not probe modem"));
        }
    }

    // ── FX-thread UI helpers ─────────────────────────────────────────────────

    private void resetToLoadingState() {
        // Card is invisible during loading — no background, no border, no shadow.
        startupCard.getStyleClass().remove("startup-card");

        // Restore spinner
        spinner.setVisible(true);
        spinner.setManaged(true);

        // Reset check icons to pending
        resetIcon(dbIcon);
        dbDetail.setText("");
        resetIcon(modemIcon);
        modemDetail.setText("");

        // Hide results/error/buttons from previous run
        checkContainer.setVisible(false);
        checkContainer.setManaged(false);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        exitButton.setVisible(false);
        exitButton.setManaged(false);
        buttonBar.setVisible(false);
        buttonBar.setManaged(false);

        // Disable button while running
        checkNowButton.setDisable(true);
        statusLabel.setText("Initializing...");
    }

    private void revealChecks() {
        // Card becomes visible now that there are results to show.
        if (!startupCard.getStyleClass().contains("startup-card")) {
            startupCard.getStyleClass().add("startup-card");
        }
        checkContainer.setVisible(true);
        checkContainer.setManaged(true);
    }

    private void revealButtons() {
        spinner.setVisible(false);
        spinner.setManaged(false);
        checkNowButton.setDisable(false);
        buttonBar.setVisible(true);
        buttonBar.setManaged(true);
    }

    private void showFatalError(String message) {
        spinner.setVisible(false);
        spinner.setManaged(false);
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        exitButton.setVisible(true);
        exitButton.setManaged(true);
        checkNowButton.setDisable(false);
        buttonBar.setVisible(true);
        buttonBar.setManaged(true);
    }

    private void markSuccess(Label icon, Label detail, String text) {
        icon.getStyleClass().setAll("startup-check-icon-success");
        icon.setText("✓");
        detail.setText(text);
    }

    private void markError(Label icon, Label detail, String text) {
        icon.getStyleClass().setAll("startup-check-icon-error");
        icon.setText("✗");
        detail.setText(text);
    }

    private void markWarning(Label icon, Label detail, String text) {
        icon.getStyleClass().setAll("startup-check-icon-warning");
        icon.setText("⚠");
        detail.setText(text);
    }

    private void markNeutral(Label icon, Label detail, String text) {
        icon.getStyleClass().setAll("startup-check-icon-neutral");
        icon.setText("—");
        detail.setText(text);
    }

    private void resetIcon(Label icon) {
        icon.getStyleClass().setAll("startup-check-icon-pending");
        icon.setText("●");
    }

    // ── Background-thread helpers ─────────────────────────────────────────────

    private void setStatus(String text) {
        Platform.runLater(() -> statusLabel.setText(text));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String friendlyDbMessage(Exception e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        Throwable cause = e.getCause();
        String causeMsg = cause == null ? "" : (cause.getMessage() == null ? "" : cause.getMessage());
        String combined = msg + " " + causeMsg;

        if (combined.contains("Communications link failure") || combined.contains("Connection refused")) {
            return "MySQL does not appear to be running.";
        }
        if (combined.contains("Access denied")) {
            return "Access denied — check username and password.";
        }
        if (combined.contains("Unknown database")) {
            return "The configured database does not exist.";
        }
        return msg.length() > 180 ? msg.substring(0, 180) + "…" : msg;
    }
}
