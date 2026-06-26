package com.churchmanagement.util;

import java.util.prefs.Preferences;

/**
 * Persists the last-applied theme to OS-level Java Preferences so the
 * startup screen can reflect the user's choice before the database connects.
 */
public final class ThemePreferenceStore {
    private static final Preferences PREFS =
            Preferences.userNodeForPackage(ThemePreferenceStore.class);
    private static final String KEY = "system.theme";

    private ThemePreferenceStore() {
    }

    public static String load() {
        return PREFS.get(KEY, "ORCHID");
    }

    public static void save(String theme) {
        if (theme != null && !theme.isBlank()) {
            PREFS.put(KEY, theme.toUpperCase());
        }
    }
}
