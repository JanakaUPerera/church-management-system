package com.churchmanagement.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public final class WeekUtil {
    public static final DayOfWeek DEFAULT_IDENTIFIER_DAY = DayOfWeek.MONDAY;
    public static final String IDENTIFIER_DAY_SETTING_KEY = "receipt.week.identifier.day";

    private WeekUtil() {
    }

    /**
     * The identifier date of the week containing {@code today} — the most
     * recent occurrence of {@code identifierDay} on or before {@code today}.
     * This is "the current submission week" with no lag: if today already
     * is the identifier day, today is returned.
     */
    public static LocalDate currentIdentifier(LocalDate today, DayOfWeek identifierDay) {
        return today.with(TemporalAdjusters.previousOrSame(identifierDay));
    }

    /** The earlier boundary of the week ending on {@code identifierDate} (6 days before it). */
    public static LocalDate weekStartFor(LocalDate identifierDate) {
        return identifierDate == null ? null : identifierDate.minusDays(6);
    }

    /** True if {@code date} falls on the configured identifier day (the week's last day). */
    public static boolean isIdentifierDay(LocalDate date, DayOfWeek identifierDay) {
        return date != null && date.getDayOfWeek() == identifierDay;
    }

    /** True if {@code date} falls on the week's first day (6 days before the identifier day). */
    public static boolean isWeekStartDay(LocalDate date, DayOfWeek identifierDay) {
        return date != null && date.getDayOfWeek() == identifierDay.plus(1);
    }

    /** True if the selected week's identifier date is before the current submission week's identifier. */
    public static boolean isBackWeek(LocalDate selectedIdentifier, LocalDate today, DayOfWeek identifierDay) {
        return selectedIdentifier != null && selectedIdentifier.isBefore(currentIdentifier(today, identifierDay));
    }

    /** Parses the {@code receipt.week.identifier.day} setting value, defaulting to Monday. */
    public static DayOfWeek parseIdentifierDay(String settingValue) {
        if (settingValue == null || settingValue.isBlank()) {
            return DEFAULT_IDENTIFIER_DAY;
        }
        try {
            return DayOfWeek.valueOf(settingValue.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return DEFAULT_IDENTIFIER_DAY;
        }
    }

    /** Full English display name for a day of week, e.g. {@code MONDAY} -&gt; {@code "Monday"}. */
    public static String displayName(DayOfWeek dayOfWeek) {
        return dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
