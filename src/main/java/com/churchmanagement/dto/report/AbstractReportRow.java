package com.churchmanagement.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;

public abstract class AbstractReportRow implements ReportTableRow {
    protected LinkedHashMap<String, Object> columns(Object... values) {
        LinkedHashMap<String, Object> columns = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            columns.put(String.valueOf(values[index]), values[index + 1]);
        }
        return columns;
    }

    protected BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    protected String text(String value) {
        return value == null ? "" : value;
    }

    protected LocalDate date(LocalDate value) {
        return value;
    }

    protected LocalDateTime dateTime(LocalDateTime value) {
        return value;
    }
}
