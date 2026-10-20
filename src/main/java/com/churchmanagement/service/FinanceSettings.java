package com.churchmanagement.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Read access to the finance settings in system_settings. */
public class FinanceSettings {
    public static final String GO_LIVE_DATE_KEY = "finance.go_live_date";
    public static final String APPROVAL_REQUIRED_KEY = "finance.approval.required";
    public static final LocalDate DEFAULT_GO_LIVE_DATE = LocalDate.of(2027, 1, 1);

    private final SystemConfigurationCache configurationCache;

    public FinanceSettings() {
        this(SystemConfigurationCache.getInstance());
    }

    public FinanceSettings(SystemConfigurationCache configurationCache) {
        this.configurationCache = configurationCache;
    }

    public LocalDate goLiveDate() {
        String value = configurationCache.getString(GO_LIVE_DATE_KEY);
        if (value == null || value.isBlank()) {
            return DEFAULT_GO_LIVE_DATE;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException exception) {
            return DEFAULT_GO_LIVE_DATE;
        }
    }

    public boolean approvalRequired() {
        return Boolean.parseBoolean(configurationCache.getString(APPROVAL_REQUIRED_KEY));
    }
}
