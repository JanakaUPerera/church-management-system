package com.churchmanagement.dto.report;

import java.math.BigDecimal;

public class ReportSummaryTotals {
    private BigDecimal offertoryTotal = BigDecimal.ZERO;
    private BigDecimal tithesTotal = BigDecimal.ZERO;
    private BigDecimal otherDonationsTotal = BigDecimal.ZERO;
    private BigDecimal grandTotal = BigDecimal.ZERO;

    public BigDecimal getOffertoryTotal() {
        return offertoryTotal;
    }

    public void setOffertoryTotal(BigDecimal offertoryTotal) {
        this.offertoryTotal = zeroIfNull(offertoryTotal);
        recomputeGrandTotal();
    }

    public BigDecimal getTithesTotal() {
        return tithesTotal;
    }

    public void setTithesTotal(BigDecimal tithesTotal) {
        this.tithesTotal = zeroIfNull(tithesTotal);
        recomputeGrandTotal();
    }

    public BigDecimal getOtherDonationsTotal() {
        return otherDonationsTotal;
    }

    public void setOtherDonationsTotal(BigDecimal otherDonationsTotal) {
        this.otherDonationsTotal = zeroIfNull(otherDonationsTotal);
        recomputeGrandTotal();
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = zeroIfNull(grandTotal);
    }

    private void recomputeGrandTotal() {
        grandTotal = offertoryTotal.add(tithesTotal).add(otherDonationsTotal);
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
