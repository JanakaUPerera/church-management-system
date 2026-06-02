package com.churchmanagement.dto.dashboard;

import java.math.BigDecimal;

public class RegionCollectionTrendDto {
    private String periodLabel;
    private String regionName;
    private BigDecimal amount = BigDecimal.ZERO;

    public RegionCollectionTrendDto() {
    }

    public RegionCollectionTrendDto(String periodLabel, String regionName, BigDecimal amount) {
        this.periodLabel = periodLabel;
        this.regionName = regionName;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
    }

    public String getPeriodLabel() {
        return periodLabel;
    }

    public void setPeriodLabel(String periodLabel) {
        this.periodLabel = periodLabel;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public BigDecimal getAmount() {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount == null ? BigDecimal.ZERO : amount;
    }
}
