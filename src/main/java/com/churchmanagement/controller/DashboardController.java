package com.churchmanagement.controller;

import com.churchmanagement.config.AppConfig;
import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.repository.SmsSettingsRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.ActivityLogService;
import com.churchmanagement.service.AtCommandService;
import com.churchmanagement.service.AutoBackupScheduler;
import com.churchmanagement.service.SerialPortService;
import com.churchmanagement.service.SystemConfigurationCache;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.SystemDateTimeFormatter;
import com.churchmanagement.util.ThemeService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.geometry.Rectangle2D;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DashboardController {
    private static DashboardController activeController;

    private final ActivityLogService activityLogService = new ActivityLogService();
    private final SmsSettingsRepository smsSettingsRepository = new SmsSettingsRepository();
    private final AtCommandService atCommandService = new AtCommandService(new SerialPortService());
    private AuthenticatedUser currentUser;
    private PermissionGuard permissionGuard;
    private List<MenuDefinition> menuDefinitions;
    private boolean sidebarCollapsed;
    private double windowDragOffsetX;
    private double windowDragOffsetY;
    private ScheduledExecutorService databaseStatusMonitor;
    private ScheduledExecutorService modemStatusMonitor;

    private static final double SIDEBAR_EXPANDED_WIDTH = 235;
    private static final double SIDEBAR_COLLAPSED_WIDTH = 72;
    private static final double MENU_ICON_SIZE = 22;
    private static final long DATABASE_STATUS_REFRESH_SECONDS = 30;
    private static final long MODEM_STATUS_REFRESH_SECONDS = 30;
    private static final int MODEM_STATUS_PROBE_TIMEOUT_MILLIS = 1_500;

    @FXML
    private ImageView brandLogoImageView;

    @FXML
    private Label organizationNameLabel;

    @FXML
    private Label dateLabel;

    @FXML
    private Label fullNameLabel;

    @FXML
    private Label roleNameLabel;

    @FXML
    private ImageView profileImageView;

    @FXML
    private Label profileAvatarLabel;

    @FXML
    private Label pageTitleLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label versionLabel;

    @FXML
    private StackPane databaseStatusBadge;

    @FXML
    private Label databaseStatusLabel;

    @FXML
    private StackPane modemStatusBadge;

    @FXML
    private Label modemStatusLabel;

    @FXML
    private StackPane contentPane;

    @FXML
    private VBox sidebar;

    @FXML
    private HBox appHeader;

    @FXML
    private Button sidebarToggleButton;

    @FXML
    private Button logoutButton;

    @FXML
    private Button myProfileButton;

    @FXML
    private Button minimizeWindowButton;

    @FXML
    private Button maximizeWindowButton;

    @FXML
    private Button dashboardButton;

    @FXML
    private Button regionsButton;

    @FXML
    private Button churchesButton;

    @FXML
    private Button weeklyReceiptsButton;

    @FXML
    private Button receiptHistoryButton;

    @FXML
    private Button submissionStatusButton;

    @FXML
    private Button reportsButton;

    @FXML
    private Button usersButton;

    @FXML
    private Button rolesButton;

    @FXML
    private Button backupButton;

    @FXML
    private Button activityLogsButton;

    @FXML
    private Button smsLogsButton;

    @FXML
    private Button settingsButton;

    @FXML
    private void initialize() {
        dateLabel.setText(new SystemDateTimeFormatter().formatDate(LocalDate.now()));
        versionLabel.setText("Version " + AppConfig.APPLICATION_VERSION);

        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty()) {
            statusLabel.setText("Please sign in to continue.");
            return;
        }

        currentUser = user.get();
        if (currentUser.isForcePasswordChange()) {
            statusLabel.setText("Password change required before accessing the dashboard.");
            Platform.runLater(this::openForcePasswordChange);
            return;
        }

        activeController = this;
        permissionGuard = new PermissionGuard(currentUser);
        configureBrandLogo();
        applyOrganizationName();
        refreshHeaderFromAuthContext();
        ButtonIconUtil.applyIcon(logoutButton, "fas-sign-out-alt");
        ButtonIconUtil.applyIcon(myProfileButton, "fas-user");
        configureWindowHeader();
        startDatabaseStatusMonitor();
        startModemStatusMonitor();

        menuDefinitions = createMenuDefinitions();
        applyMenuIcons();
        configureSidebarToggle();
        applyMenuVisibility();
        AutoBackupScheduler.getInstance().reloadSchedule();
        loadMenu(menuDefinitions.getFirst());
    }

    private void configureBrandLogo() {
        double radius = Math.min(brandLogoImageView.getFitWidth(), brandLogoImageView.getFitHeight()) / 2;
        Circle clip = new Circle(radius, radius, radius);
        brandLogoImageView.setClip(clip);
    }

    private void applyOrganizationName() {
        String organizationName = SystemConfigurationCache.getInstance().getString("organization.name");
        organizationNameLabel.setText(organizationName == null || organizationName.isBlank()
                ? AppConfig.APPLICATION_NAME
                : organizationName.strip());
    }

    @FXML
    private void handleLogout() throws IOException {
        stopStatusMonitors();
        AutoBackupScheduler.getInstance().cancel();
        AuthContext.getCurrentUser()
                .ifPresent(user -> activityLogService.logLogout(user.getUserId(), user.getUsername()));
        AuthContext.clear();

        FXMLLoader loader = new FXMLLoader(getClass().getResource(AppConfig.LOGIN_VIEW));
        Scene scene = new Scene(loader.load(), AppConfig.LOGIN_WIDTH, AppConfig.LOGIN_HEIGHT);
        new ThemeService().applyConfiguredTheme(scene.getRoot());
        scene.setFill(Color.TRANSPARENT);
        Stage dashboardStage = (Stage) dateLabel.getScene().getWindow();
        Stage stage = new Stage(StageStyle.TRANSPARENT);

        stage.setTitle(AppConfig.APPLICATION_NAME);
        stage.setScene(scene);
        stage.setMinWidth(AppConfig.LOGIN_WIDTH);
        stage.setMinHeight(AppConfig.LOGIN_HEIGHT);
        stage.setWidth(AppConfig.LOGIN_WIDTH);
        stage.setHeight(AppConfig.LOGIN_HEIGHT);
        stage.centerOnScreen();
        stage.show();
        dashboardStage.close();
    }

    @FXML
    private void minimizeWindow() {
        ((Stage) appHeader.getScene().getWindow()).setIconified(true);
    }

    @FXML
    private void toggleMaximizeWindow() {
        Stage stage = (Stage) appHeader.getScene().getWindow();
        stage.setMaximized(!stage.isMaximized());
        updateMaximizeIcon(stage);
    }

    private void configureWindowHeader() {
        ButtonIconUtil.applyIcon(minimizeWindowButton, "fas-window-minimize");
        ButtonIconUtil.applyIcon(maximizeWindowButton, "fas-window-maximize");
        minimizeWindowButton.setText("");
        maximizeWindowButton.setText("");
        minimizeWindowButton.setTooltip(new Tooltip("Minimize"));
        maximizeWindowButton.setTooltip(new Tooltip("Maximize / Restore"));

        appHeader.setOnMousePressed(event -> {
            Stage stage = (Stage) appHeader.getScene().getWindow();
            windowDragOffsetX = stage.isMaximized() ? stage.getWidth() / 2 : event.getSceneX();
            windowDragOffsetY = event.getSceneY();
        });
        appHeader.setOnMouseDragged(event -> {
            Stage stage = (Stage) appHeader.getScene().getWindow();
            if (stage.isMaximized()) {
                stage.setMaximized(false);
                updateMaximizeIcon(stage);
            }
            stage.setX(event.getScreenX() - windowDragOffsetX);
            stage.setY(event.getScreenY() - windowDragOffsetY);
        });
        appHeader.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                toggleMaximizeWindow();
            }
        });
        Platform.runLater(() -> {
            Stage stage = (Stage) appHeader.getScene().getWindow();
            stage.maximizedProperty().addListener((observable, oldValue, newValue) -> updateMaximizeIcon(stage));
            updateMaximizeIcon(stage);
        });
    }

    private void startDatabaseStatusMonitor() {
        updateDatabaseStatusChecking();
        databaseStatusMonitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "database-status-monitor");
            thread.setDaemon(true);
            return thread;
        });
        databaseStatusMonitor.scheduleWithFixedDelay(this::checkDatabaseStatus,
                0, DATABASE_STATUS_REFRESH_SECONDS, TimeUnit.SECONDS);

        Platform.runLater(() -> {
            if (dateLabel.getScene() != null && dateLabel.getScene().getWindow() != null) {
                dateLabel.getScene().getWindow()
                        .addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> stopStatusMonitors());
            }
        });
    }

    private void checkDatabaseStatus() {
        boolean connected;
        try {
            DatabaseConfig.testConnection();
            connected = true;
        } catch (RuntimeException exception) {
            connected = false;
        }

        boolean finalConnected = connected;
        Platform.runLater(() -> updateDatabaseStatus(finalConnected));
    }

    private void updateDatabaseStatusChecking() {
        databaseStatusLabel.setText("Checking");
        databaseStatusLabel.getStyleClass().removeAll(
                "status-value-badge-connected", "status-value-badge-disconnected", "status-value-badge-checking");
        databaseStatusLabel.getStyleClass().add("status-value-badge-checking");
        databaseStatusBadge.getStyleClass().removeAll(
                "connection-status-badge-connected", "connection-status-badge-disconnected");
    }

    private void updateDatabaseStatus(boolean connected) {
        databaseStatusLabel.setText(connected ? "Connected" : "Disconnected");
        databaseStatusLabel.getStyleClass().removeAll(
                "status-value-badge-connected", "status-value-badge-disconnected", "status-value-badge-checking");
        databaseStatusLabel.getStyleClass().add(connected
                ? "status-value-badge-connected"
                : "status-value-badge-disconnected");

        databaseStatusBadge.getStyleClass().removeAll(
                "connection-status-badge-connected", "connection-status-badge-disconnected");
        databaseStatusBadge.getStyleClass().add(connected
                ? "connection-status-badge-connected"
                : "connection-status-badge-disconnected");
    }

    private void stopDatabaseStatusMonitor() {
        if (databaseStatusMonitor != null) {
            databaseStatusMonitor.shutdownNow();
            databaseStatusMonitor = null;
        }
    }

    private void startModemStatusMonitor() {
        updateModemStatusChecking();
        modemStatusMonitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "modem-status-monitor");
            thread.setDaemon(true);
            return thread;
        });
        modemStatusMonitor.scheduleWithFixedDelay(this::checkModemStatus,
                0, MODEM_STATUS_REFRESH_SECONDS, TimeUnit.SECONDS);
    }

    private void checkModemStatus() {
        ModemStatus status;
        try {
            SmsSettings settings = smsSettingsRepository.getSettings();
            if (settings.getComPort() == null || settings.getComPort().isBlank()) {
                status = ModemStatus.NOT_CONFIGURED;
            } else {
                int baudRate = settings.getBaudRate() == null ? 9600 : settings.getBaudRate();
                status = atCommandService.isModemPort(settings.getComPort(), baudRate,
                        MODEM_STATUS_PROBE_TIMEOUT_MILLIS)
                        ? ModemStatus.CONNECTED
                        : ModemStatus.DISCONNECTED;
            }
        } catch (RuntimeException exception) {
            status = ModemStatus.DISCONNECTED;
        }

        ModemStatus finalStatus = status;
        Platform.runLater(() -> updateModemStatus(finalStatus));
    }

    private void updateModemStatusChecking() {
        modemStatusLabel.setText("Checking");
        modemStatusLabel.getStyleClass().removeAll(
                "status-value-badge-connected", "status-value-badge-disconnected", "status-value-badge-checking");
        modemStatusLabel.getStyleClass().add("status-value-badge-checking");
        modemStatusBadge.getStyleClass().removeAll(
                "connection-status-badge-connected", "connection-status-badge-disconnected");
    }

    private void updateModemStatus(ModemStatus status) {
        modemStatusLabel.setText(switch (status) {
            case CONNECTED -> "Connected";
            case DISCONNECTED -> "Disconnected";
            case NOT_CONFIGURED -> "Not configured";
        });
        modemStatusLabel.getStyleClass().removeAll(
                "status-value-badge-connected", "status-value-badge-disconnected", "status-value-badge-checking");
        modemStatusLabel.getStyleClass().add(status == ModemStatus.CONNECTED
                ? "status-value-badge-connected"
                : "status-value-badge-disconnected");

        modemStatusBadge.getStyleClass().removeAll(
                "connection-status-badge-connected", "connection-status-badge-disconnected");
        modemStatusBadge.getStyleClass().add(status == ModemStatus.CONNECTED
                ? "connection-status-badge-connected"
                : "connection-status-badge-disconnected");
    }

    private void stopModemStatusMonitor() {
        if (modemStatusMonitor != null) {
            modemStatusMonitor.shutdownNow();
            modemStatusMonitor = null;
        }
    }

    private void stopStatusMonitors() {
        stopDatabaseStatusMonitor();
        stopModemStatusMonitor();
    }

    private void updateMaximizeIcon(Stage stage) {
        ButtonIconUtil.applyIcon(maximizeWindowButton, stage.isMaximized()
                ? "fas-window-restore"
                : "fas-window-maximize");
        maximizeWindowButton.setText("");
    }

    @FXML
    private void showDashboard() {
        loadMenu(findMenu(dashboardButton));
    }

    @FXML
    private void showRegions() {
        loadMenu(findMenu(regionsButton));
    }

    @FXML
    private void showChurches() {
        loadMenu(findMenu(churchesButton));
    }

    @FXML
    private void showWeeklyReceipts() {
        loadMenu(findMenu(weeklyReceiptsButton));
    }

    @FXML
    private void showReceiptHistory() {
        loadMenu(findMenu(receiptHistoryButton));
    }

    @FXML
    private void showSubmissionStatus() {
        loadMenu(findMenu(submissionStatusButton));
    }

    @FXML
    private void showReports() {
        loadMenu(findMenu(reportsButton));
    }

    @FXML
    private void showUsers() {
        loadMenu(findMenu(usersButton));
    }

    @FXML
    private void showRoles() {
        loadMenu(findMenu(rolesButton));
    }

    @FXML
    private void showBackup() {
        loadMenu(findMenu(backupButton));
    }

    @FXML
    private void showActivityLogs() {
        loadMenu(findMenu(activityLogsButton));
    }

    @FXML
    private void showSmsLogs() {
        loadMenu(findMenu(smsLogsButton));
    }

    @FXML
    private void showSettings() {
        loadMenu(findMenu(settingsButton));
    }

    @FXML
    private void showMyProfile() {
        loadMenu(new MenuDefinition("My Profile", "/com/churchmanagement/view/user-profile-view.fxml",
                myProfileButton, null));
    }

    @FXML
    private void toggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed;
        sidebar.setMinWidth(sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH);
        sidebar.setPrefWidth(sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH);

        for (MenuDefinition menuDefinition : menuDefinitions) {
            Button button = menuDefinition.button();
            button.setText(sidebarCollapsed ? "" : menuDefinition.title());
            button.setContentDisplay(sidebarCollapsed ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
            button.setGraphicTextGap(sidebarCollapsed ? 0 : 10);
        }

        sidebarToggleButton.setGraphic(createMenuIcon(sidebarCollapsed ? "fas-angle-double-right" : "fas-angle-double-left"));
        sidebarToggleButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private List<MenuDefinition> createMenuDefinitions() {
        return List.of(
                new MenuDefinition("Dashboard", "/com/churchmanagement/view/dashboard-home-view.fxml",
                        dashboardButton, null),
                new MenuDefinition("Regions", "/com/churchmanagement/view/region-view.fxml",
                        regionsButton, ActivityLogService.NAVIGATE_REGIONS, "region.menu.view"),
                new MenuDefinition("Churches", "/com/churchmanagement/view/church-view.fxml",
                        churchesButton, ActivityLogService.NAVIGATE_CHURCHES, "church.menu.view"),
                new MenuDefinition("Receipt", "/com/churchmanagement/view/receipt-entry-view.fxml",
                        weeklyReceiptsButton, ActivityLogService.NAVIGATE_RECEIPTS, "receipt.entry.menu.view"),
                new MenuDefinition("Receipt History", "/com/churchmanagement/view/receipt-history-view.fxml",
                        receiptHistoryButton, ActivityLogService.NAVIGATE_RECEIPTS, "receipt.menu.view"),
                new MenuDefinition("Submission Status", "/com/churchmanagement/view/submission-status-view.fxml",
                        submissionStatusButton, ActivityLogService.NAVIGATE_RECEIPTS, "receipt.menu.view"),
                new MenuDefinition("Reports", "/com/churchmanagement/view/reports-view.fxml",
                        reportsButton, ActivityLogService.NAVIGATE_REPORTS, "report.menu.view"),
                new MenuDefinition("Users", "/com/churchmanagement/view/user-management-view.fxml",
                        usersButton, ActivityLogService.NAVIGATE_USERS, "user.menu.view"),
                new MenuDefinition("Roles & Permissions", "/com/churchmanagement/view/role-permission-view.fxml",
                        rolesButton, ActivityLogService.NAVIGATE_USERS, "role.menu.view"),
                new MenuDefinition("Backup & Restore", "/com/churchmanagement/view/backup-restore-view.fxml",
                        backupButton, ActivityLogService.NAVIGATE_BACKUP_RESTORE, "backup.menu.view"),
                new MenuDefinition("Activity Logs", "/com/churchmanagement/view/activity-log-view.fxml",
                        activityLogsButton, ActivityLogService.NAVIGATE_ACTIVITY_LOGS, "activity.menu.view"),
                new MenuDefinition("SMS Logs", "/com/churchmanagement/view/sms-log-view.fxml",
                        smsLogsButton, ActivityLogService.NAVIGATE_SMS_LOGS, "sms.menu.view"),
                new MenuDefinition("Settings", "/com/churchmanagement/view/settings-view.fxml",
                        settingsButton, null, "settings.menu.view")
        );
    }

    private void applyMenuVisibility() {
        for (MenuDefinition menuDefinition : menuDefinitions) {
            boolean visible = permissionGuard.canAny((String[]) menuDefinition.permissionCodes());
            menuDefinition.button().setVisible(visible);
            menuDefinition.button().setManaged(visible);
        }
    }

    private void applyMenuIcons() {
        setMenuIcon(dashboardButton, "fas-home");
        setMenuIcon(regionsButton, "fas-map");
        setMenuIcon(churchesButton, "fas-church");
        setMenuIcon(weeklyReceiptsButton, "fas-receipt");
        setMenuIcon(receiptHistoryButton, "fas-history");
        setMenuIcon(submissionStatusButton, "fas-tasks");
        setMenuIcon(reportsButton, "fas-chart-bar");
        setMenuIcon(usersButton, "fas-users");
        setMenuIcon(rolesButton, "fas-user-lock");
        setMenuIcon(backupButton, "fas-database");
        setMenuIcon(activityLogsButton, "fas-list-alt");
        setMenuIcon(smsLogsButton, "fas-sms");
        setMenuIcon(settingsButton, "fas-cog");
    }

    private void configureSidebarToggle() {
        sidebarToggleButton.setGraphic(createMenuIcon("fas-angle-double-left"));
        sidebarToggleButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void setMenuIcon(Button button, String iconLiteral) {
        button.setGraphic(createMenuIcon(iconLiteral));
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(10);
    }

    private Node createMenuIcon(String iconLiteral) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("menu-icon");
        icon.setIconSize(16);
        StackPane iconBox = new StackPane(icon);
        iconBox.getStyleClass().add("menu-icon-box");
        iconBox.setMinSize(MENU_ICON_SIZE, MENU_ICON_SIZE);
        iconBox.setPrefSize(MENU_ICON_SIZE, MENU_ICON_SIZE);
        iconBox.setMaxSize(MENU_ICON_SIZE, MENU_ICON_SIZE);
        return iconBox;
    }

    private MenuDefinition findMenu(Button button) {
        return menuDefinitions.stream()
                .filter(menuDefinition -> menuDefinition.button() == button)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown menu item."));
    }

    private void loadMenu(MenuDefinition menuDefinition) {
        if (currentUser != null && currentUser.isForcePasswordChange()) {
            showError("Password change required", "You must change your password before accessing modules.");
            return;
        }

        if (!permissionGuard.canAny((String[]) menuDefinition.permissionCodes())) {
            showError("Access denied", "You do not have permission to open " + menuDefinition.title() + ".");
            return;
        }

        try {
            Parent view = FXMLLoader.load(getClass().getResource(menuDefinition.viewPath()));
            contentPane.getChildren().setAll(view);
            pageTitleLabel.setText(menuDefinition.title());
            statusLabel.setText("Ready");
            highlightMenu(menuDefinition.button());
            logNavigation(menuDefinition);
        } catch (IOException | RuntimeException exception) {
            statusLabel.setText("Could not load " + menuDefinition.title() + ".");
            showError("Unable to load page", "Please try again or contact your administrator.");
        }
    }

    private void highlightMenu(Button selectedButton) {
        for (MenuDefinition menuDefinition : menuDefinitions) {
            menuDefinition.button().getStyleClass().remove("active-menu-button");
        }
        myProfileButton.getStyleClass().remove("active-menu-button");
        if (selectedButton != null) {
            selectedButton.getStyleClass().add("active-menu-button");
        }
    }

    private void logNavigation(MenuDefinition menuDefinition) {
        if (menuDefinition.activityAction() != null) {
            activityLogService.logNavigation(currentUser.getUserId(), menuDefinition.activityAction(), menuDefinition.title());
        }
    }

    private void showError(String title, String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void openForcePasswordChange() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(AppConfig.FORCE_PASSWORD_CHANGE_VIEW));
            Scene scene = new Scene(loader.load(), AppConfig.FORCE_PASSWORD_CHANGE_WIDTH,
                    AppConfig.FORCE_PASSWORD_CHANGE_HEIGHT);
            new ThemeService().applyConfiguredTheme(scene.getRoot());
            scene.setFill(Color.TRANSPARENT);
            Stage dashboardStage = (Stage) dateLabel.getScene().getWindow();
            Stage stage = new Stage(StageStyle.TRANSPARENT);

            stage.setTitle(AppConfig.APPLICATION_NAME + " - Change Password");
            stage.setScene(scene);
            stage.setMinWidth(AppConfig.FORCE_PASSWORD_CHANGE_WIDTH);
            stage.setMinHeight(AppConfig.FORCE_PASSWORD_CHANGE_HEIGHT);
            stage.centerOnScreen();
            stage.show();
            dashboardStage.close();
        } catch (IOException exception) {
            statusLabel.setText("Unable to open password change screen.");
        }
    }

    public static void openReceiptCorrection(long cancelledReceiptId) {
        if (activeController == null) {
            return;
        }

        ReceiptEntryController.prepareCorrection(cancelledReceiptId);
        activeController.loadMenu(activeController.findMenu(activeController.weeklyReceiptsButton));
    }

    public static void refreshActiveHeader() {
        if (activeController != null) {
            activeController.refreshHeaderFromAuthContext();
        }
    }

    private void refreshHeaderFromAuthContext() {
        AuthContext.getCurrentUser().ifPresent(user -> {
            currentUser = user;
            fullNameLabel.setText(user.getFullName());
            roleNameLabel.setText(user.getRoleName());
            updateProfileImage(user.getProfilePicturePath());
        });
    }

    private void updateProfileImage(String profilePicturePath) {
        if (profileImageView == null) {
            return;
        }
        profileImageView.setFitWidth(42);
        profileImageView.setFitHeight(42);
        profileImageView.setPreserveRatio(false);
        profileImageView.setClip(new Circle(21, 21, 21));

        if (profilePicturePath != null && !profilePicturePath.isBlank()
                && Files.exists(Path.of(profilePicturePath))) {
            setCircularImage(profileImageView, Path.of(profilePicturePath), 42);
            profileAvatarLabel.setVisible(false);
        } else {
            profileImageView.setImage(null);
            profileImageView.setViewport(null);
            profileAvatarLabel.setText(initials(currentUser == null ? "" : currentUser.getFullName()));
            profileAvatarLabel.setVisible(true);
        }
    }

    private void setCircularImage(ImageView imageView, Path imagePath, double size) {
        Image image = new Image(imagePath.toUri().toString());
        double width = image.getWidth();
        double height = image.getHeight();
        double squareSize = Math.min(width, height);
        imageView.setViewport(new Rectangle2D(
                (width - squareSize) / 2,
                (height - squareSize) / 2,
                squareSize,
                squareSize));
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(false);
        imageView.setImage(image);
    }

    private String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.strip().split("\\s+");
        String first = parts[0].substring(0, 1);
        String second = parts.length > 1 ? parts[1].substring(0, 1) : "";
        return (first + second).toUpperCase();
    }

    private record MenuDefinition(String title, String viewPath, Button button, String activityAction,
                                  String... permissionCodes) {
    }

    private enum ModemStatus {
        CONNECTED,
        DISCONNECTED,
        NOT_CONFIGURED
    }
}
