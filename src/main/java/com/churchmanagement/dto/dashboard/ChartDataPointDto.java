package com.churchmanagement.dto.dashboard;

import java.math.BigDecimal;

public class ChartDataPointDto {
    private String label;
    private BigDecimal value = BigDecimal.ZERO;

    public ChartDataPointDto() {
    }

    public ChartDataPointDto(String label, BigDecimal value) {
        this.label = label;
        this.value = value == null ? BigDecimal.ZERO : value;
    }

    public static ChartDataPointDto of(String label, long value) {
        return new ChartDataPointDto(label, BigDecimal.valueOf(value));
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getValue() {
        return value == null ? BigDecimal.ZERO : value;
    }

    public void setValue(BigDecimal value) {
        this.value = value == null ? BigDecimal.ZERO : value;
    }
}
