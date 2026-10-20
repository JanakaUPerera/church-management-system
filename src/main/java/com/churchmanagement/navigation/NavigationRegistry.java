package com.churchmanagement.navigation;

import com.churchmanagement.dto.report.ReportGroup;
import com.churchmanagement.service.ActivityLogService;

import java.util.List;
import java.util.Optional;

/** Every screen reachable from the main window, grouped into Home modules in tile order. */
public final class NavigationRegistry {
    public static final String OVERVIEW_ITEM_ID = "dashboard.overview";
    public static final String BACKUP_ITEM_ID = "admin.backup";
    public static final String RECEIPT_ITEM_ID = "collections.receipt";
    public static final String SEND_MESSAGE_ITEM_ID = "messaging.send-message";

    private static final String VIEW = "/com/churchmanagement/view/";

    private static final List<NavModule> MODULES = List.of(
            new NavModule("dashboard", "Dashboard", "fas-home", "Today's overview", 1, List.of(
                    item(OVERVIEW_ITEM_ID, "Overview", "Collections, submissions and trends at a glance",
                            "dashboard-home-view.fxml", "fas-home", null, null),
                    item("dashboard.submission-status", "Submission Status",
                            "Which branches have submitted this week", "submission-status-view.fxml",
                            "fas-tasks", ActivityLogService.NAVIGATE_RECEIPTS, null, "receipt.menu.view"))),
            new NavModule("collections", "Collections", "fas-receipt", "Enter and track receipts", 2, List.of(
                    item(RECEIPT_ITEM_ID, "Receipt", "Enter a branch's weekly collection and print the receipt",
                            "receipt-entry-view.fxml", "fas-receipt", ActivityLogService.NAVIGATE_RECEIPTS, null,
                            "receipt.entry.menu.view"),
                    item("collections.receipt-history", "Receipt History",
                            "Search, view, reprint or cancel issued receipts", "receipt-history-view.fxml",
                            "fas-history", ActivityLogService.NAVIGATE_RECEIPTS, null, "receipt.menu.view"),
                    item("collections.reports", "Collection Reports",
                            "Collection, submission and receipt reports (PDF / Excel)", "reports-view.fxml",
                            "fas-chart-bar", ActivityLogService.NAVIGATE_REPORTS, ReportGroup.COLLECTIONS,
                            "report.menu.view"))),
            new NavModule("branches", "Branches", "fas-church", "Churches, regions and categories", 3, List.of(
                    item("branches.churches", "Churches", "Add and edit branches, their contacts and categories",
                            "church-view.fxml", "fas-church", ActivityLogService.NAVIGATE_CHURCHES, null,
                            "church.menu.view"),
                    item("branches.regions", "Regions", "Add and edit regions", "region-view.fxml", "fas-map",
                            ActivityLogService.NAVIGATE_REGIONS, null, "region.menu.view"),
                    item("branches.categories", "Categories", "Collection categories (A, B, C ...)",
                            "category-view.fxml", "fas-tags", ActivityLogService.NAVIGATE_CATEGORIES, null,
                            "category.menu.view"))),
            new NavModule("messaging", "Messaging", "fas-sms", "Send official SMS, view logs", 4, List.of(
                    item(SEND_MESSAGE_ITEM_ID, "Send Message", "Send an official SMS to selected branches",
                            "send-message-view.fxml", "fas-paper-plane", ActivityLogService.NAVIGATE_SEND_MESSAGE,
                            null, "sms.official.menu.view"),
                    item("messaging.sms-logs", "SMS Logs", "Delivery status of every SMS, and resend",
                            "sms-log-view.fxml", "fas-sms", ActivityLogService.NAVIGATE_SMS_LOGS, null,
                            "sms.menu.view"),
                    item("messaging.reports", "SMS Reports", "SMS delivery report (PDF / Excel)",
                            "reports-view.fxml", "fas-chart-bar", ActivityLogService.NAVIGATE_REPORTS,
                            ReportGroup.MESSAGING, "report.menu.view"))),
            new NavModule("administration", "Administration", "fas-cog", "Users, security, backups and settings", 7,
                    List.of(
                            item("admin.users", "Users", "Create users, reset passwords, set start screens",
                                    "user-management-view.fxml", "fas-users", ActivityLogService.NAVIGATE_USERS,
                                    null, "user.menu.view"),
                            item("admin.roles", "Roles & Permissions", "What each role can see and do",
                                    "role-permission-view.fxml", "fas-user-lock", ActivityLogService.NAVIGATE_USERS,
                                    null, "role.menu.view"),
                            item("admin.activity-logs", "Activity Logs", "Who did what, and when",
                                    "activity-log-view.fxml", "fas-list-alt",
                                    ActivityLogService.NAVIGATE_ACTIVITY_LOGS, null, "activity.menu.view"),
                            item(BACKUP_ITEM_ID, "Backup & Restore",
                                    "Back up and restore the database (server machine only)",
                                    "backup-restore-view.fxml", "fas-database",
                                    ActivityLogService.NAVIGATE_BACKUP_RESTORE, null, "backup.menu.view"),
                            item("admin.reports", "Admin Reports", "User activity and backup history reports",
                                    "reports-view.fxml", "fas-chart-bar", ActivityLogService.NAVIGATE_REPORTS,
                                    ReportGroup.ADMINISTRATION, "report.menu.view"),
                            item("admin.settings", "Settings", "Organization, receipt, SMS and system settings",
                                    "settings-view.fxml", "fas-cog", null, null, "settings.menu.view")))
    );

    private NavigationRegistry() {
    }

    public static List<NavModule> modules() {
        return MODULES;
    }

    public static Optional<NavItem> findItem(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return MODULES.stream()
                .flatMap(module -> module.items().stream())
                .filter(item -> item.id().equals(id))
                .findFirst();
    }

    private static NavItem item(String id, String title, String description, String view, String icon,
                                String activityAction, ReportGroup reportGroup, String... permissionCodes) {
        return new NavItem(id, title, description, VIEW + view, icon, activityAction, null, reportGroup,
                List.of(permissionCodes));
    }
}
