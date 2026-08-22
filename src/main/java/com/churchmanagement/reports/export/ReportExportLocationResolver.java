package com.churchmanagement.reports.export;

import com.churchmanagement.config.DatabaseConfig;

import java.nio.file.Path;

/**
 * Resolves the report export folder from this machine's local
 * {@code application.properties} rather than the shared {@code system_settings}
 * table — the export folder is a filesystem path, so it must be per-machine:
 * every client on the LAN keeps its own configured (or default) location
 * instead of all reading one value out of the shared database.
 */
final class ReportExportLocationResolver {
    static final String SETTING_KEY = "reports.export.folder";
    private static final String DEFAULT_FOLDER = "./reports";

    private ReportExportLocationResolver() {
    }

    static Path exportFolder() {
        String configured = DatabaseConfig.getProperty(SETTING_KEY);
        String folder = configured == null || configured.isBlank() ? DEFAULT_FOLDER : configured.strip();
        return Path.of(folder);
    }
}
