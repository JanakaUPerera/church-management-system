package com.churchmanagement.reports.export;

import java.util.List;

public class ReportExportRow {
    private final List<String> values;

    public ReportExportRow(List<String> values) {
        this.values = values;
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

    private String value(int index) {
        return index < values.size() ? values.get(index) : "";
    }
}
