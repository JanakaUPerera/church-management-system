package com.churchmanagement.entity;

import java.time.LocalDate;

/** A calendar accounting year. */
public record FiscalYear(int year, LocalDate startDate, LocalDate endDate, Status status) {
    public enum Status {
        OPEN,
        CLOSED
    }

    public static FiscalYear open(int year) {
        return new FiscalYear(year, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), Status.OPEN);
    }

    public boolean isClosed() {
        return status == Status.CLOSED;
    }
}
