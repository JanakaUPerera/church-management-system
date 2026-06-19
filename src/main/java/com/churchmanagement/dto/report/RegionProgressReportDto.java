package com.churchmanagement.dto.report;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

public class RegionProgressReportDto extends AbstractReportRow {
    private Long regionId;
    private String regionCode;
    private String regionName;
    private long totalChurches;
    private long submittedWeeks;
    private long missingWeeks;
    private long lateCount;
    private BigDecimal offertoryTotal = BigDecimal.ZERO;
    private BigDecimal tithesTotal = BigDecimal.ZERO;
    private BigDecimal otherDonationsTotal = BigDecimal.ZERO;
    private BigDecimal totalCollections = BigDecimal.ZERO;

    @Override
    public LinkedHashMap<String, Object> columns() {
        return columns("Region Code", text(regionCode), "Region", text(regionName), "Total Churches", totalChurches,
                "Submitted Weeks", submittedWeeks, "Missing Weeks", missingWeeks, "Late Count", lateCount,
                "Offerings", zero(offertoryTotal), "Tithes", zero(tithesTotal),
                "Other Donations", zero(otherDonationsTotal),
                "Total Collections", zero(totalCollections));
    }

    @Override
    public Long detailId() { return regionId; }
    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }
    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public long getTotalChurches() { return totalChurches; }
    public void setTotalChurches(long totalChurches) { this.totalChurches = totalChurches; }
    public long getSubmittedWeeks() { return submittedWeeks; }
    public void setSubmittedWeeks(long submittedWeeks) { this.submittedWeeks = submittedWeeks; }
    public long getMissingWeeks() { return missingWeeks; }
    public void setMissingWeeks(long missingWeeks) { this.missingWeeks = missingWeeks; }
    public long getLateCount() { return lateCount; }
    public void setLateCount(long lateCount) { this.lateCount = lateCount; }
    public BigDecimal getOffertoryTotal() { return offertoryTotal; }
    public void setOffertoryTotal(BigDecimal offertoryTotal) { this.offertoryTotal = zero(offertoryTotal); }
    public BigDecimal getTithesTotal() { return tithesTotal; }
    public void setTithesTotal(BigDecimal tithesTotal) { this.tithesTotal = zero(tithesTotal); }
    public BigDecimal getOtherDonationsTotal() { return otherDonationsTotal; }
    public void setOtherDonationsTotal(BigDecimal otherDonationsTotal) { this.otherDonationsTotal = zero(otherDonationsTotal); }
    public BigDecimal getTotalCollections() { return totalCollections; }
    public void setTotalCollections(BigDecimal totalCollections) { this.totalCollections = zero(totalCollections); }
}
