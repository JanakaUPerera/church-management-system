package com.churchmanagement.util;

import com.churchmanagement.service.SystemConfigurationCache;
import javafx.scene.Parent;

public class ThemeService {
    public static final String DARK_THEME_CLASS = "theme-dark";
    public static final String ORCHID_THEME_CLASS = "theme-orchid";

    private final SystemConfigurationCache configurationCache;

    public ThemeService() {
        this(SystemConfigurationCache.getInstance());
    }

    public ThemeService(SystemConfigurationCache configurationCache) {
        this.configurationCache = configurationCache;
    }

    public void applyConfiguredTheme(Parent root) {
        if (root == null) {
            return;
        }

        root.getStyleClass().removeAll(DARK_THEME_CLASS, ORCHID_THEME_CLASS);
        String theme = configurationCache.getString("system.theme");
        if ("DARK".equalsIgnoreCase(theme)) {
            root.getStyleClass().add(DARK_THEME_CLASS);
        } else if ("ORCHID".equalsIgnoreCase(theme)) {
            root.getStyleClass().add(ORCHID_THEME_CLASS);
        }
        // Persist so the startup screen can apply the correct theme before
        // the database is available on the next launch.
        if (theme != null && !theme.isBlank()) {
            ThemePreferenceStore.save(theme);
        }
    }
}
