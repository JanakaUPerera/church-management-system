# Module-Based Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat 16-button sidebar with a Home screen of module tiles and a per-module sidebar, all built from one navigation registry, with a per-user start screen.

**Architecture:** `NavItem` / `NavModule` records in a `NavigationRegistry` describe every screen. A pure-Java `NavigationAccess` applies permissions, the primary-machine rule, start-screen fallback and shortcuts. `DashboardController` builds the sidebar, switcher, breadcrumb and accelerators from them; a new `HomeController` builds the tile grid. The start screen is a nullable `users.landing_screen` column read at login and saved through a small `LandingScreenService`.

**Tech Stack:** Java 21, JavaFX 21 (FXML + code-built nodes, Ikonli FontAwesome 5 icons), manual JDBC (MySQL), Flyway, JUnit 5 (hand-written fakes), Maven.

**Spec:** [docs/superpowers/specs/2026-10-20-module-navigation-design.md](../specs/2026-10-20-module-navigation-design.md)

## Global Constraints

- Branch `feat/module-navigation` (from `develop`). Never commit `docs/PHASE-02-DEVELOPMENT-PLAN.md` or `src/main/resources/application.properties` — both hold the user's own uncommitted edits.
- No new Maven dependencies. Tests use hand-written fakes / `com.churchmanagement.testsupport.RecordingDataSource`, never Mockito.
- Item ids are stable and stored in the database: `dashboard.overview`, `dashboard.submission-status`, `collections.receipt`, `collections.receipt-history`, `collections.reports`, `branches.churches`, `branches.regions`, `branches.categories`, `messaging.send-message`, `messaging.sms-logs`, `messaging.reports`, `admin.users`, `admin.roles`, `admin.activity-logs`, `admin.backup`, `admin.reports`, `admin.settings`.
- Shortcut numbers: Dashboard 1, Collections 2, Branches 3, Messaging 4, Administration 7 (5 = Finance, 6 = Members, reserved).
- Sections in the sidebar only when a module has more than 7 visible items.
- Migration is `V42__user_landing_screen.sql` (latest existing is V41).
- Full test suite needs the dev database reachable (`src/main/resources/application.properties` host, currently `192.168.1.101:3308`); run single classes with `mvn -q test -Dtest=<Class>`.
- Commits: one per task, conventional-commits style with a detailed body, ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Existing user-facing behaviour of every content screen is unchanged; only the frame around them changes (plus the small hooks listed per task).

---

## File Structure

**Create** (`src/main/java/com/churchmanagement/...`)
- `dto/report/ReportGroup.java` — COLLECTIONS / MESSAGING / ADMINISTRATION.
- `navigation/NavItem.java`, `navigation/NavModule.java` — registry records.
- `navigation/NavigationRegistry.java` — the modules and items.
- `navigation/NavigationAccess.java` — visibility, landing, shortcut rules.
- `navigation/LandingOption.java` — an "Open after login" choice.
- `navigation/UnsavedChangesAware.java` — guard interface.
- `navigation/HomeNavigator.java` — callback from Home to the frame.
- `service/LandingScreenService.java` — options, read, save start screen.
- `service/HomeBadgeService.java` — tile badge texts.
- `controller/HomeController.java`, `resources/.../view/home-view.fxml` — tile grid.
- `resources/db/migration/V42__user_landing_screen.sql`.
- Tests: `dto/report/ReportTypeGroupTest`, `navigation/NavigationRegistryTest`, `navigation/NavigationAccessTest`, `service/LandingScreenServiceTest`, `service/HomeBadgeServiceTest`, `repository/UserRepositoryTest`.

**Modify**
- `dto/report/ReportType.java` (+`group()`), `controller/ReportsController.java` (+`setReportGroup`).
- `security/AuthenticatedUser.java`, `repository/UserRepository.java`, `service/AuthService.java` (landing screen at login).
- `repository/SmsLogRepository.java` (+`countRecentFailed`), `repository/DashboardRepository.java` (+`countPendingChurchesForWeek`).
- `service/ActivityLogService.java` (+`NAVIGATE_MODULE`).
- `controller/ReceiptEntryController.java`, `controller/SendMessageController.java` (implement `UnsavedChangesAware`).
- `controller/DashboardController.java`, `view/dashboard-view.fxml`, `view/app.css` (frame).
- `controller/UserProfileController.java`, `view/user-profile-view.fxml`, `controller/UserManagementController.java` (start-screen combo).
- `docs/user-manual.md`, `docs/user-manual-si.md`.
- Tests: `service/AuthServiceTest`, `repository/SmsLogRepositoryTest`.

---

### Task 1: Report groups

**Files:**
- Create: `src/main/java/com/churchmanagement/dto/report/ReportGroup.java`
- Modify: `src/main/java/com/churchmanagement/dto/report/ReportType.java`
- Modify: `src/main/java/com/churchmanagement/controller/ReportsController.java` (`buildReportCards` ~line 496, new `setReportGroup`)
- Test: `src/test/java/com/churchmanagement/dto/report/ReportTypeGroupTest.java`

**Interfaces:**
- Produces: `enum ReportGroup { COLLECTIONS, MESSAGING, ADMINISTRATION }`; `ReportGroup ReportType.group()`; `static List<ReportType> ReportType.inGroup(ReportGroup group)` (null → all, declaration order); `public void ReportsController.setReportGroup(ReportGroup group)`.

- [ ] **Step 1: Write the failing test**

```java
package com.churchmanagement.dto.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportTypeGroupTest {
    @Test
    void messagingHasOnlySmsDelivery() {
        assertEquals(List.of(ReportType.SMS_DELIVERY), ReportType.inGroup(ReportGroup.MESSAGING));
    }

    @Test
    void administrationHasActivityAndBackupHistory() {
        assertEquals(List.of(ReportType.USER_ACTIVITY, ReportType.BACKUP_RESTORE_HISTORY),
                ReportType.inGroup(ReportGroup.ADMINISTRATION));
    }

    @Test
    void collectionsHasTheOtherThirteenInOrder() {
        List<ReportType> collections = ReportType.inGroup(ReportGroup.COLLECTIONS);

        assertEquals(13, collections.size());
        assertEquals(ReportType.RECEIPT_COLLECTION, collections.getFirst());
        assertEquals(ReportType.RECEIPT_PRINT_STATUS, collections.getLast());
    }

    @Test
    void noGroupMeansEveryType() {
        assertEquals(List.of(ReportType.values()), ReportType.inGroup(null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=ReportTypeGroupTest`
Expected: compilation FAILURE — `cannot find symbol: class ReportGroup`.

- [ ] **Step 3: Implement**

`ReportGroup.java`:

```java
package com.churchmanagement.dto.report;

/** Which navigation module lists a report type. */
public enum ReportGroup {
    COLLECTIONS,
    MESSAGING,
    ADMINISTRATION
}
```

In `ReportType`, add (imports `java.util.Arrays`, `java.util.List`):

```java
    public ReportGroup group() {
        return switch (this) {
            case SMS_DELIVERY -> ReportGroup.MESSAGING;
            case USER_ACTIVITY, BACKUP_RESTORE_HISTORY -> ReportGroup.ADMINISTRATION;
            default -> ReportGroup.COLLECTIONS;
        };
    }

    public static List<ReportType> inGroup(ReportGroup group) {
        return Arrays.stream(values())
                .filter(type -> group == null || type.group() == group)
                .toList();
    }
```

In `ReportsController`, add a field next to `currentReportType`:

```java
    private ReportGroup reportGroup;
```

replace the loop in `buildReportCards()`:

```java
    private void buildReportCards() {
        reportCardPane.getChildren().clear();
        for (ReportType reportType : ReportType.inGroup(reportGroup)) {
            reportCardPane.getChildren().add(reportCard(reportType));
        }
    }
```

and add after `initialize()`:

```java
    /** Limits the screen to one module's reports. Called by the frame right after loading. */
    public void setReportGroup(ReportGroup group) {
        reportGroup = group;
        buildReportCards();
        ReportType.inGroup(group).stream().findFirst().ifPresent(type -> selectReportType(type, false));
    }
```

Import `com.churchmanagement.dto.report.ReportGroup`.

- [ ] **Step 4: Run tests and compile**

Run: `mvn -q test -Dtest=ReportTypeGroupTest` then `mvn -q compile`
Expected: PASS (4 tests); compile succeeds.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/dto/report/ReportGroup.java src/main/java/com/churchmanagement/dto/report/ReportType.java src/main/java/com/churchmanagement/controller/ReportsController.java src/test/java/com/churchmanagement/dto/report/ReportTypeGroupTest.java
git commit -m "feat(reports): group report types by navigation module" -m "Each ReportType now belongs to a ReportGroup: SMS Delivery to Messaging, User Activity and Backup & Restore History to Administration, every other report to Collections. ReportsController.setReportGroup limits the report cards to one group, so the same Reports screen can be opened from three modules. Without a group it still shows every report." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Navigation registry

**Files:**
- Create: `src/main/java/com/churchmanagement/navigation/NavItem.java`
- Create: `src/main/java/com/churchmanagement/navigation/NavModule.java`
- Create: `src/main/java/com/churchmanagement/navigation/NavigationRegistry.java`
- Modify: `src/main/java/com/churchmanagement/service/ActivityLogService.java` (constant `NAVIGATE_MODULE`)
- Test: `src/test/java/com/churchmanagement/navigation/NavigationRegistryTest.java`

**Interfaces:**
- Consumes: `ReportGroup` (Task 1); existing `ActivityLogService.NAVIGATE_*` constants.
- Produces:
  - `record NavItem(String id, String title, String description, String viewPath, String icon, String activityAction, String section, ReportGroup reportGroup, List<String> permissionCodes)` with `String[] permissionCodeArray()`.
  - `record NavModule(String id, String title, String icon, String description, int shortcutNumber, List<NavItem> items)`.
  - `NavigationRegistry`: `static List<NavModule> modules()`, `static Optional<NavItem> findItem(String id)`, constants `OVERVIEW_ITEM_ID = "dashboard.overview"`, `BACKUP_ITEM_ID = "admin.backup"`, `RECEIPT_ITEM_ID = "collections.receipt"`, `SEND_MESSAGE_ITEM_ID = "messaging.send-message"`.
  - `ActivityLogService.NAVIGATE_MODULE = "NAVIGATE_MODULE"`.

- [ ] **Step 1: Write the failing test**

```java
package com.churchmanagement.navigation;

import com.churchmanagement.dto.report.ReportGroup;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationRegistryTest {
    private static List<NavItem> allItems() {
        return NavigationRegistry.modules().stream().flatMap(module -> module.items().stream()).toList();
    }

    @Test
    void modulesInTileOrderWithFixedShortcuts() {
        assertEquals(List.of("dashboard", "collections", "branches", "messaging", "administration"),
                NavigationRegistry.modules().stream().map(NavModule::id).toList());
        assertEquals(List.of(1, 2, 3, 4, 7),
                NavigationRegistry.modules().stream().map(NavModule::shortcutNumber).toList());
    }

    @Test
    void itemIdsAreUniqueAndFindable() {
        Set<String> ids = new HashSet<>();
        for (NavItem item : allItems()) {
            assertTrue(ids.add(item.id()), "duplicate id " + item.id());
            assertEquals(item, NavigationRegistry.findItem(item.id()).orElseThrow());
        }
        assertEquals(17, ids.size());
        assertTrue(NavigationRegistry.findItem("no.such.item").isEmpty());
        assertTrue(NavigationRegistry.findItem(null).isEmpty());
    }

    @Test
    void everyViewExistsOnTheClasspath() {
        for (NavItem item : allItems()) {
            assertNotNull(NavigationRegistry.class.getResource(item.viewPath()), item.id() + " -> " + item.viewPath());
        }
    }

    @Test
    void reportItemsCarryTheirGroup() {
        assertEquals(ReportGroup.COLLECTIONS, NavigationRegistry.findItem("collections.reports").orElseThrow().reportGroup());
        assertEquals(ReportGroup.MESSAGING, NavigationRegistry.findItem("messaging.reports").orElseThrow().reportGroup());
        assertEquals(ReportGroup.ADMINISTRATION, NavigationRegistry.findItem("admin.reports").orElseThrow().reportGroup());
    }

    @Test
    void wellKnownIdsExist() {
        assertTrue(NavigationRegistry.findItem(NavigationRegistry.OVERVIEW_ITEM_ID).isPresent());
        assertTrue(NavigationRegistry.findItem(NavigationRegistry.BACKUP_ITEM_ID).isPresent());
        assertTrue(NavigationRegistry.findItem(NavigationRegistry.RECEIPT_ITEM_ID).isPresent());
        assertTrue(NavigationRegistry.findItem(NavigationRegistry.SEND_MESSAGE_ITEM_ID).isPresent());
    }

    @Test
    void overviewNeedsNoPermission() {
        assertTrue(NavigationRegistry.findItem(NavigationRegistry.OVERVIEW_ITEM_ID).orElseThrow()
                .permissionCodes().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=NavigationRegistryTest`
Expected: compilation FAILURE — `package com.churchmanagement.navigation does not exist`.

- [ ] **Step 3: Implement**

`NavItem.java`:

```java
package com.churchmanagement.navigation;

import com.churchmanagement.dto.report.ReportGroup;

import java.util.List;

/**
 * One screen in the navigation registry. {@code id} is stored in users.landing_screen, so it must
 * never be renamed. {@code section} groups items in the sidebar of large modules (may be null).
 */
public record NavItem(String id, String title, String description, String viewPath, String icon,
                      String activityAction, String section, ReportGroup reportGroup,
                      List<String> permissionCodes) {
    public NavItem {
        permissionCodes = List.copyOf(permissionCodes);
    }

    public String[] permissionCodeArray() {
        return permissionCodes.toArray(String[]::new);
    }
}
```

`NavModule.java`:

```java
package com.churchmanagement.navigation;

import java.util.List;

/** A Home tile and its sidebar. {@code shortcutNumber} is Ctrl+number and is fixed per module. */
public record NavModule(String id, String title, String icon, String description, int shortcutNumber,
                        List<NavItem> items) {
    public NavModule {
        items = List.copyOf(items);
    }
}
```

`NavigationRegistry.java`:

```java
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
```

In `ActivityLogService`, next to `NAVIGATE_SEND_MESSAGE`:

```java
    public static final String NAVIGATE_MODULE = "NAVIGATE_MODULE";
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=NavigationRegistryTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/navigation/NavItem.java src/main/java/com/churchmanagement/navigation/NavModule.java src/main/java/com/churchmanagement/navigation/NavigationRegistry.java src/main/java/com/churchmanagement/service/ActivityLogService.java src/test/java/com/churchmanagement/navigation/NavigationRegistryTest.java
git commit -m "feat(navigation): registry of modules and screens" -m "NavigationRegistry describes every screen once: stable id, title, one-line description, view, icon, activity code, report group and menu permission, grouped into the Dashboard, Collections, Branches, Messaging and Administration modules in tile order with fixed Ctrl+number shortcuts (Administration keeps 7). Adds the NAVIGATE_MODULE activity code." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: NavigationAccess

**Files:**
- Create: `src/main/java/com/churchmanagement/navigation/NavigationAccess.java`
- Test: `src/test/java/com/churchmanagement/navigation/NavigationAccessTest.java`

**Interfaces:**
- Consumes: `NavItem`, `NavModule`, `NavigationRegistry` (Task 2); existing `AuthenticatedUser`, `PermissionGuard` (Admin role sees everything; force-password-change sees nothing).
- Produces: `NavigationAccess(AuthenticatedUser user, boolean primaryMachine)`; package-private `NavigationAccess(AuthenticatedUser, boolean, List<NavModule>)`; methods `boolean canOpen(NavItem)`, `List<NavItem> visibleItems(NavModule)`, `List<NavModule> visibleModules()`, `List<NavItem> allVisibleItems()`, `Optional<NavModule> moduleOf(NavItem)`, `boolean opensDirectly(NavModule)`, `boolean showHomeAndSwitcher()`, `boolean useSections(NavModule)`, `Optional<NavModule> moduleForShortcut(int)`, `Landing landing(String savedItemId)`; nested `record Landing(NavItem item, boolean fellBack)` (`item` null only when nothing at all is visible).

- [ ] **Step 1: Write the failing test**

```java
package com.churchmanagement.navigation;

import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationAccessTest {
    private static AuthenticatedUser user(String... permissions) {
        return new AuthenticatedUser(5L, "clerk", "Clerk", 2L, "Clerk", List.of(permissions));
    }

    private static AuthenticatedUser admin() {
        return new AuthenticatedUser(1L, "admin", "Admin", 1L, "Admin", List.of());
    }

    private static NavModule module(String id) {
        return NavigationRegistry.modules().stream().filter(module -> module.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<String> ids(List<NavItem> items) {
        return items.stream().map(NavItem::id).toList();
    }

    @Test
    void itemsFilteredByPermission() {
        NavigationAccess access = new NavigationAccess(user("receipt.entry.menu.view"), true);

        assertEquals(List.of("collections.receipt"), ids(access.visibleItems(module("collections"))));
    }

    @Test
    void itemWithoutPermissionCodesIsAlwaysVisible() {
        NavigationAccess access = new NavigationAccess(user(), true);

        assertEquals(List.of("dashboard.overview"), ids(access.visibleItems(module("dashboard"))));
    }

    @Test
    void backupHiddenOnSecondaryMachineEvenForAdmin() {
        assertTrue(ids(new NavigationAccess(admin(), true).visibleItems(module("administration"))).contains("admin.backup"));
        assertFalse(ids(new NavigationAccess(admin(), false).visibleItems(module("administration"))).contains("admin.backup"));
    }

    @Test
    void moduleHiddenWhenNoItemVisible() {
        NavigationAccess access = new NavigationAccess(user("receipt.entry.menu.view"), true);

        assertEquals(List.of("dashboard", "collections"), access.visibleModules().stream().map(NavModule::id).toList());
    }

    @Test
    void singleItemModuleOpensDirectly() {
        NavigationAccess access = new NavigationAccess(user("receipt.entry.menu.view"), true);

        assertTrue(access.opensDirectly(module("collections")));
        assertFalse(new NavigationAccess(admin(), true).opensDirectly(module("collections")));
    }

    @Test
    void homeAndSwitcherHiddenForSingleModuleUser() {
        assertFalse(new NavigationAccess(user(), true).showHomeAndSwitcher());
        assertTrue(new NavigationAccess(user("church.menu.view"), true).showHomeAndSwitcher());
    }

    @Test
    void sectionsOnlyAboveSevenItems() {
        List<NavItem> seven = items(7);
        List<NavItem> eight = items(8);
        NavModule small = new NavModule("small", "Small", "fas-home", "", 5, seven);
        NavModule large = new NavModule("large", "Large", "fas-home", "", 6, eight);
        NavigationAccess access = new NavigationAccess(admin(), true, List.of(small, large));

        assertFalse(access.useSections(small));
        assertTrue(access.useSections(large));
    }

    @Test
    void landingUsesSavedItemWhenVisible() {
        NavigationAccess.Landing landing = new NavigationAccess(user("receipt.entry.menu.view"), true)
                .landing("collections.receipt");

        assertEquals("collections.receipt", landing.item().id());
        assertFalse(landing.fellBack());
    }

    @Test
    void landingDefaultsToOverview() {
        NavigationAccess.Landing landing = new NavigationAccess(user(), true).landing(null);

        assertEquals("dashboard.overview", landing.item().id());
        assertFalse(landing.fellBack());
    }

    @Test
    void landingFallsBackWhenSavedItemNotPermitted() {
        NavigationAccess.Landing landing = new NavigationAccess(user(), true).landing("admin.users");

        assertEquals("dashboard.overview", landing.item().id());
        assertTrue(landing.fellBack());
    }

    @Test
    void landingFallsBackForUnknownId() {
        assertTrue(new NavigationAccess(user(), true).landing("renamed.screen").fellBack());
    }

    @Test
    void landingNullWhenNothingVisible() {
        AuthenticatedUser mustChange = user("receipt.entry.menu.view").withForcePasswordChange(true);

        assertNull(new NavigationAccess(mustChange, true).landing(null).item());
    }

    @Test
    void shortcutsMapToVisibleModulesOnly() {
        NavigationAccess access = new NavigationAccess(user("church.menu.view"), true);

        assertEquals("branches", access.moduleForShortcut(3).orElseThrow().id());
        assertTrue(access.moduleForShortcut(7).isEmpty());
        assertTrue(access.moduleForShortcut(5).isEmpty());
    }

    @Test
    void moduleOfFindsTheOwningModule() {
        NavigationAccess access = new NavigationAccess(admin(), true);

        assertEquals("messaging", access.moduleOf(NavigationRegistry.findItem("messaging.reports").orElseThrow())
                .orElseThrow().id());
    }

    @Test
    void allVisibleItemsInRegistryOrder() {
        NavigationAccess access = new NavigationAccess(user("church.menu.view", "region.menu.view"), true);

        assertEquals(List.of("dashboard.overview", "branches.churches", "branches.regions"),
                ids(access.allVisibleItems()));
    }

    private static List<NavItem> items(int count) {
        List<NavItem> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            items.add(new NavItem("test." + index, "Item " + index, "", "/x.fxml", "fas-home", null, "S", null,
                    List.of()));
        }
        return items;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=NavigationAccessTest`
Expected: compilation FAILURE — `cannot find symbol: class NavigationAccess`.

- [ ] **Step 3: Implement**

```java
package com.churchmanagement.navigation;

import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

import java.util.List;
import java.util.Optional;

/** Which modules and screens a user can see, where they start, and what each shortcut opens. */
public class NavigationAccess {
    static final int SECTION_THRESHOLD = 7;

    private final PermissionGuard permissionGuard;
    private final boolean primaryMachine;
    private final List<NavModule> modules;

    public NavigationAccess(AuthenticatedUser user, boolean primaryMachine) {
        this(user, primaryMachine, NavigationRegistry.modules());
    }

    NavigationAccess(AuthenticatedUser user, boolean primaryMachine, List<NavModule> modules) {
        this.permissionGuard = new PermissionGuard(user);
        this.primaryMachine = primaryMachine;
        this.modules = modules;
    }

    public boolean canOpen(NavItem item) {
        if (NavigationRegistry.BACKUP_ITEM_ID.equals(item.id()) && !primaryMachine) {
            return false;
        }
        return permissionGuard.canAny(item.permissionCodeArray());
    }

    public List<NavItem> visibleItems(NavModule module) {
        return module.items().stream().filter(this::canOpen).toList();
    }

    public List<NavModule> visibleModules() {
        return modules.stream().filter(module -> !visibleItems(module).isEmpty()).toList();
    }

    public List<NavItem> allVisibleItems() {
        return visibleModules().stream().flatMap(module -> visibleItems(module).stream()).toList();
    }

    public Optional<NavModule> moduleOf(NavItem item) {
        return modules.stream().filter(module -> module.items().contains(item)).findFirst();
    }

    public boolean opensDirectly(NavModule module) {
        return visibleItems(module).size() == 1;
    }

    public boolean showHomeAndSwitcher() {
        return visibleModules().size() > 1;
    }

    public boolean useSections(NavModule module) {
        return visibleItems(module).size() > SECTION_THRESHOLD;
    }

    public Optional<NavModule> moduleForShortcut(int number) {
        return visibleModules().stream().filter(module -> module.shortcutNumber() == number).findFirst();
    }

    public Landing landing(String savedItemId) {
        boolean hadSaved = savedItemId != null && !savedItemId.isBlank();
        Optional<NavItem> saved = NavigationRegistry.findItem(savedItemId).filter(this::canOpen);
        if (saved.isPresent()) {
            return new Landing(saved.get(), false);
        }
        Optional<NavItem> overview = NavigationRegistry.findItem(NavigationRegistry.OVERVIEW_ITEM_ID)
                .filter(this::canOpen);
        if (overview.isPresent()) {
            return new Landing(overview.get(), hadSaved);
        }
        List<NavItem> visible = allVisibleItems();
        return new Landing(visible.isEmpty() ? null : visible.getFirst(), hadSaved);
    }

    /** The screen to open after login; {@code fellBack} is true when a saved choice could not be used. */
    public record Landing(NavItem item, boolean fellBack) {
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q test -Dtest=NavigationAccessTest`
Expected: PASS (15 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/navigation/NavigationAccess.java src/test/java/com/churchmanagement/navigation/NavigationAccessTest.java
git commit -m "feat(navigation): visibility, start-screen and shortcut rules" -m "NavigationAccess applies the existing PermissionGuard (Admin sees everything, a pending password change sees nothing) and the server-machine-only rule for Backup & Restore to the registry: visible screens and modules, single-screen modules, whether Home and the switcher are shown, section headers above seven screens, Ctrl+number lookup, and the start screen after login with its fallback to Dashboard > Overview or the first visible screen." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Start screen persistence

**Files:**
- Create: `src/main/resources/db/migration/V42__user_landing_screen.sql`
- Create: `src/main/java/com/churchmanagement/navigation/LandingOption.java`
- Create: `src/main/java/com/churchmanagement/service/LandingScreenService.java`
- Modify: `src/main/java/com/churchmanagement/security/AuthenticatedUser.java`
- Modify: `src/main/java/com/churchmanagement/repository/UserRepository.java` (`findByUsername`, `UserCredentials`, new `updateLandingScreen`, `findLandingScreen`)
- Modify: `src/main/java/com/churchmanagement/service/AuthService.java` (~line 56)
- Modify: `src/test/java/com/churchmanagement/service/AuthServiceTest.java` (`UserCredentials` constructions)
- Test: `src/test/java/com/churchmanagement/repository/UserRepositoryTest.java`, `src/test/java/com/churchmanagement/service/LandingScreenServiceTest.java`

**Interfaces:**
- Consumes: `NavigationAccess`, `NavigationRegistry` (Tasks 2–3); existing `PermissionRepository.findPermissionCodesByRoleId(long)`, `AuthContext.setCurrentUser`, `PrimaryMachine.isPrimary()`.
- Produces:
  - `AuthenticatedUser`: 9-arg constructor `(…, String profilePicturePath, String landingScreen)`, `String getLandingScreen()`, `AuthenticatedUser withLandingScreen(String)`; `withForcePasswordChange` / `withProfile` keep the landing screen.
  - `UserRepository.UserCredentials` gains a 10th component `String landingScreen`; `void updateLandingScreen(long userId, String itemId)`; `Optional<String> findLandingScreen(long userId)`.
  - `record LandingOption(String itemId, String label)` (`itemId` null = default) with `toString()` returning `label`; `LandingOption.DEFAULT`.
  - `LandingScreenService`: `List<LandingOption> optionsFor(AuthenticatedUser)`, `List<LandingOption> optionsForRole(long roleId, String roleName)`, `Optional<String> findForUser(long userId)`, `void saveOwn(String itemId)`, `void saveForUser(long userId, String itemId)`; nested `LandingScreenException extends RuntimeException`.

- [ ] **Step 1: Write the migration**

`V42__user_landing_screen.sql`:

```sql
ALTER TABLE users
    ADD COLUMN landing_screen VARCHAR(80) NULL AFTER profile_picture_path;
```

- [ ] **Step 2: Write the failing tests**

`UserRepositoryTest.java`:

```java
package com.churchmanagement.repository;

import com.churchmanagement.testsupport.RecordingDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserRepositoryTest {
    @Test
    void loginLookupReadsLandingScreen() {
        RecordingDataSource dataSource = new RecordingDataSource();

        new UserRepository(dataSource).findByUsername("admin");

        assertTrue(dataSource.lastSql().contains("u.landing_screen"));
    }

    @Test
    void updatesLandingScreen() {
        RecordingDataSource dataSource = new RecordingDataSource();

        new UserRepository(dataSource).updateLandingScreen(7L, "collections.receipt");

        assertTrue(dataSource.lastSql().contains("SET landing_screen = ?"));
        assertEquals("collections.receipt", dataSource.lastValues().get(0));
        assertEquals(7L, dataSource.lastValues().get(1));
    }

    @Test
    void readsLandingScreenById() {
        RecordingDataSource dataSource = new RecordingDataSource();

        assertTrue(new UserRepository(dataSource).findLandingScreen(7L).isEmpty());
        assertTrue(dataSource.lastSql().contains("SELECT landing_screen FROM users WHERE id = ?"));
    }
}
```

`LandingScreenServiceTest.java`:

```java
package com.churchmanagement.service;

import com.churchmanagement.navigation.LandingOption;
import com.churchmanagement.repository.PermissionRepository;
import com.churchmanagement.repository.UserRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LandingScreenServiceTest {
    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final LandingScreenService service = new LandingScreenService(userRepository,
            new FakePermissionRepository(), true);

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void optionsStartWithDefaultThenVisibleScreens() {
        List<LandingOption> options = service.optionsFor(clerk());

        assertEquals(LandingOption.DEFAULT, options.getFirst());
        assertEquals(List.of("Dashboard (default)", "Collections › Receipt"),
                options.stream().map(LandingOption::label).toList());
    }

    @Test
    void optionsForRoleUseThatRolesPermissions() {
        List<LandingOption> options = service.optionsForRole(2L, "Clerk");

        assertEquals(List.of(null, "collections.receipt"), options.stream().map(LandingOption::itemId).toList());
    }

    @Test
    void saveOwnStoresAndUpdatesSession() {
        AuthContext.setCurrentUser(clerk());

        service.saveOwn("collections.receipt");

        assertEquals(5L, userRepository.savedUserId);
        assertEquals("collections.receipt", userRepository.savedItemId);
        assertEquals("collections.receipt", AuthContext.getCurrentUser().orElseThrow().getLandingScreen());
    }

    @Test
    void saveOwnBlankMeansDefault() {
        AuthContext.setCurrentUser(clerk());

        service.saveOwn("  ");

        assertNull(userRepository.savedItemId);
        assertEquals(5L, userRepository.savedUserId);
    }

    @Test
    void rejectsUnknownScreen() {
        AuthContext.setCurrentUser(clerk());

        assertThrows(LandingScreenService.LandingScreenException.class, () -> service.saveOwn("no.such"));
    }

    @Test
    void saveForUserNeedsUserUpdatePermission() {
        AuthContext.setCurrentUser(clerk());

        LandingScreenService.LandingScreenException exception = assertThrows(
                LandingScreenService.LandingScreenException.class,
                () -> service.saveForUser(9L, "collections.receipt"));

        assertEquals("You do not have permission to update users.", exception.getMessage());
    }

    @Test
    void saveForUserWithPermission() {
        AuthContext.setCurrentUser(new AuthenticatedUser(1L, "manager", "Manager", 3L, "Manager",
                List.of("user.update")));

        service.saveForUser(9L, "collections.receipt");

        assertEquals(9L, userRepository.savedUserId);
        assertEquals("collections.receipt", userRepository.savedItemId);
    }

    @Test
    void findForUserReadsRepository() {
        userRepository.stored = "branches.churches";

        assertEquals(Optional.of("branches.churches"), service.findForUser(9L));
    }

    private static AuthenticatedUser clerk() {
        return new AuthenticatedUser(5L, "clerk", "Clerk", 2L, "Clerk", List.of("receipt.entry.menu.view"));
    }

    private static class FakeUserRepository extends UserRepository {
        private Long savedUserId;
        private String savedItemId = "unset";
        private String stored;

        private FakeUserRepository() {
            super((DataSource) null);
        }

        @Override
        public void updateLandingScreen(long userId, String itemId) {
            savedUserId = userId;
            savedItemId = itemId;
        }

        @Override
        public Optional<String> findLandingScreen(long userId) {
            return Optional.ofNullable(stored);
        }
    }

    private static class FakePermissionRepository extends PermissionRepository {
        private FakePermissionRepository() {
            super((DataSource) null);
        }

        @Override
        public List<String> findPermissionCodesByRoleId(long roleId) {
            return List.of("receipt.entry.menu.view");
        }
    }
}
```

In `AuthServiceTest`, add one trailing argument `null` (the landing screen) to every `new UserCredentials(…)` call, and add this test (it uses the existing fake that returns user id 1 / "admin" / "admin123"; set the fake's landing screen field as shown):

```java
    @Test
    void loginCarriesLandingScreen() {
        userRepository.landingScreen = "collections.receipt";

        AuthenticatedUser user = authService.login("admin", "admin123");

        assertEquals("collections.receipt", user.getLandingScreen());
    }
```

and in the fake repository add `private String landingScreen;` and pass `landingScreen` as the new last constructor argument instead of `null`. (Adapt the field names `userRepository` / `authService` to the ones the test class already uses.)

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -q test -Dtest=UserRepositoryTest,LandingScreenServiceTest,AuthServiceTest`
Expected: compilation FAILURE — `cannot find symbol: method updateLandingScreen`.

- [ ] **Step 4: Implement**

`AuthenticatedUser` — add `private final String landingScreen;`, make the existing 8-arg constructor delegate:

```java
    public AuthenticatedUser(long userId, String username, String fullName, long roleId, String roleName,
                             List<String> permissions, boolean forcePasswordChange, String profilePicturePath) {
        this(userId, username, fullName, roleId, roleName, permissions, forcePasswordChange, profilePicturePath,
                null);
    }

    public AuthenticatedUser(long userId, String username, String fullName, long roleId, String roleName,
                             List<String> permissions, boolean forcePasswordChange, String profilePicturePath,
                             String landingScreen) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.roleId = roleId;
        this.roleName = roleName;
        this.permissions = List.copyOf(permissions);
        this.permissionSet = new HashSet<>(this.permissions);
        this.forcePasswordChange = forcePasswordChange;
        this.profilePicturePath = profilePicturePath;
        this.landingScreen = landingScreen;
    }
```

change `withForcePasswordChange` and `withProfile` to pass `landingScreen` as the last argument, and add:

```java
    public String getLandingScreen() {
        return landingScreen;
    }

    public AuthenticatedUser withLandingScreen(String landingScreen) {
        return new AuthenticatedUser(userId, username, fullName, roleId, roleName, permissions, forcePasswordChange,
                profilePicturePath, landingScreen);
    }
```

`UserRepository`:
- In `findByUsername`, change the select's first line to `SELECT u.id, u.username, u.password_hash, u.full_name, u.active, u.profile_picture_path, u.landing_screen,` and pass `resultSet.getString("landing_screen")` as the new last `UserCredentials` argument.
- Add `String landingScreen` as the last component of `record UserCredentials`.
- Add:

```java
    public void updateLandingScreen(long userId, String itemId) {
        String sql = "UPDATE users SET landing_screen = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemId);
            statement.setLong(2, userId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save the start screen.", exception);
        }
    }

    public Optional<String> findLandingScreen(long userId) {
        String sql = "SELECT landing_screen FROM users WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.ofNullable(resultSet.getString("landing_screen")) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load the start screen.", exception);
        }
    }
```

`AuthService` — pass `credentials.landingScreen()` as the new last argument of `new AuthenticatedUser(...)`.

`LandingOption.java`:

```java
package com.churchmanagement.navigation;

/** One "Open after login" choice. A null {@code itemId} means the default (Dashboard > Overview). */
public record LandingOption(String itemId, String label) {
    public static final LandingOption DEFAULT = new LandingOption(null, "Dashboard (default)");

    @Override
    public String toString() {
        return label;
    }
}
```

`LandingScreenService.java`:

```java
package com.churchmanagement.service;

import com.churchmanagement.config.PrimaryMachine;
import com.churchmanagement.navigation.LandingOption;
import com.churchmanagement.navigation.NavItem;
import com.churchmanagement.navigation.NavigationAccess;
import com.churchmanagement.navigation.NavigationRegistry;
import com.churchmanagement.repository.PermissionRepository;
import com.churchmanagement.repository.UserRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads and saves a user's "Open after login" screen (users.landing_screen). */
public class LandingScreenService {
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final boolean primaryMachine;

    public LandingScreenService() {
        this(new UserRepository(), new PermissionRepository(), PrimaryMachine.isPrimary());
    }

    public LandingScreenService(UserRepository userRepository, PermissionRepository permissionRepository,
                                boolean primaryMachine) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.primaryMachine = primaryMachine;
    }

    public List<LandingOption> optionsFor(AuthenticatedUser user) {
        NavigationAccess access = new NavigationAccess(user, primaryMachine);
        List<LandingOption> options = new ArrayList<>();
        options.add(LandingOption.DEFAULT);
        for (NavItem item : access.allVisibleItems()) {
            if (!NavigationRegistry.OVERVIEW_ITEM_ID.equals(item.id())) {
                String module = access.moduleOf(item).map(found -> found.title()).orElse("");
                options.add(new LandingOption(item.id(), module + " › " + item.title()));
            }
        }
        return options;
    }

    public List<LandingOption> optionsForRole(long roleId, String roleName) {
        List<String> permissions = permissionRepository.findPermissionCodesByRoleId(roleId);
        return optionsFor(new AuthenticatedUser(0L, "", "", roleId, roleName, permissions));
    }

    public Optional<String> findForUser(long userId) {
        return userRepository.findLandingScreen(userId);
    }

    public void saveOwn(String itemId) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new LandingScreenException("Please sign in to change your start screen."));
        String normalized = normalize(itemId);
        userRepository.updateLandingScreen(currentUser.getUserId(), normalized);
        AuthContext.setCurrentUser(currentUser.withLandingScreen(normalized));
    }

    public void saveForUser(long userId, String itemId) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new LandingScreenException("Please sign in to update users."));
        if (!new PermissionGuard(currentUser).can("user.update")) {
            throw new LandingScreenException("You do not have permission to update users.");
        }
        userRepository.updateLandingScreen(userId, normalize(itemId));
    }

    private String normalize(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        String stripped = itemId.strip();
        if (NavigationRegistry.findItem(stripped).isEmpty()) {
            throw new LandingScreenException("Unknown start screen.");
        }
        return stripped;
    }

    public static class LandingScreenException extends RuntimeException {
        public LandingScreenException(String message) {
            super(message);
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q test -Dtest=UserRepositoryTest,LandingScreenServiceTest,AuthServiceTest,PasswordChangeServiceTest,UserProfileServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V42__user_landing_screen.sql src/main/java/com/churchmanagement/navigation/LandingOption.java src/main/java/com/churchmanagement/service/LandingScreenService.java src/main/java/com/churchmanagement/security/AuthenticatedUser.java src/main/java/com/churchmanagement/repository/UserRepository.java src/main/java/com/churchmanagement/service/AuthService.java src/test/java/com/churchmanagement/repository/UserRepositoryTest.java src/test/java/com/churchmanagement/service/LandingScreenServiceTest.java src/test/java/com/churchmanagement/service/AuthServiceTest.java
git commit -m "feat(users): store each user's start screen" -m "V42 adds a nullable users.landing_screen holding a navigation item id. Login reads it into AuthenticatedUser. LandingScreenService lists the 'Open after login' choices a user (or a role) can open, and saves a user's own choice or, with user.update, another user's. Blank means the default, Dashboard > Overview; unknown ids are rejected on save and fall back at login." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Home tile badges

**Files:**
- Create: `src/main/java/com/churchmanagement/service/HomeBadgeService.java`
- Modify: `src/main/java/com/churchmanagement/repository/SmsLogRepository.java` (new `countRecentFailed`)
- Modify: `src/main/java/com/churchmanagement/repository/DashboardRepository.java` (new public `countPendingChurchesForWeek`)
- Test: `src/test/java/com/churchmanagement/service/HomeBadgeServiceTest.java`, `src/test/java/com/churchmanagement/repository/SmsLogRepositoryTest.java`

**Interfaces:**
- Consumes: existing private `DashboardRepository.countPendingChurches(LocalDate, LocalDate, Long)`, `WeekUtil.currentIdentifier`, `WeekUtil.weekStartFor`, `WeekUtil.parseIdentifierDay`, `WeekUtil.IDENTIFIER_DAY_SETTING_KEY`, `SystemConfigurationCache.getString`.
- Produces: `long SmsLogRepository.countRecentFailed(LocalDateTime since)`; `long DashboardRepository.countPendingChurchesForWeek(LocalDate weekStart, LocalDate weekEnd)`; `HomeBadgeService` with `Map<String, String> badgeTexts()` keyed by module id (`"collections"` → "N pending", `"messaging"` → "N failed"; a zero count or a failure omits the key).

- [ ] **Step 1: Write the failing tests**

Add to `SmsLogRepositoryTest` (uses its inner `RecordingDataSource`):

```java
    @Test
    void countRecentFailedExcludesResentRows() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);

        long count = repository.countRecentFailed(LocalDateTime.of(2026, 10, 13, 9, 0));

        assertEquals(0L, count);
        assertTrue(dataSource.sql.contains("resend_of_sms_log_id = sl.id"));
        assertEquals(SmsSendStatus.FAILED.name(), dataSource.valueAt(1));
    }
```

`HomeBadgeServiceTest.java`:

```java
package com.churchmanagement.service;

import com.churchmanagement.repository.DashboardRepository;
import com.churchmanagement.repository.SmsLogRepository;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeBadgeServiceTest {
    // 21 Oct 2026, identifier day MONDAY; the expected week is computed with WeekUtil below.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneId.of("UTC"));

    @Test
    void badgesForPendingAndFailed() {
        FakeDashboardRepository dashboard = new FakeDashboardRepository(12);
        FakeSmsLogRepository sms = new FakeSmsLogRepository(3);

        Map<String, String> badges = service(dashboard, sms).badgeTexts();

        assertEquals(Map.of("collections", "12 pending", "messaging", "3 failed"), badges);
        assertEquals(LocalDateTime.of(2026, 10, 14, 9, 0), sms.since);
        assertEquals(WeekUtilRange.start(), dashboard.weekStart);
        assertEquals(WeekUtilRange.end(), dashboard.weekEnd);
    }

    @Test
    void zeroCountsHaveNoBadge() {
        assertEquals(Map.of(), service(new FakeDashboardRepository(0), new FakeSmsLogRepository(0)).badgeTexts());
    }

    @Test
    void failureHidesOnlyThatBadge() {
        FakeDashboardRepository failing = new FakeDashboardRepository(-1);

        assertEquals(Map.of("messaging", "3 failed"),
                service(failing, new FakeSmsLogRepository(3)).badgeTexts());
    }

    private HomeBadgeService service(DashboardRepository dashboard, SmsLogRepository sms) {
        return new HomeBadgeService(dashboard, sms, new FakeSystemConfigurationCache(), CLOCK);
    }

    /** The week the service must ask for, computed the same way Receipt Entry does. */
    private static final class WeekUtilRange {
        static LocalDate end() {
            return com.churchmanagement.util.WeekUtil.currentIdentifier(LocalDate.of(2026, 10, 21),
                    java.time.DayOfWeek.MONDAY);
        }

        static LocalDate start() {
            return com.churchmanagement.util.WeekUtil.weekStartFor(end());
        }
    }

    private static class FakeDashboardRepository extends DashboardRepository {
        private final long count;
        private LocalDate weekStart;
        private LocalDate weekEnd;

        private FakeDashboardRepository(long count) {
            super((DataSource) null);
            this.count = count;
        }

        @Override
        public long countPendingChurchesForWeek(LocalDate weekStart, LocalDate weekEnd) {
            if (count < 0) {
                throw new IllegalStateException("database down");
            }
            this.weekStart = weekStart;
            this.weekEnd = weekEnd;
            return count;
        }
    }

    private static class FakeSmsLogRepository extends SmsLogRepository {
        private final long count;
        private LocalDateTime since;

        private FakeSmsLogRepository(long count) {
            super((DataSource) null);
            this.count = count;
        }

        @Override
        public long countRecentFailed(LocalDateTime since) {
            this.since = since;
            return count;
        }
    }

    private static class FakeSystemConfigurationCache extends SystemConfigurationCache {
        private FakeSystemConfigurationCache() {
            super(null);
        }

        @Override
        public String getString(String key) {
            return "MONDAY";
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q test -Dtest=HomeBadgeServiceTest,SmsLogRepositoryTest`
Expected: compilation FAILURE — `cannot find symbol: class HomeBadgeService`.

- [ ] **Step 3: Implement**

`SmsLogRepository` — add after `reclaimStaleSending`:

```java
    /** FAILED messages since the given time that have not been resent (drives the Messaging tile badge). */
    public long countRecentFailed(LocalDateTime since) {
        String sql = """
                SELECT COUNT(*) AS failed_count
                FROM sms_logs sl
                WHERE sl.status = ?
                  AND sl.created_at >= ?
                  AND NOT EXISTS (SELECT 1 FROM sms_logs newer WHERE newer.resend_of_sms_log_id = sl.id)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SmsSendStatus.FAILED.name());
            statement.setTimestamp(2, Timestamp.valueOf(since));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong("failed_count") : 0L;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to count failed SMS.", exception);
        }
    }
```

`DashboardRepository` — add next to `getWeeklySummary`:

```java
    /** Branches with no active receipt in the given week, all regions (drives the Collections tile badge). */
    public long countPendingChurchesForWeek(LocalDate weekStartDate, LocalDate weekEndDate) {
        return countPendingChurches(weekStartDate, weekEndDate, null);
    }
```

`HomeBadgeService.java`:

```java
package com.churchmanagement.service;

import com.churchmanagement.repository.DashboardRepository;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.util.WeekUtil;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Small status counts shown on Home tiles. A failing count is simply left out. */
public class HomeBadgeService {
    private static final int FAILED_SMS_DAYS = 7;

    private final DashboardRepository dashboardRepository;
    private final SmsLogRepository smsLogRepository;
    private final SystemConfigurationCache configurationCache;
    private final Clock clock;

    public HomeBadgeService() {
        this(new DashboardRepository(), new SmsLogRepository(), SystemConfigurationCache.getInstance(),
                Clock.systemDefaultZone());
    }

    public HomeBadgeService(DashboardRepository dashboardRepository, SmsLogRepository smsLogRepository,
                            SystemConfigurationCache configurationCache, Clock clock) {
        this.dashboardRepository = dashboardRepository;
        this.smsLogRepository = smsLogRepository;
        this.configurationCache = configurationCache;
        this.clock = clock;
    }

    /** Badge text keyed by module id; modules with nothing to report are absent. */
    public Map<String, String> badgeTexts() {
        Map<String, String> badges = new LinkedHashMap<>();
        put(badges, "collections", this::pendingSubmissions, " pending");
        put(badges, "messaging", this::recentFailedSms, " failed");
        return badges;
    }

    private long pendingSubmissions() {
        DayOfWeek identifierDay = WeekUtil.parseIdentifierDay(
                configurationCache.getString(WeekUtil.IDENTIFIER_DAY_SETTING_KEY));
        LocalDate identifier = WeekUtil.currentIdentifier(LocalDate.now(clock), identifierDay);
        return dashboardRepository.countPendingChurchesForWeek(WeekUtil.weekStartFor(identifier), identifier);
    }

    private long recentFailedSms() {
        return smsLogRepository.countRecentFailed(LocalDateTime.now(clock).minusDays(FAILED_SMS_DAYS));
    }

    private void put(Map<String, String> badges, String moduleId, LongSupplier count, String suffix) {
        try {
            long value = count.getAsLong();
            if (value > 0) {
                badges.put(moduleId, value + suffix);
            }
        } catch (RuntimeException exception) {
            System.err.println("Home badge '" + moduleId + "' unavailable: " + exception.getMessage());
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q test -Dtest=HomeBadgeServiceTest,SmsLogRepositoryTest,DashboardServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/churchmanagement/service/HomeBadgeService.java src/main/java/com/churchmanagement/repository/SmsLogRepository.java src/main/java/com/churchmanagement/repository/DashboardRepository.java src/test/java/com/churchmanagement/service/HomeBadgeServiceTest.java src/test/java/com/churchmanagement/repository/SmsLogRepositoryTest.java
git commit -m "feat(navigation): counts for the Home tile badges" -m "HomeBadgeService returns 'N pending' for Collections (branches with no active receipt in the current receipt week, using the configured week identifier day) and 'N failed' for Messaging (SMS failed in the last 7 days that were not resent, matching the resend window). Zero counts show no badge and a failing count is left out without affecting the other." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Unsaved-changes hooks

**Files:**
- Create: `src/main/java/com/churchmanagement/navigation/UnsavedChangesAware.java`
- Modify: `src/main/java/com/churchmanagement/controller/ReceiptEntryController.java` (class declaration line 59, new method)
- Modify: `src/main/java/com/churchmanagement/controller/SendMessageController.java` (class declaration, new method)

**Interfaces:**
- Produces: `interface UnsavedChangesAware { boolean hasUnsavedChanges(); }`, implemented by both controllers. Task 7 calls it.

No unit test: both are FXML controllers whose fields are injected by `FXMLLoader`. Verified by compile here and the manual guard check in Task 7.

- [ ] **Step 1: Implement**

```java
package com.churchmanagement.navigation;

/** A screen that can hold input the user would lose by navigating away. */
public interface UnsavedChangesAware {
    boolean hasUnsavedChanges();
}
```

`ReceiptEntryController`: `public class ReceiptEntryController implements UnsavedChangesAware {` (import `com.churchmanagement.navigation.UnsavedChangesAware`) and add:

```java
    /** True while a receipt is being entered. The form is cleared after every save and by Clear. */
    @Override
    public boolean hasUnsavedChanges() {
        return churchComboBox.getValue() != null
                || hasText(submittedByNameField)
                || hasText(offertoryAmountField)
                || hasText(tithesAmountField)
                || hasText(otherDonationsAmountField);
    }

    private static boolean hasText(TextField field) {
        return field.getText() != null && !field.getText().isBlank();
    }
```

`SendMessageController`: `public class SendMessageController implements UnsavedChangesAware {` and add:

```java
    @Override
    public boolean hasUnsavedChanges() {
        return messageTextArea.getText() != null && !messageTextArea.getText().isBlank();
    }
```

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: succeeds.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/churchmanagement/navigation/UnsavedChangesAware.java src/main/java/com/churchmanagement/controller/ReceiptEntryController.java src/main/java/com/churchmanagement/controller/SendMessageController.java
git commit -m "feat(navigation): let screens report unsaved input" -m "UnsavedChangesAware lets the frame ask a screen before navigating away. Receipt Entry reports unsaved input while a branch is chosen or the submitter or any amount is filled (the form is cleared after every save), and Send Message while the message box has text." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Frame — module sidebar, switcher, breadcrumb, shortcuts, start screen

**Files:**
- Modify: `src/main/resources/com/churchmanagement/view/dashboard-view.fxml` (sidebar lines ~51–72, page title line ~79)
- Modify: `src/main/java/com/churchmanagement/controller/DashboardController.java`
- Modify: `src/main/resources/com/churchmanagement/view/app.css` (new rules after `.page-title`, ~line 1115, plus Orchid and Dark variants)

**Interfaces:**
- Consumes: `NavigationRegistry`, `NavigationAccess`, `NavItem`, `NavModule` (Tasks 2–3); `UnsavedChangesAware` (Task 6); `ReportsController.setReportGroup` (Task 1); `AuthenticatedUser.getLandingScreen()` (Task 4); `ActivityLogService.NAVIGATE_MODULE` (Task 2).
- Produces: private `openHome()`, `openModule(NavModule, boolean logModule)`, `boolean openItem(NavItem)`; public static `openReceiptCorrection(long)`, `openSendMessage()`, `refreshActiveHeader()` unchanged. `openHome()` loads `/com/churchmanagement/view/home-view.fxml` and calls `HomeController.configure(NavigationAccess, HomeNavigator, AuthenticatedUser)` — **created in Task 8**. To keep this task compilable on its own, Step 3 first adds a minimal `HomeController` / `home-view.fxml` / `HomeNavigator` (Task 8 replaces the controller and view bodies).

- [ ] **Step 1: Replace the sidebar and title in `dashboard-view.fxml`**

Replace everything inside `<VBox fx:id="sidebar" …>` after the `<padding>` block (the toggle button and the 17 menu buttons) with:

```xml
            <Button fx:id="sidebarToggleButton" maxWidth="Infinity" onAction="#toggleSidebar" styleClass="sidebar-toggle-button"/>
            <VBox fx:id="sidebarHeaderBox" spacing="4" styleClass="nav-sidebar-header"/>
            <VBox fx:id="sidebarItemsBox" spacing="4"/>
```

Replace `<Label fx:id="pageTitleLabel" styleClass="page-title"/>` with:

```xml
            <HBox fx:id="breadcrumbBar" alignment="CENTER_LEFT" spacing="8" styleClass="breadcrumb-bar"/>
```

- [ ] **Step 2: Minimal Home placeholder (replaced in Task 8)**

`src/main/java/com/churchmanagement/navigation/HomeNavigator.java`:

```java
package com.churchmanagement.navigation;

/** Lets the Home screen ask the main window to open a module. */
@FunctionalInterface
public interface HomeNavigator {
    void openModule(NavModule module);
}
```

`src/main/java/com/churchmanagement/controller/HomeController.java` (placeholder):

```java
package com.churchmanagement.controller;

import com.churchmanagement.navigation.HomeNavigator;
import com.churchmanagement.navigation.NavigationAccess;
import com.churchmanagement.security.AuthenticatedUser;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.TilePane;

public class HomeController {
    @FXML private TilePane tilePane;

    public void configure(NavigationAccess access, HomeNavigator navigator, AuthenticatedUser user) {
        tilePane.getChildren().setAll(access.visibleModules().stream().map(module -> {
            Button button = new Button(module.title());
            button.setOnAction(event -> navigator.openModule(module));
            return button;
        }).toList());
    }
}
```

`src/main/resources/com/churchmanagement/view/home-view.fxml` (placeholder):

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.scene.layout.TilePane?>

<TilePane fx:id="tilePane" hgap="16" vgap="16" xmlns="http://javafx.com/javafx/21"
          xmlns:fx="http://javafx.com/fxml/1" fx:controller="com.churchmanagement.controller.HomeController"/>
```

- [ ] **Step 3: Rewrite the navigation parts of `DashboardController`**

1. **Delete**: the 16 `@FXML private Button …Button;` fields from `dashboardButton` to `settingsButton`; the field `private List<MenuDefinition> menuDefinitions;`; the `@FXML private Label pageTitleLabel;` field; the 16 `show…()` handlers from `showDashboard()` to `showSettings()`; `createMenuDefinitions()`, `applyMenuVisibility()`, `applyMenuIcons()`, `setMenuIcon(...)`, `findMenu(...)`, `loadMenu(...)`, `highlightMenu(...)`, `logNavigation(MenuDefinition)`; the `MenuDefinition` record. Keep `createMenuIcon(String)`, `configureSidebarToggle()` and everything unrelated to navigation (header, monitors, window controls, force password change, profile image).

2. **Add fields** (next to the other fields):

```java
    private static final String HOME_VIEW = "/com/churchmanagement/view/home-view.fxml";
    private static final String MY_PROFILE_VIEW = "/com/churchmanagement/view/user-profile-view.fxml";
    private static final KeyCode[] SHORTCUT_DIGITS = {
            KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4, KeyCode.DIGIT5, KeyCode.DIGIT6,
            KeyCode.DIGIT7};

    private NavigationAccess navigationAccess;
    private NavModule currentModule;
    private NavItem currentItem;
    private String standaloneTitle;
    private boolean onHome;
    private Object currentContentController;
    private final Map<String, String> lastItemByModule = new HashMap<>();

    @FXML
    private VBox sidebarHeaderBox;

    @FXML
    private VBox sidebarItemsBox;

    @FXML
    private HBox breadcrumbBar;
```

3. **In `initialize()`**, replace the block

```java
        menuDefinitions = createMenuDefinitions();
        applyMenuIcons();
        configureSidebarToggle();
        applyMenuVisibility();
        SmsQueueProcessor.getInstance().start();
        loadMenu(menuDefinitions.getFirst());
```

with:

```java
        navigationAccess = new NavigationAccess(currentUser, PrimaryMachine.isPrimary());
        configureSidebarToggle();
        SmsQueueProcessor.getInstance().start();
        Platform.runLater(this::installShortcuts);
        NavigationAccess.Landing landing = navigationAccess.landing(currentUser.getLandingScreen());
        if (landing.item() != null && openItem(landing.item()) && landing.fellBack()) {
            setStatus("Your saved start screen is no longer available — opened " + landing.item().title() + ".",
                    "status-warning");
        }
```

4. **Replace `toggleSidebar()`'s loop** over `menuDefinitions` (the rest of the method stays) with the following, placed after the width changes (navigation is not set up while a password change is pending):

```java
        if (navigationAccess != null) {
            renderSidebar();
        }
```

5. **Replace `showMyProfile()`** with:

```java
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
```

6. **At the start of `handleLogout()`** add:

```java
        if (!confirmLeave()) {
            return;
        }
```

7. **Replace the bodies** of the two static helpers:

```java
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
```

8. **Add the navigation methods** (in place of the deleted `loadMenu` group):

```java
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
```

9. **Imports** to add: `com.churchmanagement.navigation.NavItem`, `com.churchmanagement.navigation.NavModule`, `com.churchmanagement.navigation.NavigationAccess`, `com.churchmanagement.navigation.NavigationRegistry`, `com.churchmanagement.navigation.UnsavedChangesAware`, `javafx.geometry.Side`, `javafx.scene.control.ButtonType`, `javafx.scene.control.ContextMenu`, `javafx.scene.control.MenuItem`, `javafx.scene.input.KeyCode`, `javafx.scene.input.KeyCodeCombination`, `javafx.scene.input.KeyCombination`, `java.util.ArrayList`, `java.util.HashMap`, `java.util.Map`. Remove imports that become unused.

- [ ] **Step 4: Add the frame styles to `app.css`**

After the `.page-title` rule:

```css
/* ── Module navigation: sidebar header and breadcrumb ── */
.nav-sidebar-header {
    -fx-padding: 0 0 8 0;
    -fx-border-color: transparent transparent rgba(253, 230, 138, 0.35) transparent;
    -fx-border-width: 0 0 1 0;
}

.nav-module-button {
    -fx-font-weight: bold;
}

.nav-section-label {
    -fx-text-fill: #fcd34d;
    -fx-font-size: 11px;
    -fx-font-weight: bold;
    -fx-padding: 10 14 2 14;
}

.breadcrumb-link {
    -fx-background-color: transparent;
    -fx-text-fill: #92400e;
    -fx-font-size: 18px;
    -fx-padding: 0;
    -fx-cursor: hand;
    -fx-underline: true;
}

.breadcrumb-link:focused {
    -fx-border-color: #f59e0b;
    -fx-border-width: 0 0 2 0;
}

.breadcrumb-separator {
    -fx-text-fill: #b45309;
    -fx-font-size: 18px;
}
```

After the `.theme-orchid .active-menu-button .menu-icon` rule:

```css
.theme-orchid .nav-sidebar-header {
    -fx-border-color: transparent transparent rgba(243, 205, 244, 0.35) transparent;
}

.theme-orchid .nav-section-label {
    -fx-text-fill: #f3cdf4;
}

.theme-orchid .breadcrumb-link {
    -fx-text-fill: #8c23a0;
}

.theme-orchid .breadcrumb-separator {
    -fx-text-fill: #bb29c1;
}
```

After the `.theme-dark .active-menu-button` rule:

```css
.theme-dark .nav-sidebar-header {
    -fx-border-color: transparent transparent #4b5563 transparent;
}

.theme-dark .nav-section-label {
    -fx-text-fill: #d1d5db;
}

.theme-dark .breadcrumb-link {
    -fx-text-fill: #fbbf24;
}

.theme-dark .breadcrumb-separator {
    -fx-text-fill: #9ca3af;
}
```

- [ ] **Step 5: Compile and run the tests**

Run: `mvn -q compile` then `mvn -q test -Dtest=NavigationAccessTest,NavigationRegistryTest`
Expected: compile succeeds; tests PASS.

- [ ] **Step 6: Manual check**

Start the app from the IDE as Admin with the dev database reachable:
1. Login opens Dashboard › Overview; the sidebar shows Home, "Dashboard", Overview and Submission Status; the breadcrumb reads `Home › Dashboard › Overview`.
2. Home opens the (placeholder) Home; each button opens its module; the sidebar shows only that module.
3. The module name in the sidebar opens the switcher; picking Messaging opens Send Message.
4. Ctrl+H opens Home; Ctrl+2 opens Collections; Ctrl+5 does nothing.
5. Collections › Collection Reports shows 13 report cards; Messaging › SMS Reports shows 1; Administration › Admin Reports shows 2.
6. In Collections › Receipt pick a branch, then click Home → "You have unsaved changes. Leave this screen?"; Cancel stays, OK leaves.
7. Open Receipt History, then Collections again from Home → returns to Receipt History (remembered).
8. Collapse the sidebar → icons only, titles as tooltips.
9. Receipt History → cancelled receipt → "Re-create" still opens Receipt Entry with the correction; Settings → SMS → Open Send Message still works.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/com/churchmanagement/view/dashboard-view.fxml src/main/java/com/churchmanagement/controller/DashboardController.java src/main/resources/com/churchmanagement/view/app.css src/main/java/com/churchmanagement/navigation/HomeNavigator.java src/main/java/com/churchmanagement/controller/HomeController.java src/main/resources/com/churchmanagement/view/home-view.fxml
git commit -m "feat(navigation): module sidebar, switcher, breadcrumb and shortcuts" -m "The main window now builds its sidebar from the navigation registry instead of 16 hard-coded buttons: a Home button, the current module (click to switch module) and only that module's screens, collapsed to icons with tooltips. The page title becomes a clickable breadcrumb (Home > Module > Screen). Ctrl+H opens Home and Ctrl+1..7 open modules by their fixed numbers. Each module reopens the screen last used in it, report screens open with their module's reports only, and leaving Receipt Entry or Send Message with unsaved input asks first, including on logout." -m "After login the user's start screen opens, with a status-bar note when a saved choice is no longer available. Home is a minimal placeholder until the tile grid lands." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Home screen tiles

**Files:**
- Modify (replace): `src/main/resources/com/churchmanagement/view/home-view.fxml`
- Modify (replace): `src/main/java/com/churchmanagement/controller/HomeController.java`
- Modify: `src/main/resources/com/churchmanagement/view/app.css` (tile, badge, info button, popover rules + Orchid / Dark)

**Interfaces:**
- Consumes: `NavigationAccess`, `NavModule`, `NavItem`, `HomeNavigator` (Tasks 3, 7); `HomeBadgeService.badgeTexts()` (Task 5); existing `ThemeService.applyConfiguredTheme(Parent)`, `SystemConfigurationCache.getString("system.date.format")`; existing CSS classes `report-card`, `report-card-icon`, `report-card-title`, `report-card-action` (already themed for Light, Orchid, Dark).
- Produces: `public void HomeController.configure(NavigationAccess access, HomeNavigator navigator, AuthenticatedUser user)` (same signature as the Task 7 placeholder).

- [ ] **Step 1: Replace `home-view.fxml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>

<?import javafx.geometry.Insets?>
<?import javafx.scene.control.Label?>
<?import javafx.scene.control.ScrollPane?>
<?import javafx.scene.layout.HBox?>
<?import javafx.scene.layout.Region?>
<?import javafx.scene.layout.TilePane?>
<?import javafx.scene.layout.VBox?>

<VBox spacing="18" styleClass="module-page"
      xmlns="http://javafx.com/javafx/21"
      xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="com.churchmanagement.controller.HomeController">
    <padding>
        <Insets bottom="22" left="22" right="22" top="22"/>
    </padding>
    <HBox alignment="CENTER_LEFT">
        <Label fx:id="greetingLabel" styleClass="report-card-title"/>
        <Region HBox.hgrow="ALWAYS"/>
        <Label fx:id="dateLabel" styleClass="report-card-action"/>
    </HBox>
    <ScrollPane fitToWidth="true" styleClass="report-card-scroll" VBox.vgrow="ALWAYS">
        <TilePane fx:id="tilePane" hgap="16" prefTileHeight="140" prefTileWidth="220" styleClass="report-card-grid"
                  vgap="16"/>
    </ScrollPane>
</VBox>
```

- [ ] **Step 2: Replace `HomeController`**

```java
package com.churchmanagement.controller;

import com.churchmanagement.navigation.HomeNavigator;
import com.churchmanagement.navigation.NavItem;
import com.churchmanagement.navigation.NavModule;
import com.churchmanagement.navigation.NavigationAccess;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.service.HomeBadgeService;
import com.churchmanagement.service.SystemConfigurationCache;
import com.churchmanagement.util.ThemeService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/** Home: one tile per module the user can open, with an (i) popover listing its screens. */
public class HomeController {
    private static final String STYLESHEET = "/com/churchmanagement/view/app.css";

    @FXML private Label greetingLabel;
    @FXML private Label dateLabel;
    @FXML private TilePane tilePane;

    private final Map<String, Label> badgeLabels = new HashMap<>();
    private NavigationAccess access;
    private HomeNavigator navigator;
    private Popup openPopover;

    public void configure(NavigationAccess access, HomeNavigator navigator, AuthenticatedUser user) {
        this.access = access;
        this.navigator = navigator;
        greetingLabel.setText(greeting() + ", " + user.getFullName());
        dateLabel.setText(LocalDate.now().format(dateFormatter()));
        tilePane.getChildren().setAll(access.visibleModules().stream().map(this::tile).toList());
        loadBadges();
    }

    private Node tile(NavModule module) {
        FontIcon icon = new FontIcon(module.icon());
        icon.getStyleClass().add("report-card-icon");

        Button infoButton = new Button();
        infoButton.setGraphic(new FontIcon("fas-info-circle"));
        infoButton.getStyleClass().add("nav-tile-info-button");
        infoButton.setAccessibleText("What's in " + module.title());
        infoButton.setTooltip(new Tooltip("What's in " + module.title()));
        infoButton.setOnAction(event -> {
            event.consume();
            showPopover(module, infoButton);
        });

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox top = new HBox(icon, topSpacer, infoButton);
        top.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(module.title());
        title.getStyleClass().add("report-card-title");
        Label description = new Label(module.description());
        description.setWrapText(true);
        description.getStyleClass().add("report-card-action");

        Label badge = new Label();
        badge.getStyleClass().add("nav-tile-badge");
        badge.setVisible(false);
        badge.setManaged(false);
        badgeLabels.put(module.id(), badge);

        Region bottomSpacer = new Region();
        VBox.setVgrow(bottomSpacer, Priority.ALWAYS);
        VBox content = new VBox(6, top, title, description, bottomSpacer, badge);

        StackPane tile = new StackPane(content);
        tile.getStyleClass().addAll("report-card", "nav-tile");
        tile.setFocusTraversable(true);
        tile.setAccessibleText(module.title() + ". " + module.description());
        Tooltip.install(tile, new Tooltip(module.title() + " (Ctrl+" + module.shortcutNumber() + ")"));
        tile.setOnMouseClicked(event -> navigator.openModule(module));
        tile.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                event.consume();
                navigator.openModule(module);
            }
        });
        return tile;
    }

    private void showPopover(NavModule module, Node anchor) {
        if (openPopover != null) {
            openPopover.hide();
        }

        FontIcon icon = new FontIcon(module.icon());
        icon.getStyleClass().add("report-card-icon");
        Label title = new Label(module.title());
        title.getStyleClass().add("report-card-title");
        HBox header = new HBox(8, icon, title);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, header);
        boolean sections = access.useSections(module);
        String lastSection = null;
        for (NavItem item : access.visibleItems(module)) {
            if (sections && item.section() != null && !item.section().equals(lastSection)) {
                Label sectionLabel = new Label(item.section());
                sectionLabel.getStyleClass().add("nav-popover-section");
                box.getChildren().add(sectionLabel);
                lastSection = item.section();
            }
            FontIcon itemIcon = new FontIcon(item.icon());
            itemIcon.getStyleClass().add("report-card-icon");
            Label itemTitle = new Label(item.title());
            itemTitle.getStyleClass().add("nav-popover-item-title");
            Label itemDescription = new Label(item.description());
            itemDescription.setWrapText(true);
            itemDescription.getStyleClass().add("report-card-action");
            HBox row = new HBox(10, itemIcon, new VBox(2, itemTitle, itemDescription));
            row.setAlignment(Pos.TOP_LEFT);
            box.getChildren().add(row);
        }

        Popup popover = new Popup();
        Button openButton = new Button("Open module");
        openButton.getStyleClass().add("primary-button");
        openButton.setOnAction(event -> {
            popover.hide();
            navigator.openModule(module);
        });
        HBox actions = new HBox(openButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        box.getChildren().add(actions);

        box.getStyleClass().addAll("report-card", "nav-popover");
        box.setPrefWidth(330);
        box.getStylesheets().add(getClass().getResource(STYLESHEET).toExternalForm());
        new ThemeService().applyConfiguredTheme(box);

        popover.getContent().add(box);
        popover.setAutoHide(true);
        popover.setHideOnEscape(true);
        popover.setOnHidden(event -> anchor.requestFocus());
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popover.show(anchor, bounds.getMinX(), bounds.getMaxY() + 4);
        openPopover = popover;
        Platform.runLater(openButton::requestFocus);
    }

    private void loadBadges() {
        Thread thread = new Thread(() -> {
            Map<String, String> badges = new HomeBadgeService().badgeTexts();
            Platform.runLater(() -> badges.forEach((moduleId, text) -> {
                Label badge = badgeLabels.get(moduleId);
                if (badge != null) {
                    badge.setText(text);
                    badge.setVisible(true);
                    badge.setManaged(true);
                }
            }));
        }, "home-badges");
        thread.setDaemon(true);
        thread.start();
    }

    private static String greeting() {
        int hour = LocalTime.now().getHour();
        return hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
    }

    private static DateTimeFormatter dateFormatter() {
        String pattern = SystemConfigurationCache.getInstance().getString("system.date.format");
        try {
            return DateTimeFormatter.ofPattern(pattern == null || pattern.isBlank() ? "yyyy-MMM-dd" : pattern);
        } catch (IllegalArgumentException exception) {
            return DateTimeFormatter.ofPattern("yyyy-MMM-dd");
        }
    }
}
```

- [ ] **Step 3: Add the tile styles to `app.css`**

After the `.report-card-action` rule (~line 2017):

```css
/* ── Home tiles ── */
.nav-tile {
    -fx-pref-width: 220;
    -fx-pref-height: 140;
    -fx-min-height: 140;
}

.nav-tile:focused {
    -fx-border-color: #b45309;
    -fx-border-width: 2.4;
}

.nav-tile-info-button {
    -fx-background-color: transparent;
    -fx-padding: 4;
    -fx-min-width: 30;
    -fx-min-height: 30;
    -fx-cursor: hand;
}

.nav-tile-info-button .ikonli-font-icon {
    -fx-icon-color: #b45309;
    -fx-icon-size: 16px;
}

.nav-tile-info-button:focused {
    -fx-border-color: #b45309;
    -fx-border-radius: 999;
}

.nav-tile-badge {
    -fx-background-color: #fef3c7;
    -fx-background-radius: 999;
    -fx-text-fill: #78350f;
    -fx-font-size: 12px;
    -fx-font-weight: bold;
    -fx-padding: 2 10;
}

.nav-popover {
    -fx-cursor: default;
    -fx-min-height: 0;
}

.nav-popover-item-title {
    -fx-font-weight: bold;
    -fx-text-fill: #451a03;
}

.nav-popover-section {
    -fx-font-size: 11px;
    -fx-font-weight: bold;
    -fx-text-fill: #92400e;
}
```

After the `.theme-orchid .report-card-action` rule:

```css
.theme-orchid .nav-tile:focused {
    -fx-border-color: #8c23a0;
}

.theme-orchid .nav-tile-info-button .ikonli-font-icon {
    -fx-icon-color: #8c23a0;
}

.theme-orchid .nav-tile-info-button:focused {
    -fx-border-color: #8c23a0;
}

.theme-orchid .nav-tile-badge {
    -fx-background-color: #fbeefb;
    -fx-text-fill: #6e1871;
}

.theme-orchid .nav-popover-item-title {
    -fx-text-fill: #3d0e3f;
}

.theme-orchid .nav-popover-section {
    -fx-text-fill: #6e1871;
}
```

After the `.theme-dark .report-card-action` rule:

```css
.theme-dark .nav-tile:focused {
    -fx-border-color: #fbbf24;
}

.theme-dark .nav-tile-info-button .ikonli-font-icon {
    -fx-icon-color: #fbbf24;
}

.theme-dark .nav-tile-info-button:focused {
    -fx-border-color: #fbbf24;
}

.theme-dark .nav-tile-badge {
    -fx-background-color: #374151;
    -fx-text-fill: #fde68a;
}

.theme-dark .nav-popover-item-title {
    -fx-text-fill: #f9fafb;
}

.theme-dark .nav-popover-section {
    -fx-text-fill: #d1d5db;
}
```

- [ ] **Step 4: Compile**

Run: `mvn -q compile`
Expected: succeeds.

- [ ] **Step 5: Manual check**

As Admin, then as a data-entry user (only `receipt.entry.menu.view`), in each theme (Settings → System → Theme):
1. Home shows Dashboard, Collections, Branches, Messaging, Administration tiles in that order, about 220 × 140, wrapping at 1366 px; greeting and date at the top.
2. Collections shows "N pending" and Messaging "N failed" when there are any; nothing when zero.
3. (i) opens the popover listing only permitted screens with descriptions, without opening the module; Esc and an outside click close it; Open module opens the module; opening a second (i) closes the first.
4. Tab moves between tiles and (i) buttons with a visible focus ring; Enter/Space opens.
5. The data-entry user has no Home button, no switcher and lands on their screen; a user with two modules sees exactly two tiles.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/com/churchmanagement/view/home-view.fxml src/main/java/com/churchmanagement/controller/HomeController.java src/main/resources/com/churchmanagement/view/app.css
git commit -m "feat(navigation): Home screen with module tiles" -m "Home shows a greeting and one tile per module the user can open, in fixed order: icon, title, a short description and, for Collections and Messaging, a pending / failed badge loaded in the background. The whole tile opens the module by click, Enter or Space. An (i) button opens a popover listing the module's screens with one-line descriptions and an Open module button; it closes with Esc or an outside click. Tiles reuse the themed report-card styles, with focus rings and badge colours added for Light, Orchid and Dark." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: "Open after login" in My Profile and User Management

**Files:**
- Modify: `src/main/resources/com/churchmanagement/view/user-profile-view.fxml` (profile `GridPane`, after the Mobile Number row 5)
- Modify: `src/main/java/com/churchmanagement/controller/UserProfileController.java` (fields, profile load ~line 114, `handleSaveProfile` ~line 71)
- Modify: `src/main/java/com/churchmanagement/controller/UserManagementController.java` (`showUserDialog` ~249, `userDialogContent` ~297, `UserDialogFields` ~410)

**Interfaces:**
- Consumes: `LandingScreenService.optionsFor`, `optionsForRole`, `findForUser`, `saveOwn`, `saveForUser`; `LandingOption` (Task 4).

- [ ] **Step 1: My Profile**

In the profile `GridPane` of `user-profile-view.fxml`, after the Mobile Number row:

```xml
                    <Label styleClass="field-label" text="Open after login" GridPane.columnIndex="0" GridPane.rowIndex="6"/>
                    <ComboBox fx:id="landingScreenComboBox" maxWidth="Infinity" GridPane.columnIndex="1" GridPane.rowIndex="6"/>
```

(add `<?import javafx.scene.control.ComboBox?>` if missing).

`UserProfileController`:

```java
    @FXML private ComboBox<LandingOption> landingScreenComboBox;
    private final LandingScreenService landingScreenService = new LandingScreenService();
```

Where the profile is loaded into the fields (after `profile = userProfileService.loadOwnProfile();` and the field assignments), add:

```java
        AuthContext.getCurrentUser().ifPresent(user -> {
            List<LandingOption> options = landingScreenService.optionsFor(user);
            landingScreenComboBox.getItems().setAll(options);
            landingScreenComboBox.setValue(options.stream()
                    .filter(option -> Objects.equals(option.itemId(), user.getLandingScreen()))
                    .findFirst()
                    .orElse(LandingOption.DEFAULT));
        });
```

In `handleSaveProfile()`, right after the `userProfileService.updateOwnProfile(...)` call succeeds (before `DashboardController.refreshActiveHeader();`):

```java
            LandingOption landing = landingScreenComboBox.getValue();
            landingScreenService.saveOwn(landing == null ? null : landing.itemId());
```

If `handleSaveProfile` catches specific exceptions, also catch `LandingScreenService.LandingScreenException` the same way (show its message). Imports: `com.churchmanagement.navigation.LandingOption`, `com.churchmanagement.service.LandingScreenService`, `com.churchmanagement.security.AuthContext` (if missing), `javafx.scene.control.ComboBox`, `java.util.List`, `java.util.Objects`.

- [ ] **Step 2: User Management (edit dialog only)**

Add a field `private final LandingScreenService landingScreenService = new LandingScreenService();`.

`UserDialogFields` gets a last component `ComboBox<LandingOption> landingScreenComboBox` (null when creating).

In `userDialogContent(UserDto user)`, inside `if (editing) { … }` build the combo, and add it to the grid after the force-password checkbox:

```java
        ComboBox<LandingOption> landingScreenComboBox = null;
        if (editing) {
            ComboBox<LandingOption> combo = new ComboBox<>();
            combo.setMaxWidth(Double.MAX_VALUE);
            String saved = landingScreenService.findForUser(user.getId()).orElse(null);
            Runnable refreshOptions = () -> {
                UserManagementRepository.RoleOption role = roleComboBox.getValue();
                List<LandingOption> options = role == null
                        ? List.of(LandingOption.DEFAULT)
                        : landingScreenService.optionsForRole(role.id(), role.name());
                String keep = combo.getValue() == null ? saved : combo.getValue().itemId();
                combo.getItems().setAll(options);
                combo.setValue(options.stream()
                        .filter(option -> Objects.equals(option.itemId(), keep))
                        .findFirst()
                        .orElse(LandingOption.DEFAULT));
            };
            refreshOptions.run();
            roleComboBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshOptions.run());
            landingScreenComboBox = combo;
        }
```

Place this block **after** the existing `if (editing) { … } else { … }` field setup (so the role is already selected), and in the grid's editing branch add:

```java
            grid.add(DialogStyler.fieldLabel("Open after login"), 0, 5);
            grid.add(landingScreenComboBox, 1, 5);
```

Pass `landingScreenComboBox` as the new last argument of `new UserDialogFields(...)`.

In `showUserDialog`, inside the `editing` branch of the processing lambda, after `userManagementService.update(...)`:

```java
                            LandingOption landing = fields.landingScreenComboBox().getValue();
                            landingScreenService.saveForUser(user.getId(), landing == null ? null : landing.itemId());
```

Imports: `com.churchmanagement.navigation.LandingOption`, `com.churchmanagement.service.LandingScreenService`, `java.util.List`, `java.util.Objects` (if missing).

- [ ] **Step 3: Compile and test**

Run: `mvn -q compile` then `mvn -q test -Dtest=LandingScreenServiceTest,UserManagementServiceTest,UserProfileServiceTest`
Expected: compile succeeds; tests PASS.

- [ ] **Step 4: Manual check**

1. My Profile → Open after login lists "Dashboard (default)" and only screens you can open → choose Collections › Receipt → Save → log out and in → Receipt opens.
2. Users → edit a user → Open after login shows that role's screens; change the role → the list follows; save; that user logs in on the chosen screen.
3. Remove that role's permission for the chosen screen (Roles & Permissions) → the user logs in on Overview with the status-bar note.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/com/churchmanagement/view/user-profile-view.fxml src/main/java/com/churchmanagement/controller/UserProfileController.java src/main/java/com/churchmanagement/controller/UserManagementController.java
git commit -m "feat(users): choose the screen that opens after login" -m "My Profile and the User Management edit dialog get an 'Open after login' choice listing Dashboard (default) and only the screens that user (or the selected role) can open, labelled Module > Screen. The edit dialog's list follows a role change. Saved through LandingScreenService with the rest of the form." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: User manuals (EN + SI)

**Files:**
- Modify: `docs/user-manual.md` (§3.3 sidebar bullets ~line 161, §3.4 Navigation ~line 169, every "Click **X** in the sidebar" line, §14.7 intro)
- Modify: `docs/user-manual-si.md` (matching lines)

- [ ] **Step 1: English**

Replace the §3.3 "Sidebar (Left Panel)" bullets with:

```markdown
**Sidebar (Left Panel):**
- **Home** (top) returns to the module tiles
- The current module's name — click it to switch to another module
- Only the current module's screens, shown/hidden based on your permissions
```

Replace §3.4 with:

```markdown
### 3.4 Getting Around

The system is organised into **modules**: Dashboard, Collections, Branches, Messaging and Administration.

- **Home** shows one tile per module you can use. Each tile has a short description and may show a badge — for example *12 pending* on Collections (branches that have not submitted this week) or *3 failed* on Messaging (SMS that failed in the last 7 days). Click a tile, or select it with **Tab** and press **Enter**, to open the module.
- The **(i)** button on a tile lists the module's screens with a one-line explanation of each, without opening it. Press **Esc** to close the list, or **Open module** to go in.
- Inside a module the sidebar shows only that module's screens. Click the module name at the top of the sidebar to jump straight to another module.
- The title line shows where you are, e.g. **Home › Collections › Receipt History**. Click any part to go back to it.
- Re-opening a module returns to the screen you last used in it.
- **Keyboard shortcuts:** **Ctrl+H** Home · **Ctrl+1** Dashboard · **Ctrl+2** Collections · **Ctrl+3** Branches · **Ctrl+4** Messaging · **Ctrl+7** Administration.
- **Start screen:** after login you go straight to your own start screen. Choose it in **My Profile → Open after login** (an administrator can also set it in **Users**). If you lose access to that screen, the system opens Dashboard and tells you in the status bar.
- If you leave **Receipt** or **Send Message** with unsaved input, the system asks before discarding it.
- The sidebar can be collapsed with the **«** button; hover over an icon to see its name.
```

Replace each "Click **X** in the sidebar." with the module path:

| Line text now | Replace with |
|---------------|--------------|
| Click **Regions** in the sidebar. | Open **Branches › Regions**. |
| Click **Churches** in the sidebar. | Open **Branches › Churches**. |
| Click **Receipt Entry** in the sidebar. | Open **Collections › Receipt**. |
| Click **Receipt History** in the sidebar. | Open **Collections › Receipt History**. |
| Click **Submission Status** in the sidebar. | Open **Dashboard › Submission Status**. |
| Click **Reports** in the sidebar. | Open **Collections › Collection Reports** (SMS Delivery is under **Messaging › SMS Reports**; User Activity and Backup & Restore History are under **Administration › Admin Reports**). |
| Click **User Management** in the sidebar. | Open **Administration › Users**. |
| Click **Roles & Permissions** in the sidebar. | Open **Administration › Roles & Permissions**. |
| Click **Backup & Restore** in the sidebar. | Open **Administration › Backup & Restore**. |
| Click **Activity Logs** in the sidebar. | Open **Administration › Activity Logs**. |
| Click **SMS Logs** in the sidebar. | Open **Messaging › SMS Logs**. |
| Click **Settings** in the sidebar. | Open **Administration › Settings**. |
| Click **My Profile** in the sidebar. | Click **My Profile** in the header. |

and in §14.7 replace "(sidebar, under SMS Logs)" with "(**Messaging › Send Message**)".

In §16 My Profile add a bullet: "**Open after login** — the screen that opens when you sign in (see 3.4)."

- [ ] **Step 2: Sinhala**

Run `grep -n -i "sidebar\|පැති මෙනුව" docs/user-manual-si.md` and make the same changes line by line: the §3.3 sidebar bullets, §3.4 (title **3.4 සංචාලනය**), every "open from the sidebar" line using the same module paths (keep screen names in English, as the rest of the SI manual does), §14.7's "(පැති මෙනුවේ, SMS Logs යටතේ)" → "(**Messaging › Send Message**)", and the My Profile bullet. Sinhala text for §3.4:

```markdown
### 3.4 සංචාලනය

පද්ධතිය **මොඩියුල** ලෙස සකසා ඇත: Dashboard, Collections, Branches, Messaging සහ Administration.

- **Home** තිරයේ ඔබට භාවිත කළ හැකි සෑම මොඩියුලයකටම ටයිල් එකක් ඇත. සෑම ටයිල් එකකම කෙටි විස්තරයක් ඇති අතර ලාංඡනයක් (badge) පෙන්විය හැක — උදා. Collections හි *12 pending* (මෙම සතියේ ඉදිරිපත් නොකළ ශාඛා) හෝ Messaging හි *3 failed* (පසුගිය දින 7 තුළ අසාර්ථක වූ SMS). මොඩියුලය විවෘත කිරීමට ටයිල් එක ක්ලික් කරන්න, නැතහොත් **Tab** මගින් තෝරා **Enter** ඔබන්න.
- ටයිල් එකේ **(i)** බොත්තම මොඩියුලය විවෘත නොකර එහි තිර සහ ඒ එක් එක් තිරයේ කෙටි පැහැදිලි කිරීමක් පෙන්වයි. වසා දැමීමට **Esc** ඔබන්න, ඇතුළු වීමට **Open module** ඔබන්න.
- මොඩියුලයක් තුළ පැති මෙනුවේ එම මොඩියුලයේ තිර පමණක් පෙන්වයි. වෙනත් මොඩියුලයකට කෙලින්ම යාමට පැති මෙනුවේ ඉහළ ඇති මොඩියුල නම ක්ලික් කරන්න.
- මාතෘකා පේළිය ඔබ සිටින තැන පෙන්වයි, උදා. **Home › Collections › Receipt History**. ආපසු යාමට ඕනෑම කොටසක් ක්ලික් කරන්න.
- මොඩියුලයක් නැවත විවෘත කළ විට ඔබ අවසන් වරට භාවිත කළ තිරය විවෘත වේ.
- **යතුරුපුවරු කෙටිමං:** **Ctrl+H** Home · **Ctrl+1** Dashboard · **Ctrl+2** Collections · **Ctrl+3** Branches · **Ctrl+4** Messaging · **Ctrl+7** Administration.
- **ආරම්භක තිරය:** පිවිසීමෙන් පසු ඔබ ඔබේම ආරම්භක තිරයට කෙලින්ම යයි. එය **My Profile → Open after login** හි තෝරන්න (පරිපාලකයෙකුට **Users** හිද එය සැකසිය හැක). එම තිරයට ප්‍රවේශය අහිමි වුවහොත්, පද්ධතිය Dashboard විවෘත කර තත්ත්ව තීරුවේ දන්වයි.
- **Receipt** හෝ **Send Message** හි සුරැකිය යුතු දත්ත තිබියදී ඉවත් වන විට, පද්ධතිය පළමුව ඔබෙන් අසයි.
- **«** බොත්තමෙන් පැති මෙනුව හැකිළිය හැක; නම බැලීමට අයිකනයක් මත මූසිකය තබන්න.
```

- [ ] **Step 3: Commit**

```bash
git add docs/user-manual.md docs/user-manual-si.md
git commit -m "docs(manual): getting around with modules (EN + SI)" -m "Replaces the Navigation section with Getting Around: Home tiles and badges, the (i) list, the module sidebar and switcher, the breadcrumb, remembered screens, keyboard shortcuts, the start screen and the unsaved-input prompt. Every 'click X in the sidebar' instruction now names its Module > Screen path, including where each report group now lives." -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Final verification

- [ ] **Step 1:** Check the database host is reachable (`Test-NetConnection 192.168.1.101 -Port 3308`), then run `mvn -q test`. Expected: all tests pass (419 before this feature + the new ones).
- [ ] **Step 2:** Start the app on the primary machine against the dev database; confirm Flyway applies V42 and `SHOW COLUMNS FROM users` lists `landing_screen`.
- [ ] **Step 3:** Run the manual checks from Tasks 7, 8 and 9 for Admin, a data-entry user and a viewer, on both machines (Backup & Restore absent on the secondary), at 1366 and 1920 px wide, keyboard only.
- [ ] **Step 4:** Report results and anything not checked. Integration is decided with the user via superpowers:finishing-a-development-branch (keep the feature branch after merging).
