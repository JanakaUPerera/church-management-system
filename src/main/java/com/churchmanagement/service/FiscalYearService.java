package com.churchmanagement.service;

import com.churchmanagement.entity.FiscalYear;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.FiscalYearRepository;

import java.time.LocalDate;

/** Calendar accounting years, created on first use. Closing is F6. */
public class FiscalYearService {
    private final FiscalYearRepository fiscalYearRepository;

    public FiscalYearService() {
        this(new FiscalYearRepository());
    }

    public FiscalYearService(FiscalYearRepository fiscalYearRepository) {
        this.fiscalYearRepository = fiscalYearRepository;
    }

    public FiscalYear forDate(LocalDate date) {
        int year = date.getYear();
        try {
            return fiscalYearRepository.findByYear(year).orElseGet(() -> {
                fiscalYearRepository.insertIfMissing(FiscalYear.open(year));
                return fiscalYearRepository.findByYear(year).orElse(FiscalYear.open(year));
            });
        } catch (DatabaseException exception) {
            throw new FinanceException("Unable to load the accounting year right now.", exception);
        }
    }

    public FiscalYear requireOpen(LocalDate date) {
        FiscalYear fiscalYear = forDate(date);
        if (fiscalYear.isClosed()) {
            throw new FinanceException("The " + fiscalYear.year() + " accounting year is closed.");
        }
        return fiscalYear;
    }
}
