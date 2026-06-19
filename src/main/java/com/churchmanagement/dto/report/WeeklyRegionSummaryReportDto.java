package com.churchmanagement.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;

public class WeeklyRegionSummaryReportDto extends AbstractReportRow {
    private Long regionId;
    private String regionCode;
    private String regionName;
    private LocalDate weekStartDate;
    private long totalChurches;
    private long submittedChurches;
    private long missingChurches;
    private long lateSubmissions;
    private BigDecimal offertoryTotal = BigDecimal.ZERO;
    private BigDecimal tithesTotal = BigDecimal.ZERO;
    private BigDecimal otherDonationsTotal = BigDecimal.ZERO;
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Override
    public LinkedHashMap<String, Object> columns() {
        return columns("Region Code", text(regionCode), "Region", text(regionName), "Week Start", date(weekStartDate),
                "Total Churches", totalChurches, "Submitted", submittedChurches, "Missing", missingChurches,
                "Late", lateSubmissions, "Offerings", zero(offertoryTotal), "Tithes", zero(tithesTotal),
                "Other Donations", zero(otherDonationsTotal), "Grand Total", zero(grandTotal));
    }

    @Override
    public Long detailId() { return regionId; }
    public Long getRegionId() { return regionId; }
    public void setRegionId(Long regionId) { this.regionId = regionId; }
    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(LocalDate weekStartDate) { this.weekStartDate = weekStartDate; }
    public long getTotalChurches() { return totalChurches; }
    public void setTotalChurches(long totalChurches) { this.totalChurches = totalChurches; }
    public long getSubmittedChurches() { return submittedChurches; }
    public void setSubmittedChurches(long submittedChurches) { this.submittedChurches = submittedChurches; }
    public long getMissingChurches() { return missingChurches; }
    public void setMissingChurches(long missingChurches) { this.missingChurches = missingChurches; }
    public long getLateSubmissions() { return lateSubmissions; }
    public void setLateSubmissions(long lateSubmissions) { this.lateSubmissions = lateSubmissions; }
    public BigDecimal getOffertoryTotal() { return offertoryTotal; }
    public void setOffertoryTotal(BigDecimal offertoryTotal) { this.offertoryTotal = zero(offertoryTotal); }
    public BigDecimal getTithesTotal() { return tithesTotal; }
    public void setTithesTotal(BigDecimal tithesTotal) { this.tithesTotal = zero(tithesTotal); }
    public BigDecimal getOtherDonationsTotal() { return otherDonationsTotal; }
    public void setOtherDonationsTotal(BigDecimal otherDonationsTotal) { this.otherDonationsTotal = zero(otherDonationsTotal); }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = zero(grandTotal); }
}
