package com.churchmanagement.dto.dashboard;

import java.math.BigDecimal;

public class ChurchCollectionTrendDto {
    private String periodLabel;
    private String churchName;
    private BigDecimal amount = BigDecimal.ZERO;

    public ChurchCollectionTrendDto() {
    }

    public ChurchCollectionTrendDto(String periodLabel, String churchName, BigDecimal amount) {
        this.periodLabel = periodLabel;
        this.churchName = churchName;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
    }

    public String getPeriodLabel() {
        return periodLabel;
    }

    public void setPeriodLabel(String periodLabel) {
        this.periodLabel = periodLabel;
    }

    public String getChurchName() {
        return churchName;
    }

    public void setChurchName(String churchName) {
        this.churchName = churchName;
    }

    public BigDecimal getAmount() {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount == null ? BigDecimal.ZERO : amount;
    }
}
