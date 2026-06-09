package com.churchmanagement.dto.report;

import java.util.ArrayList;
import java.util.List;

public class ReportResult<T extends ReportTableRow> {
    private ReportType reportType;
    private List<T> rows = new ArrayList<>();
    private ReportSummaryTotals totals = new ReportSummaryTotals();
    private long totalRows;

    public ReportType getReportType() {
        return reportType;
    }

    public void setReportType(ReportType reportType) {
        this.reportType = reportType;
    }

    public List<T> getRows() {
        return rows;
    }

    public void setRows(List<T> rows) {
        this.rows = rows == null ? new ArrayList<>() : rows;
    }

    public ReportSummaryTotals getTotals() {
        return totals;
    }

    public void setTotals(ReportSummaryTotals totals) {
        this.totals = totals == null ? new ReportSummaryTotals() : totals;
    }

    public long getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(long totalRows) {
        this.totalRows = totalRows;
    }
}
