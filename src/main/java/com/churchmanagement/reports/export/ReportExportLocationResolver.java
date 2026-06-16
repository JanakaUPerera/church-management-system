package com.churchmanagement.reports.export;

import com.churchmanagement.service.SystemConfigurationCache;

import java.nio.file.Path;

final class ReportExportLocationResolver {
    static final String SETTING_KEY = "reports.export.folder";
    private static final String DEFAULT_FOLDER = "./reports";

    private ReportExportLocationResolver() {
    }

    static Path exportFolder() {
        String configured = SystemConfigurationCache.getInstance().getString(SETTING_KEY);
        String folder = configured == null || configured.isBlank() ? DEFAULT_FOLDER : configured.strip();
        return Path.of(folder);
    }
}
