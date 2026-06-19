package com.churchmanagement.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;

public class LateSubmissionReportDto extends AbstractReportRow {
    private Long receiptId;
    private String regionName;
    private String churchCode;
    private String churchName;
    private LocalDate weekStartDate;
    private String receiptNo;
    private LocalDateTime submittedAt;
    private String reason;
    private BigDecimal offertoryTotal = BigDecimal.ZERO;
    private BigDecimal tithesTotal = BigDecimal.ZERO;
    private BigDecimal otherDonationsTotal = BigDecimal.ZERO;
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Override
    public LinkedHashMap<String, Object> columns() {
        return columns("Region", text(regionName), "Church Code", text(churchCode), "Church", text(churchName),
                "Week Start", date(weekStartDate), "Receipt No", text(receiptNo), "Submitted At", dateTime(submittedAt),
                "Reason", text(reason), "Offerings", zero(offertoryTotal), "Tithes", zero(tithesTotal),
                "Other Donations", zero(otherDonationsTotal), "Grand Total", zero(grandTotal));
    }

    @Override
    public Long detailId() { return receiptId; }
    public Long getReceiptId() { return receiptId; }
    public void setReceiptId(Long receiptId) { this.receiptId = receiptId; }
    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }
    public String getChurchCode() { return churchCode; }
    public void setChurchCode(String churchCode) { this.churchCode = churchCode; }
    public String getChurchName() { return churchName; }
    public void setChurchName(String churchName) { this.churchName = churchName; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(LocalDate weekStartDate) { this.weekStartDate = weekStartDate; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public BigDecimal getOffertoryTotal() { return offertoryTotal; }
    public void setOffertoryTotal(BigDecimal offertoryTotal) { this.offertoryTotal = zero(offertoryTotal); }
    public BigDecimal getTithesTotal() { return tithesTotal; }
    public void setTithesTotal(BigDecimal tithesTotal) { this.tithesTotal = zero(tithesTotal); }
    public BigDecimal getOtherDonationsTotal() { return otherDonationsTotal; }
    public void setOtherDonationsTotal(BigDecimal otherDonationsTotal) { this.otherDonationsTotal = zero(otherDonationsTotal); }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = zero(grandTotal); }
}
