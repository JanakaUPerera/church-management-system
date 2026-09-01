package com.churchmanagement.util;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeekUtilTest {

    @Test
    void currentIdentifierIsTodayWhenTodayIsTheIdentifierDay() {
        LocalDate monday = LocalDate.of(2026, 5, 18);

        assertEquals(monday, WeekUtil.currentIdentifier(monday, DayOfWeek.MONDAY));
    }

    @Test
    void currentIdentifierIsTheMostRecentIdentifierDayOnOtherDays() {
        LocalDate sunday = LocalDate.of(2026, 5, 24);

        assertEquals(LocalDate.of(2026, 5, 18), WeekUtil.currentIdentifier(sunday, DayOfWeek.MONDAY));
    }

    @Test
    void currentIdentifierSupportsANonDefaultDay() {
        LocalDate wednesday = LocalDate.of(2026, 5, 20);

        assertEquals(LocalDate.of(2026, 5, 15), WeekUtil.currentIdentifier(wednesday, DayOfWeek.FRIDAY));
    }

    @Test
    void weekStartForIsSixDaysBeforeTheIdentifier() {
        assertEquals(LocalDate.of(2026, 5, 12), WeekUtil.weekStartFor(LocalDate.of(2026, 5, 18)));
    }

    @Test
    void weekStartForReturnsNullForNullInput() {
        assertNull(WeekUtil.weekStartFor(null));
    }

    @Test
    void isIdentifierDayMatchesOnlyTheConfiguredDay() {
        assertTrue(WeekUtil.isIdentifierDay(LocalDate.of(2026, 5, 18), DayOfWeek.MONDAY));
        assertFalse(WeekUtil.isIdentifierDay(LocalDate.of(2026, 5, 17), DayOfWeek.MONDAY));
        assertFalse(WeekUtil.isIdentifierDay(null, DayOfWeek.MONDAY));
    }

    @Test
    void isWeekStartDayMatchesSixDaysBeforeTheIdentifierDay() {
        assertTrue(WeekUtil.isWeekStartDay(LocalDate.of(2026, 5, 12), DayOfWeek.MONDAY));
        assertFalse(WeekUtil.isWeekStartDay(LocalDate.of(2026, 5, 11), DayOfWeek.MONDAY));
    }

    @Test
    void isBackWeekIsTrueOnlyBeforeTheCurrentIdentifier() {
        LocalDate today = LocalDate.of(2026, 5, 18);

        assertFalse(WeekUtil.isBackWeek(LocalDate.of(2026, 5, 18), today, DayOfWeek.MONDAY));
        assertTrue(WeekUtil.isBackWeek(LocalDate.of(2026, 5, 11), today, DayOfWeek.MONDAY));
        assertFalse(WeekUtil.isBackWeek(null, today, DayOfWeek.MONDAY));
    }

    @Test
    void parseIdentifierDayDefaultsToMondayForBlankOrInvalidInput() {
        assertEquals(DayOfWeek.MONDAY, WeekUtil.parseIdentifierDay(null));
        assertEquals(DayOfWeek.MONDAY, WeekUtil.parseIdentifierDay(""));
        assertEquals(DayOfWeek.MONDAY, WeekUtil.parseIdentifierDay("  "));
        assertEquals(DayOfWeek.MONDAY, WeekUtil.parseIdentifierDay("NOT_A_DAY"));
    }

    @Test
    void parseIdentifierDayParsesAConfiguredDayCaseInsensitively() {
        assertEquals(DayOfWeek.WEDNESDAY, WeekUtil.parseIdentifierDay("wednesday"));
        assertEquals(DayOfWeek.SUNDAY, WeekUtil.parseIdentifierDay("SUNDAY"));
    }

    @Test
    void displayNameFormatsAsFullEnglishDayName() {
        assertEquals("Monday", WeekUtil.displayName(DayOfWeek.MONDAY));
        assertEquals("Tuesday", WeekUtil.displayName(DayOfWeek.TUESDAY));
    }
}
