package com.churchmanagement.dto.dashboard;

import java.math.BigDecimal;

public class CollectionTrendDto {
    private String periodLabel;
    private BigDecimal offertoryTotal = BigDecimal.ZERO;
    private BigDecimal tithesTotal = BigDecimal.ZERO;
    private BigDecimal otherDonationsTotal = BigDecimal.ZERO;

    public CollectionTrendDto() {
    }

    public CollectionTrendDto(String periodLabel, BigDecimal offertoryTotal, BigDecimal tithesTotal,
                              BigDecimal otherDonationsTotal) {
        this.periodLabel = periodLabel;
        this.offertoryTotal = safe(offertoryTotal);
        this.tithesTotal = safe(tithesTotal);
        this.otherDonationsTotal = safe(otherDonationsTotal);
    }

    public String getPeriodLabel() {
        return periodLabel;
    }

    public void setPeriodLabel(String periodLabel) {
        this.periodLabel = periodLabel;
    }

    public BigDecimal getOffertoryTotal() {
        return safe(offertoryTotal);
    }

    public void setOffertoryTotal(BigDecimal offertoryTotal) {
        this.offertoryTotal = safe(offertoryTotal);
    }

    public BigDecimal getTithesTotal() {
        return safe(tithesTotal);
    }

    public void setTithesTotal(BigDecimal tithesTotal) {
        this.tithesTotal = safe(tithesTotal);
    }

    public BigDecimal getOtherDonationsTotal() {
        return safe(otherDonationsTotal);
    }

    public void setOtherDonationsTotal(BigDecimal otherDonationsTotal) {
        this.otherDonationsTotal = safe(otherDonationsTotal);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
