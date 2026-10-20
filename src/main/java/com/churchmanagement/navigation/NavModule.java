package com.churchmanagement.navigation;

import java.util.List;

/** A Home tile and its sidebar. {@code shortcutNumber} is Ctrl+number and is fixed per module. */
public record NavModule(String id, String title, String icon, String description, int shortcutNumber,
                        List<NavItem> items) {
    public NavModule {
        items = List.copyOf(items);
    }
}
