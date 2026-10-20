package com.churchmanagement.service;

import com.churchmanagement.entity.FiscalYear;
import com.churchmanagement.testsupport.FinanceFakes;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FiscalYearServiceTest {
    @Test
    void createsMissingYearOpen() {
        FinanceFakes.FakeFiscalYearRepository repository = new FinanceFakes.FakeFiscalYearRepository();

        FiscalYear year = new FiscalYearService(repository).forDate(LocalDate.of(2027, 3, 4));

        assertEquals(2027, year.year());
        assertEquals(FiscalYear.Status.OPEN, year.status());
        assertEquals(LocalDate.of(2027, 12, 31), year.endDate());
    }

    @Test
    void closedYearIsRejected() {
        FinanceFakes.FakeFiscalYearRepository repository = new FinanceFakes.FakeFiscalYearRepository();
        repository.close(2027);

        FinanceException exception = assertThrows(FinanceException.class,
                () -> new FiscalYearService(repository).requireOpen(LocalDate.of(2027, 6, 1)));

        assertEquals("The 2027 accounting year is closed.", exception.getMessage());
    }
}
