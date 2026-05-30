package com.churchmanagement.controller;

import com.churchmanagement.config.AppConfig;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.ActivityLogService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class DashboardController {
    private static DashboardController activeController;

    private final ActivityLogService activityLogService = new ActivityLogService();
    private AuthenticatedUser currentUser;
    private PermissionGuard permissionGuard;
    private List<MenuDefinition> menuDefinitions;
    private boolean sidebarCollapsed;

    private static final double SIDEBAR_EXPANDED_WIDTH = 235;
    private static final double SIDEBAR_COLLAPSED_WIDTH = 72;

    @FXML
    private Label dateLabel;

    @FXML
    private Label fullNameLabel;

    @FXML
    private Label roleNameLabel;

    @FXML
    private Label pageTitleLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private StackPane contentPane;

    @FXML
    private VBox sidebar;

    @FXML
    private Button sidebarToggleButton;

    @FXML
    private Button logoutButton;

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
    private Button settingsButton;

    @FXML
    private void initialize() {
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));

        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty()) {
            statusLabel.setText("Please sign in to continue.");
            return;
        }

        currentUser = user.get();
        activeController = this;
        permissionGuard = new PermissionGuard(currentUser);
        fullNameLabel.setText(currentUser.getFullName());
        roleNameLabel.setText(currentUser.getRoleName());
        ButtonIconUtil.applyIcon(logoutButton, "fas-sign-out-alt");

        menuDefinitions = createMenuDefinitions();
        applyMenuIcons();
        configureSidebarToggle();
        applyMenuVisibility();
        loadMenu(menuDefinitions.getFirst());
    }

    @FXML
    private void handleLogout() throws IOException {
        AuthContext.getCurrentUser()
                .ifPresent(user -> activityLogService.logLogout(user.getUserId(), user.getUsername()));
        AuthContext.clear();

        FXMLLoader loader = new FXMLLoader(getClass().getResource(AppConfig.LOGIN_VIEW));
        Scene scene = new Scene(loader.load(), AppConfig.LOGIN_WIDTH, AppConfig.LOGIN_HEIGHT);
        Stage stage = (Stage) dateLabel.getScene().getWindow();

        stage.setTitle(AppConfig.APPLICATION_NAME);
        stage.setFullScreen(false);
        stage.setMaximized(false);
        stage.setIconified(false);
        stage.setScene(scene);
        stage.setMinWidth(AppConfig.LOGIN_WIDTH);
        stage.setMinHeight(AppConfig.LOGIN_HEIGHT);
        stage.setWidth(AppConfig.LOGIN_WIDTH);
        stage.setHeight(AppConfig.LOGIN_HEIGHT);
        stage.centerOnScreen();
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
    private void showSettings() {
        loadMenu(findMenu(settingsButton));
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
                        regionsButton, ActivityLogService.NAVIGATE_REGIONS, "region.view"),
                new MenuDefinition("Churches", "/com/churchmanagement/view/church-view.fxml",
                        churchesButton, ActivityLogService.NAVIGATE_CHURCHES, "church.view"),
                new MenuDefinition("Receipt", "/com/churchmanagement/view/receipt-entry-view.fxml",
                        weeklyReceiptsButton, ActivityLogService.NAVIGATE_RECEIPTS, "receipt.create"),
                new MenuDefinition("Receipt History", "/com/churchmanagement/view/receipt-history-view.fxml",
                        receiptHistoryButton, ActivityLogService.NAVIGATE_RECEIPTS, "receipt.view"),
                new MenuDefinition("Reports", "/com/churchmanagement/view/reports-view.fxml",
                        reportsButton, ActivityLogService.NAVIGATE_REPORTS, "report.view"),
                new MenuDefinition("Users", "/com/churchmanagement/view/user-management-view.fxml",
                        usersButton, ActivityLogService.NAVIGATE_USERS, "user.manage"),
                new MenuDefinition("Roles & Permissions", "/com/churchmanagement/view/role-permission-view.fxml",
                        rolesButton, ActivityLogService.NAVIGATE_USERS, "role.manage"),
                new MenuDefinition("Backup & Restore", "/com/churchmanagement/view/backup-restore-view.fxml",
                        backupButton, ActivityLogService.NAVIGATE_BACKUP_RESTORE, "backup.create", "backup.restore"),
                new MenuDefinition("Activity Logs", "/com/churchmanagement/view/activity-log-view.fxml",
                        activityLogsButton, ActivityLogService.NAVIGATE_ACTIVITY_LOGS, "activity.view"),
                new MenuDefinition("Settings", "/com/churchmanagement/view/settings-view.fxml",
                        settingsButton, null, "settings.manage")
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
        setMenuIcon(reportsButton, "fas-chart-bar");
        setMenuIcon(usersButton, "fas-users");
        setMenuIcon(rolesButton, "fas-user-lock");
        setMenuIcon(backupButton, "fas-database");
        setMenuIcon(activityLogsButton, "fas-list-alt");
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

    private FontIcon createMenuIcon(String iconLiteral) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("menu-icon");
        return icon;
    }

    private MenuDefinition findMenu(Button button) {
        return menuDefinitions.stream()
                .filter(menuDefinition -> menuDefinition.button() == button)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown menu item."));
    }

    private void loadMenu(MenuDefinition menuDefinition) {
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
        selectedButton.getStyleClass().add("active-menu-button");
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

    public static void openReceiptCorrection(long cancelledReceiptId) {
        if (activeController == null) {
            return;
        }

        ReceiptEntryController.prepareCorrection(cancelledReceiptId);
        activeController.loadMenu(activeController.findMenu(activeController.weeklyReceiptsButton));
    }

    private record MenuDefinition(String title, String viewPath, Button button, String activityAction,
                                  String... permissionCodes) {
    }
}
