package com.churchmanagement.controller;

import com.churchmanagement.config.AppConfig;
import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.config.PrimaryMachine;
import com.churchmanagement.dto.SmsSettings;
import com.churchmanagement.navigation.NavItem;
import com.churchmanagement.navigation.NavModule;
import com.churchmanagement.navigation.NavigationAccess;
import com.churchmanagement.navigation.NavigationRegistry;
import com.churchmanagement.navigation.UnsavedChangesAware;
import com.churchmanagement.repository.SmsSettingsRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.ActivityLogService;
import com.churchmanagement.service.AtCommandService;
import com.churchmanagement.service.SerialPortService;
import com.churchmanagement.service.SmsQueueProcessor;
import com.churchmanagement.service.SystemConfigurationCache;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ThemeService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.geometry.Rectangle2D;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
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
    private boolean sidebarCollapsed;
    private double windowDragOffsetX;
    private double windowDragOffsetY;
    private ScheduledExecutorService databaseStatusMonitor;
    private ScheduledExecutorService modemStatusMonitor;
    private Timeline footerClock;
    private NavigationAccess navigationAccess;
    private NavModule currentModule;
    private NavItem currentItem;
    private String standaloneTitle;
    private boolean onHome;
    private Object currentContentController;
    private final Map<String, String> lastItemByModule = new HashMap<>();

    private static final String HOME_VIEW = "/com/churchmanagement/view/home-view.fxml";
    private static final String MY_PROFILE_VIEW = "/com/churchmanagement/view/user-profile-view.fxml";
    private static final KeyCode[] SHORTCUT_DIGITS = {
            KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4, KeyCode.DIGIT5, KeyCode.DIGIT6,
            KeyCode.DIGIT7};
    private static final double SIDEBAR_EXPANDED_WIDTH = 235;
    private static final double SIDEBAR_COLLAPSED_WIDTH = 72;
    private static final double MENU_ICON_SIZE = 22;
    private static final long DATABASE_STATUS_REFRESH_SECONDS = 30;
    private static final long MODEM_STATUS_REFRESH_SECONDS = 30;
    private static final int MODEM_STATUS_PROBE_TIMEOUT_MILLIS = 1_500;
    private static final DateTimeFormatter FOOTER_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("hh:mm a\nyyyy-MMM-dd", Locale.ENGLISH);

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
    private VBox sidebarHeaderBox;

    @FXML
    private VBox sidebarItemsBox;

    @FXML
    private HBox breadcrumbBar;

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
    private void initialize() {
        startFooterClock();
        versionLabel.setText("Version " + AppConfig.APPLICATION_VERSION);

        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty()) {
            setStatus("Please sign in to continue.", "status-warning");
            return;
        }

        currentUser = user.get();
        if (currentUser.isForcePasswordChange()) {
            setStatus("Password change required before accessing the dashboard.", "status-warning");
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

        navigationAccess = new NavigationAccess(currentUser, PrimaryMachine.isPrimary());
        configureSidebarToggle();
        SmsQueueProcessor.getInstance().start();
        Platform.runLater(this::installShortcuts);
        NavigationAccess.Landing landing = navigationAccess.landing(currentUser.getLandingScreen());
        if (landing.item() != null && openItem(landing.item()) && landing.fellBack()) {
            setStatus("Your saved start screen is no longer available — opened " + landing.item().title() + ".",
                    "status-warning");
        }
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
        if (!confirmLeave()) {
            return;
        }
        stopStatusMonitors();
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
        stopFooterClock();
    }

    private void startFooterClock() {
        updateFooterDateTime();
        footerClock = new Timeline(new KeyFrame(Duration.seconds(1), event -> updateFooterDateTime()));
        footerClock.setCycleCount(Timeline.INDEFINITE);
        footerClock.play();
    }

    private void updateFooterDateTime() {
        dateLabel.setText(LocalDateTime.now().format(FOOTER_DATE_TIME_FORMATTER));
    }

    private void stopFooterClock() {
        if (footerClock != null) {
            footerClock.stop();
            footerClock = null;
        }
    }

    private void updateMaximizeIcon(Stage stage) {
        ButtonIconUtil.applyIcon(maximizeWindowButton, stage.isMaximized()
                ? "fas-window-restore"
                : "fas-window-maximize");
        maximizeWindowButton.setText("");
    }

    @FXML
    private void showMyProfile() {
        if (!confirmLeave()) {
            return;
        }
        if (loadContent(MY_PROFILE_VIEW, "My Profile")) {
            currentItem = null;
            onHome = false;
            standaloneTitle = "My Profile";
            renderSidebar();
            renderBreadcrumb();
            myProfileButton.getStyleClass().add("active-menu-button");
        }
    }

    @FXML
    private void toggleSidebar() {
        sidebarCollapsed = !sidebarCollapsed;
        sidebar.setMinWidth(sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH);
        sidebar.setPrefWidth(sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH);

        if (navigationAccess != null) {
            renderSidebar();
        }

        sidebarToggleButton.setGraphic(createMenuIcon(sidebarCollapsed ? "fas-angle-double-right" : "fas-angle-double-left"));
        sidebarToggleButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void configureSidebarToggle() {
        sidebarToggleButton.setGraphic(createMenuIcon("fas-angle-double-left"));
        sidebarToggleButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
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

    private void openHome() {
        if (!navigationAccess.showHomeAndSwitcher() || !confirmLeave()) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(HOME_VIEW));
            Parent view = loader.load();
            HomeController homeController = loader.getController();
            homeController.configure(navigationAccess, module -> openModule(module, true), currentUser);
            contentPane.getChildren().setAll(view);
            currentContentController = homeController;
            currentModule = null;
            currentItem = null;
            standaloneTitle = null;
            onHome = true;
            myProfileButton.getStyleClass().remove("active-menu-button");
            renderSidebar();
            renderBreadcrumb();
            setStatus("Ready", "status-ready");
        } catch (IOException | RuntimeException exception) {
            setStatus("Could not load Home.", "status-error");
            showError("Unable to load page", "Please try again or contact your administrator.");
        }
    }

    private void openModule(NavModule module, boolean logModule) {
        List<NavItem> items = navigationAccess.visibleItems(module);
        if (items.isEmpty()) {
            return;
        }
        String rememberedId = lastItemByModule.get(module.id());
        NavItem target = items.stream()
                .filter(item -> item.id().equals(rememberedId))
                .findFirst()
                .orElse(items.getFirst());
        if (openItem(target) && logModule) {
            activityLogService.logNavigation(currentUser.getUserId(), ActivityLogService.NAVIGATE_MODULE,
                    module.title());
        }
    }

    private boolean openItem(NavItem item) {
        if (currentUser != null && currentUser.isForcePasswordChange()) {
            showError("Password change required", "You must change your password before accessing modules.");
            return false;
        }
        if (!navigationAccess.canOpen(item)) {
            if (NavigationRegistry.BACKUP_ITEM_ID.equals(item.id()) && !PrimaryMachine.isPrimary()) {
                showError("Access denied", "Backup & Restore is only available on the server machine.");
            } else {
                showError("Access denied", "You do not have permission to open " + item.title() + ".");
            }
            return false;
        }
        if (item == currentItem && !onHome && standaloneTitle == null) {
            return true; // already showing it; reloading would drop the screen's input
        }
        if (!confirmLeave()) {
            return false;
        }
        if (!loadContent(item.viewPath(), item.title(), item)) {
            return false;
        }
        currentItem = item;
        currentModule = navigationAccess.moduleOf(item).orElse(null);
        standaloneTitle = null;
        onHome = false;
        if (currentModule != null) {
            lastItemByModule.put(currentModule.id(), item.id());
        }
        myProfileButton.getStyleClass().remove("active-menu-button");
        renderSidebar();
        renderBreadcrumb();
        if (item.activityAction() != null) {
            activityLogService.logNavigation(currentUser.getUserId(), item.activityAction(), item.title());
        }
        return true;
    }

    private boolean loadContent(String viewPath, String title) {
        return loadContent(viewPath, title, null);
    }

    private boolean loadContent(String viewPath, String title, NavItem item) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(viewPath));
            Parent view = loader.load();
            Object controller = loader.getController();
            if (item != null && item.reportGroup() != null && controller instanceof ReportsController reports) {
                reports.setReportGroup(item.reportGroup());
            }
            contentPane.getChildren().setAll(view);
            currentContentController = controller;
            setStatus("Ready", "status-ready");
            return true;
        } catch (IOException | RuntimeException exception) {
            setStatus("Could not load " + title + ".", "status-error");
            showError("Unable to load page", "Please try again or contact your administrator.");
            return false;
        }
    }

    private boolean confirmLeave() {
        if (currentContentController instanceof UnsavedChangesAware screen && screen.hasUnsavedChanges()) {
            Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.CONFIRMATION));
            alert.setTitle("Unsaved changes");
            alert.setHeaderText("Unsaved changes");
            alert.setContentText("You have unsaved changes. Leave this screen?");
            return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
        }
        return true;
    }

    private void renderSidebar() {
        sidebarHeaderBox.getChildren().clear();
        sidebarItemsBox.getChildren().clear();
        boolean showHome = navigationAccess.showHomeAndSwitcher();
        if (showHome) {
            Button homeButton = sidebarButton("Home", "fas-th-large", "Home (Ctrl+H)");
            homeButton.setOnAction(event -> openHome());
            if (onHome) {
                homeButton.getStyleClass().add("active-menu-button");
            }
            sidebarHeaderBox.getChildren().add(homeButton);
        }
        if (currentModule == null) {
            return;
        }

        Button moduleButton = sidebarButton(currentModule.title(), currentModule.icon(),
                showHome ? "Switch module" : currentModule.title());
        moduleButton.getStyleClass().add("nav-module-button");
        if (showHome) {
            ContextMenu switcher = moduleSwitcher();
            moduleButton.setOnAction(event -> switcher.show(moduleButton, Side.RIGHT, 0, 0));
        }
        sidebarHeaderBox.getChildren().add(moduleButton);

        boolean sections = navigationAccess.useSections(currentModule) && !sidebarCollapsed;
        String lastSection = null;
        for (NavItem item : navigationAccess.visibleItems(currentModule)) {
            if (sections && item.section() != null && !item.section().equals(lastSection)) {
                Label sectionLabel = new Label(item.section());
                sectionLabel.getStyleClass().add("nav-section-label");
                sidebarItemsBox.getChildren().add(sectionLabel);
                lastSection = item.section();
            }
            Button button = sidebarButton(item.title(), item.icon(), item.description());
            button.setOnAction(event -> openItem(item));
            if (item == currentItem) {
                button.getStyleClass().add("active-menu-button");
            }
            sidebarItemsBox.getChildren().add(button);
        }
    }

    private ContextMenu moduleSwitcher() {
        ContextMenu menu = new ContextMenu();
        for (NavModule module : navigationAccess.visibleModules()) {
            MenuItem menuItem = new MenuItem(module.title() + "   (Ctrl+" + module.shortcutNumber() + ")",
                    new FontIcon(module.icon()));
            menuItem.setOnAction(event -> openModule(module, true));
            menu.getItems().add(menuItem);
        }
        return menu;
    }

    private Button sidebarButton(String title, String icon, String tooltip) {
        Button button = new Button(sidebarCollapsed ? "" : title);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("menu-button");
        button.setGraphic(createMenuIcon(icon));
        button.setContentDisplay(sidebarCollapsed ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
        button.setGraphicTextGap(sidebarCollapsed ? 0 : 10);
        button.setTooltip(new Tooltip(sidebarCollapsed ? title : tooltip));
        button.setAccessibleText(title);
        return button;
    }

    private void renderBreadcrumb() {
        List<Node> parts = new ArrayList<>();
        boolean showHome = navigationAccess.showHomeAndSwitcher();
        if (onHome) {
            parts.add(breadcrumbCurrent("Home"));
        } else {
            if (showHome) {
                parts.add(breadcrumbLink("Home", this::openHome));
            }
            if (standaloneTitle != null) {
                parts.add(breadcrumbCurrent(standaloneTitle));
            } else if (currentModule != null && currentItem != null) {
                NavModule module = currentModule;
                parts.add(breadcrumbLink(module.title(), () -> openModule(module, false)));
                parts.add(breadcrumbCurrent(currentItem.title()));
            }
        }
        breadcrumbBar.getChildren().clear();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                Label separator = new Label("›");
                separator.getStyleClass().add("breadcrumb-separator");
                breadcrumbBar.getChildren().add(separator);
            }
            breadcrumbBar.getChildren().add(parts.get(index));
        }
    }

    private Button breadcrumbLink(String text, Runnable action) {
        Button link = new Button(text);
        link.getStyleClass().add("breadcrumb-link");
        link.setOnAction(event -> action.run());
        return link;
    }

    private Label breadcrumbCurrent(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("page-title");
        return label;
    }

    private void installShortcuts() {
        Scene scene = contentPane.getScene();
        if (scene == null) {
            return;
        }
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.H, KeyCombination.SHORTCUT_DOWN), this::openHome);
        for (int number = 1; number <= SHORTCUT_DIGITS.length; number++) {
            int shortcut = number;
            scene.getAccelerators().put(new KeyCodeCombination(SHORTCUT_DIGITS[number - 1],
                            KeyCombination.SHORTCUT_DOWN),
                    () -> navigationAccess.moduleForShortcut(shortcut).ifPresent(module -> openModule(module, true)));
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
            setStatus("Unable to open password change screen.", "status-error");
        }
    }

    public static void openReceiptCorrection(long cancelledReceiptId) {
        if (activeController == null) {
            return;
        }

        ReceiptEntryController.prepareCorrection(cancelledReceiptId);
        NavigationRegistry.findItem(NavigationRegistry.RECEIPT_ITEM_ID).ifPresent(activeController::openItem);
    }

    public static void openSendMessage() {
        if (activeController != null) {
            NavigationRegistry.findItem(NavigationRegistry.SEND_MESSAGE_ITEM_ID).ifPresent(activeController::openItem);
        }
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

    private void setStatus(String text, String cssClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll(
                "status-ready", "status-error", "status-warning", "status-info");
        statusLabel.getStyleClass().add(cssClass);
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

    private enum ModemStatus {
        CONNECTED,
        DISCONNECTED,
        NOT_CONFIGURED
    }
}
