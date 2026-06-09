package com.churchmanagement.dto.report;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

public class ChurchProgressReportDto extends AbstractReportRow {
    private Long churchId;
    private String regionName;
    private String churchCode;
    private String churchName;
    private long submittedWeeks;
    private long missingWeeks;
    private long lateCount;
    private BigDecimal totalCollections = BigDecimal.ZERO;

    @Override
    public LinkedHashMap<String, Object> columns() {
        return columns("Region", text(regionName), "Church Code", text(churchCode), "Church", text(churchName),
                "Submitted Weeks", submittedWeeks, "Missing Weeks", missingWeeks, "Late Count", lateCount,
                "Total Collections", zero(totalCollections));
    }

    @Override
    public Long detailId() { return churchId; }
    public Long getChurchId() { return churchId; }
    public void setChurchId(Long churchId) { this.churchId = churchId; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public String getChurchCode() { return churchCode; }
    public void setChurchCode(String churchCode) { this.churchCode = churchCode; }
    public String getChurchName() { return churchName; }
    public void setChurchName(String churchName) { this.churchName = churchName; }
    public long getSubmittedWeeks() { return submittedWeeks; }
    public void setSubmittedWeeks(long submittedWeeks) { this.submittedWeeks = submittedWeeks; }
    public long getMissingWeeks() { return missingWeeks; }
    public void setMissingWeeks(long missingWeeks) { this.missingWeeks = missingWeeks; }
    public long getLateCount() { return lateCount; }
    public void setLateCount(long lateCount) { this.lateCount = lateCount; }
    public BigDecimal getTotalCollections() { return totalCollections; }
    public void setTotalCollections(BigDecimal totalCollections) { this.totalCollections = zero(totalCollections); }
}
