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
