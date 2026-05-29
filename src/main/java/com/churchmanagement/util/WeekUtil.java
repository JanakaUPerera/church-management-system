package com.churchmanagement.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public final class WeekUtil {
    private WeekUtil() {
    }

    public static LocalDate getPreviousWeekMonday(LocalDate today) {
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
    }

    public static LocalDate getPreviousWeekSunday(LocalDate today) {
        return getPreviousWeekMonday(today).plusDays(6);
    }

    public static boolean isMonday(LocalDate date) {
        return date != null && date.getDayOfWeek() == DayOfWeek.MONDAY;
    }

    public static boolean isWeekStartMonday(LocalDate date) {
        return isMonday(date);
    }

    public static boolean isWeekEndSunday(LocalDate date) {
        return date != null && date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    public static LocalDate getSundayForMonday(LocalDate monday) {
        return monday == null ? null : monday.plusDays(6);
    }

    public static boolean isBackWeek(LocalDate selectedWeekStart, LocalDate today) {
        return selectedWeekStart != null && selectedWeekStart.isBefore(getPreviousWeekMonday(today));
    }

    public static boolean isCurrentSubmissionWeek(LocalDate selectedWeekStart, LocalDate today) {
        return selectedWeekStart != null && selectedWeekStart.equals(getPreviousWeekMonday(today));
    }
}
