package com.churchmanagement.dto;

import java.math.BigDecimal;

public class SubmissionTotalsDto {
    private BigDecimal totalOffertory = BigDecimal.ZERO;
    private BigDecimal totalTithes = BigDecimal.ZERO;
    private BigDecimal totalOtherDonations = BigDecimal.ZERO;
    private BigDecimal grandTotal = BigDecimal.ZERO;

    public BigDecimal getTotalOffertory() {
        return totalOffertory;
    }

    public void setTotalOffertory(BigDecimal totalOffertory) {
        this.totalOffertory = safeAmount(totalOffertory);
    }

    public BigDecimal getTotalTithes() {
        return totalTithes;
    }

    public void setTotalTithes(BigDecimal totalTithes) {
        this.totalTithes = safeAmount(totalTithes);
    }

    public BigDecimal getTotalOtherDonations() {
        return totalOtherDonations;
    }

    public void setTotalOtherDonations(BigDecimal totalOtherDonations) {
        this.totalOtherDonations = safeAmount(totalOtherDonations);
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = safeAmount(grandTotal);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
