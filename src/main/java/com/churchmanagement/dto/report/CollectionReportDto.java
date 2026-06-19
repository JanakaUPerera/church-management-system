package com.churchmanagement.dto.report;

import java.math.BigDecimal;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;

public class CollectionReportDto extends AbstractReportRow {
    private int year;
    private Integer month;
    private String regionName;
    private String churchName;
    private boolean monthly;
    private boolean churchWise;
    private BigDecimal offertoryTotal = BigDecimal.ZERO;
    private BigDecimal tithesTotal = BigDecimal.ZERO;
    private BigDecimal otherDonationsTotal = BigDecimal.ZERO;
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Override
    public LinkedHashMap<String, Object> columns() {
        if (monthly && churchWise) {
            return columns("Year", year, "Month", monthName(), "Region", text(regionName), "Church", text(churchName),
                    "Offerings", zero(offertoryTotal), "Tithes", zero(tithesTotal),
                    "Other Donations", zero(otherDonationsTotal), "Grand Total", zero(grandTotal));
        }
        if (monthly) {
            return columns("Year", year, "Month", monthName(), "Region", text(regionName),
                    "Offerings", zero(offertoryTotal), "Tithes", zero(tithesTotal),
                    "Other Donations", zero(otherDonationsTotal), "Grand Total", zero(grandTotal));
        }
        if (churchWise) {
            return columns("Year", year, "Region", text(regionName), "Church", text(churchName),
                    "Offerings", zero(offertoryTotal), "Tithes", zero(tithesTotal),
                    "Other Donations", zero(otherDonationsTotal), "Grand Total", zero(grandTotal));
        }
        return columns("Year", year, "Region", text(regionName),
                "Offerings", zero(offertoryTotal), "Tithes", zero(tithesTotal),
                "Other Donations", zero(otherDonationsTotal), "Grand Total", zero(grandTotal));
    }

    private String monthName() {
        return month == null ? "" : Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public String getChurchName() { return churchName; }
    public void setChurchName(String churchName) { this.churchName = churchName; }
    public boolean isMonthly() { return monthly; }
    public void setMonthly(boolean monthly) { this.monthly = monthly; }
    public boolean isChurchWise() { return churchWise; }
    public void setChurchWise(boolean churchWise) { this.churchWise = churchWise; }
    public BigDecimal getOffertoryTotal() { return offertoryTotal; }
    public void setOffertoryTotal(BigDecimal offertoryTotal) { this.offertoryTotal = zero(offertoryTotal); }
    public BigDecimal getTithesTotal() { return tithesTotal; }
    public void setTithesTotal(BigDecimal tithesTotal) { this.tithesTotal = zero(tithesTotal); }
    public BigDecimal getOtherDonationsTotal() { return otherDonationsTotal; }
    public void setOtherDonationsTotal(BigDecimal otherDonationsTotal) { this.otherDonationsTotal = zero(otherDonationsTotal); }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = zero(grandTotal); }
}
