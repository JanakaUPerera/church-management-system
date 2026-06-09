package com.churchmanagement.dto.report;

import java.time.LocalDate;
import java.util.LinkedHashMap;

public class MissingSubmissionReportDto extends AbstractReportRow {
    private Long churchId;
    private String regionCode;
    private String regionName;
    private String churchCode;
    private String churchName;
    private LocalDate weekStartDate;
    private String smsMobileNumber;

    @Override
    public LinkedHashMap<String, Object> columns() {
        return columns("Region Code", text(regionCode), "Region", text(regionName), "Church Code", text(churchCode),
                "Church", text(churchName), "Week Start", date(weekStartDate), "SMS Mobile", text(smsMobileNumber),
                "Status", "MISSING");
    }

    @Override
    public Long detailId() { return churchId; }
    public Long getChurchId() { return churchId; }
    public void setChurchId(Long churchId) { this.churchId = churchId; }
    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public String getChurchCode() { return churchCode; }
    public void setChurchCode(String churchCode) { this.churchCode = churchCode; }
    public String getChurchName() { return churchName; }
    public void setChurchName(String churchName) { this.churchName = churchName; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(LocalDate weekStartDate) { this.weekStartDate = weekStartDate; }
    public String getSmsMobileNumber() { return smsMobileNumber; }
    public void setSmsMobileNumber(String smsMobileNumber) { this.smsMobileNumber = smsMobileNumber; }
}
