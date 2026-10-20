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
