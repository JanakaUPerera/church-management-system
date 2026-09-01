package com.churchmanagement.config;

import java.nio.file.Path;

/**
 * Single source of truth for the external {@code application.properties} location.
 *
 * <h3>Dev / IDE mode ({@code app.home} absent)</h3>
 * <p>Returns {@code null} — the bundled classpath file is the configuration
 * source and no external file is read or written.</p>
 *
 * <h3>Production (jpackage install)</h3>
 * <p>The external file lives in a <em>user-writable</em> directory so normal
 * (non-admin) users can save the database wizard configuration without needing
 * elevated privileges:</p>
 * <ul>
 *   <li>Windows — {@code %APPDATA%\Church Management System\application.properties}
 *       (e.g. {@code C:\Users\Jana\AppData\Roaming\Church Management System\})</li>
 *   <li>macOS / Linux — {@code $HOME/.church-management/application.properties}</li>
 * </ul>
 *
 * <p>{@code app.home} still signals "we are installed" but is no longer used
 * as the config directory — that role belongs to AppData on Windows so that
 * writing the file never requires administrator rights.</p>
 */
public final class AppHome {

    /** Folder name used under APPDATA (Windows) or HOME (other OS). */
    private static final String APP_FOLDER = "Church Management System";

    private AppHome() {}

    /**
     * Returns the directory where the external {@code application.properties}
     * is stored, creating it if necessary before the first write.
     * Returns {@code null} when running in dev / IDE mode.
     */
    public static Path configDir() {
        if (System.getProperty("app.home") == null) {
            return null; // dev mode — use bundled classpath properties
        }

        // Windows: %APPDATA% → C:\Users\<user>\AppData\Roaming
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            return Path.of(appData, APP_FOLDER);
        }

        // macOS / Linux fallback
        String xdgConfig = System.getenv("XDG_CONFIG_HOME");
        if (xdgConfig != null) {
            return Path.of(xdgConfig, APP_FOLDER);
        }
        return Path.of(System.getProperty("user.home"), ".church-management");
    }

    /**
     * Returns the full path to the external {@code application.properties},
     * or {@code null} in dev mode.
     */
    public static Path configFile() {
        Path dir = configDir();
        return dir == null ? null : dir.resolve("application.properties");
    }
}
