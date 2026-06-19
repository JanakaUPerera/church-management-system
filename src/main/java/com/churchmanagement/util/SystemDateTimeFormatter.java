package com.churchmanagement.util;

import com.churchmanagement.service.SystemConfigurationCache;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class SystemDateTimeFormatter {
    private static final String DEFAULT_DATE_PATTERN = "yyyy-MMM-dd";
    private static final String DEFAULT_TIME_PATTERN = "HH:mm";
    private static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MMM-dd HH:mm";

    private final SystemConfigurationCache configurationCache;

    public SystemDateTimeFormatter() {
        this(SystemConfigurationCache.getInstance());
    }

    public SystemDateTimeFormatter(SystemConfigurationCache configurationCache) {
        this.configurationCache = configurationCache;
    }

    public String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : value.format(dateTimeFormatter());
    }

    public String formatDate(LocalDate value) {
        return value == null ? "-" : value.format(dateFormatter());
    }

    public String formatTime(LocalTime value) {
        return value == null ? "-" : value.format(timeFormatter());
    }

    public DateTimeFormatter dateFormatter() {
        return formatter(setting("system.date.format", DEFAULT_DATE_PATTERN), DEFAULT_DATE_PATTERN);
    }

    public DateTimeFormatter timeFormatter() {
        return formatter(setting("system.time.format", DEFAULT_TIME_PATTERN), DEFAULT_TIME_PATTERN);
    }

    public DateTimeFormatter dateTimeFormatter() {
        String datePattern = setting("system.date.format", DEFAULT_DATE_PATTERN);
        String timePattern = setting("system.time.format", DEFAULT_TIME_PATTERN);
        return formatter(datePattern + " " + timePattern, DEFAULT_DATE_TIME_PATTERN);
    }

    private DateTimeFormatter formatter(String pattern, String fallback) {
        try {
            return DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException exception) {
            return DateTimeFormatter.ofPattern(fallback);
        }
    }

    private String setting(String key, String fallback) {
        String value = configurationCache.getString(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
