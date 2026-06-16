package com.churchmanagement.reports.export;

import java.util.List;

public class ReportExportRow {
    private final List<String> values;
    private final boolean totalsRow;
    private final boolean oddRow;

    public ReportExportRow(List<String> values, boolean totalsRow, boolean oddRow) {
        this.values = values;
        this.totalsRow = totalsRow;
        this.oddRow = oddRow;
    }

    public String getColumn1() { return value(0); }
    public String getColumn2() { return value(1); }
    public String getColumn3() { return value(2); }
    public String getColumn4() { return value(3); }
    public String getColumn5() { return value(4); }
    public String getColumn6() { return value(5); }
    public String getColumn7() { return value(6); }
    public String getColumn8() { return value(7); }
    public String getColumn9() { return value(8); }
    public String getColumn10() { return value(9); }
    public String getColumn11() { return value(10); }
    public String getColumn12() { return value(11); }
    public boolean isTotalsRow() { return totalsRow; }
    public boolean isOddRow() { return oddRow; }

    private String value(int index) {
        return index < values.size() ? values.get(index) : "";
    }
}
