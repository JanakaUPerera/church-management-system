# Module-Based Navigation — Design

**Requirement:** Phase 02 / 05c (Milestone M1)
**Date:** 2026-10-20
**Status:** Approved in brainstorming, pending spec review
**Related:** [PHASE-02-DEVELOPMENT-PLAN.md](../../PHASE-02-DEVELOPMENT-PLAN.md) §05c (product requirements, Q16, Q17, UX standards)

---

## 1. Goal

Replace the flat 16-button sidebar with hub-and-spoke navigation: a **Home** screen of module tiles, and a sidebar that shows only the current module's screens. Every screen is defined once in a **navigation registry**, so adding an M2/M3 screen is one registry entry. Users go straight to their own **start screen** after login.

PHASE-02-DEVELOPMENT-PLAN §05c is the product requirement. This spec records how it is built and the decisions taken where §05c left room.

## 2. Decisions

| # | Decision |
|---|----------|
| D1 | The current Reports screen is **shared by three modules**, each showing only its own report types: Collections → *Collection Reports* (13 types), Messaging → *SMS Reports* (SMS Delivery), Administration → *Admin Reports* (User Activity, Backup & Restore History). All three use `report.menu.view`. |
| D2 | Module shortcut numbers are **fixed per module**, not per visible tile: Dashboard 1, Collections 2, Branches 3, Messaging 4, Finance 5, Members 6, Administration 7. Ctrl+5 / Ctrl+6 do nothing until M2 / M3 ship. |
| D3 | The registry lives in Java code (records), not FXML or the database. The 16 `@FXML` sidebar buttons and their `show…()` handlers are removed. |
| D4 | Visibility and landing rules live in a pure-Java `NavigationAccess` class with no JavaFX dependency, so they are unit tested. |
| D5 | The unsaved-changes guard is an interface, `UnsavedChangesAware`, implemented in this release by **Receipt Entry** and **Send Message** only. Other screens can opt in later. |
| D6 | Tile badges in this release: **Collections** — branches pending in the current receipt week; **Messaging** — SMS FAILED in the last 7 days with no newer resend. No other badges yet. |
| D7 | The start screen is stored as the registry item id in a new nullable `users.landing_screen` column (migration **V42**). Unset → Dashboard → Overview. |
| D8 | "Remember position" is in memory for the running session only (not persisted). |
| D9 | Only modules that exist now get tiles (Dashboard, Collections, Branches, Messaging, Administration). Finance and Members are added by M2 / M3 with their screens. |

## 3. Registry

Package `com.churchmanagement.navigation`.

```java
public record NavItem(String id, String title, String description, String viewPath, String icon,
                      String activityAction, String section, ReportGroup reportGroup,
                      List<String> permissionCodes) { }

public record NavModule(String id, String title, String icon, String description, int shortcutNumber,
                        List<NavItem> items) { }
```

`NavigationRegistry.modules()` returns the modules in tile order. `NavigationRegistry.findItem(String id)` looks an item up by id.

| Module (id, shortcut, icon) | Items (id → view, permission) |
|-----------------------------|-------------------------------|
| **Dashboard** (`dashboard`, 1, `fas-home`) — "Today's overview" | `dashboard.overview` → dashboard-home-view (none); `dashboard.submission-status` → submission-status-view (`receipt.menu.view`) |
| **Collections** (`collections`, 2, `fas-receipt`) — "Enter and track receipts" | `collections.receipt` → receipt-entry-view (`receipt.entry.menu.view`); `collections.receipt-history` → receipt-history-view (`receipt.menu.view`); `collections.reports` → reports-view, group COLLECTIONS (`report.menu.view`) |
| **Branches** (`branches`, 3, `fas-church`) — "Churches, regions and categories" | `branches.churches` → church-view (`church.menu.view`); `branches.regions` → region-view (`region.menu.view`); `branches.categories` → category-view (`category.menu.view`) |
| **Messaging** (`messaging`, 4, `fas-sms`) — "Send official SMS, view logs" | `messaging.send-message` → send-message-view (`sms.official.menu.view`); `messaging.sms-logs` → sms-log-view (`sms.menu.view`); `messaging.reports` → reports-view, group MESSAGING (`report.menu.view`) |
| **Administration** (`administration`, 7, `fas-cog`) — "Users, security, backups and settings" | `admin.users` → user-management-view (`user.menu.view`); `admin.roles` → role-permission-view (`role.menu.view`); `admin.activity-logs` → activity-log-view (`activity.menu.view`); `admin.backup` → backup-restore-view (`backup.menu.view`, primary machine only); `admin.reports` → reports-view, group ADMINISTRATION (`report.menu.view`); `admin.settings` → settings-view (`settings.menu.view`) |

Each item also has a one-line `description`, used in tile popovers, sidebar tooltips and the manual (e.g. Receipt History: "Search, view, reprint or cancel issued receipts"). Activity codes are the existing `NAVIGATE_*` constants.

Item ids are **stable**: they are stored in `users.landing_screen` and must never be renamed.

## 4. Rules — `NavigationAccess`

Constructed with the user's permission codes and `isPrimaryMachine`.

- `visibleItems(NavModule)`: items where the user has any of `permissionCodes` (empty list = always visible), minus `admin.backup` on a secondary machine.
- `visibleModules()`: modules with at least one visible item, in registry order.
- `opensDirectly(NavModule)`: true when exactly one item is visible. The tile opens that screen, and the sidebar still shows the module.
- `showHomeAndSwitcher()`: false when exactly one module is visible.
- `useSections(NavModule)`: true when more than 7 items are visible.
- `landing(String savedItemId)` returns `Landing(NavItem item, boolean fellBack)`:
  1. the saved item, if visible;
  2. else `dashboard.overview`, if visible;
  3. else the first visible item of the first visible module.
  `fellBack` is true when a saved id was given but not used.
- `moduleForShortcut(int number)`: the visible module with that shortcut number, if any.

## 5. Frame — `dashboard-view.fxml` / `DashboardController`

- The sidebar `VBox` keeps the collapse toggle and gets a code-built header plus item list:
  - Header: `← Home` button; module icon and name, which opens a `ContextMenu` switcher listing the visible modules. Both are hidden when `showHomeAndSwitcher()` is false.
  - Items: one menu button per visible item, with the existing `menu-button` / `active-menu-button` styles. Section header labels appear only when `useSections`. Collapsed: icon only, with the title as a `Tooltip`.
- The page title becomes a breadcrumb `HBox`: `Home › <Module> › <Item>`. Each part is a link-style button; Home is omitted when Home is hidden.
- Navigation API (private): `openHome()`, `openModule(NavModule)`, `openItem(NavItem)`. `openModule` opens the remembered item for that module, else its first visible item.
- Before any navigation (sidebar, Home, switcher, shortcut, breadcrumb, logout), if the current content controller implements `UnsavedChangesAware` and `hasUnsavedChanges()` is true, ask: "You have unsaved changes. Leave this screen?" Cancel keeps the screen.
- Content is loaded with an `FXMLLoader` instance (not the static `load`) so the frame keeps the controller. Report items call `ReportsController.setReportGroup(ReportGroup)` after loading.
- Scene accelerators (installed when the scene is ready): `Ctrl+H` → Home; `Ctrl+1…7` → `moduleForShortcut`.
- After login (and after force-password-change, unchanged): `openItem(landing(user.landingScreen).item())`. If `fellBack`, set the status "Your saved start screen is no longer available — opened <title>."
- `openReceiptCorrection(id)` and `openSendMessage()` keep their public static signatures and open `collections.receipt` / `messaging.send-message`.
- Logging: item navigation logs as today. Opening a module from Home or the switcher also logs `NAVIGATE_MODULE` ("Navigated to module: <title>").
- My Profile (header button) and Logout stay in the header.

## 6. Home — `home-view.fxml` / `HomeController`

- Greeting row: "Good morning/afternoon/evening, <full name>" and today's date in the configured date format.
- `TilePane` of tiles about 220 × 140 px, wrapping to the width, in fixed registry order, visible modules only.
- Tile: icon, title, description, optional badge, and an **(i)** button in the top-right corner.
  - The whole tile is a focusable button. Click / Enter / Space opens the module (or its only item).
  - Hover/focus: raised shadow and accent border; visible focus ring.
  - Tooltip: "<Module> (Ctrl+<n>)".
- **(i) popover** (`Popup`): module icon and title, then each visible item with its icon, title and description, grouped by section when `useSections`, then an **Open module** button. The (i) button does not open the module. It is focusable, opens with Enter/Space, and closes with Esc or an outside click. One popover at a time. Accessible text: "What's in <module>".
- **Badges** (D6) load on a background thread and are applied on the FX thread. A failure hides the badge. Text plus colour (e.g. "12 pending", "3 failed").
  - Collections: `DashboardRepository` pending-church count for the current receipt week from `WeekUtil` (all regions).
  - Messaging: new `SmsLogRepository.countRecentFailed(LocalDateTime since)` — `status = 'FAILED'`, `created_at >= since`, and no other row with `resend_of_sms_log_id` = its id; `since` = now − 7 days.
- `HomeController` gets the registry, `NavigationAccess` and a callback to open a module from the frame through a small `HomeNavigator` interface (set after load).
- Styles in `app.css` for the Orchid, Light and Dark themes. Text contrast ≥ 4.5:1, click targets ≥ 44 px.

## 7. Reports grouping

- New `enum ReportGroup { COLLECTIONS, MESSAGING, ADMINISTRATION }`. `ReportType` gets a `group()`: SMS_DELIVERY → MESSAGING; USER_ACTIVITY and BACKUP_RESTORE_HISTORY → ADMINISTRATION; every other type → COLLECTIONS.
- `ReportsController.setReportGroup(ReportGroup)` limits the report-type combo to that group (registry order kept) and selects the first. With no group set, it shows all types (no behaviour change for anything else).

## 8. Start screen — migration `V42__user_landing_screen.sql`

- `ALTER TABLE users ADD COLUMN landing_screen VARCHAR(80) NULL`.
- `AuthenticatedUser` gains `landingScreen` (nullable), filled at login by the existing user lookup.
- **My Profile:** "Open after login" combo: "Dashboard (default)" plus every item visible to the user, labelled "<Module> › <Item>". Saved with the profile.
- **User Management** (edit user): the same combo, built from that user's role permissions, saved with the user.
- Unknown or no-longer-visible ids are handled at login by `landing()` (§4), not by validation.

## 9. Unsaved-changes guard

```java
public interface UnsavedChangesAware {
    boolean hasUnsavedChanges();
}
```

- `ReceiptEntryController`: true when a branch is selected or any amount field is non-blank, and the form has not been saved or cleared since.
- `SendMessageController`: true when the message text is non-blank.

## 10. Tests

- **`NavigationAccessTest`:** item filtering by permission; empty-permission items always visible; backup hidden on a secondary machine; tile hidden when no items visible; `opensDirectly`; `showHomeAndSwitcher` false for a single-module user; `useSections` threshold (7 vs 8); `landing` saved / unset / not permitted / nothing but one module; `moduleForShortcut` for a visible, hidden and unused number.
- **`NavigationRegistryTest`:** ids unique; `findItem` round-trip; every `viewPath` exists on the classpath; shortcut numbers unique; every report item has a group; every `ReportType` maps to a group, and each group has the expected types.
- **Repository:** `countRecentFailed` SQL and parameter; landing-screen read in the login lookup and write in profile / user updates.
- **Services:** profile and user-management updates carry `landingScreen`.
- **Manual** (05c.8): each role (admin, data entry, viewer) on both machines; login lands on the start screen; (i) opens without opening the module and closes with Esc; keyboard-only navigation; collapsed tooltips; widths 1366 and 1920; unsaved-receipt guard; Ctrl+H / Ctrl+1…7; the three report items show only their own types.

## 11. Documentation

User manual EN + SI: a new "Getting around" section (Home tiles, (i) popover, module sidebar, switcher, breadcrumb, shortcuts, Open after login), and every screen's "how to open" path updated to `<Module> › <Item>`.

## 12. Out of scope

- Finance, Members and Branch Documents screens and tiles (M2 / M3), and their badges.
- Persisting "remember position" across sessions.
- Unsaved-changes tracking on screens other than Receipt Entry and Send Message.
