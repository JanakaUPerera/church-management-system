package com.churchmanagement.util;

import com.churchmanagement.service.SystemConfigurationCache;
import javafx.scene.Parent;

public class ThemeService {
    public static final String DARK_THEME_CLASS = "theme-dark";

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

        root.getStyleClass().remove(DARK_THEME_CLASS);
        String theme = configurationCache.getString("system.theme");
        if ("DARK".equalsIgnoreCase(theme)) {
            root.getStyleClass().add(DARK_THEME_CLASS);
        }
    }
}
