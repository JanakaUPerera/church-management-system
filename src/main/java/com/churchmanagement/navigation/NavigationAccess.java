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
