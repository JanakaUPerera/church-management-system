package com.churchmanagement.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;

public class SubmissionStatusReportDto extends AbstractReportRow {
    private Long receiptId;
    private String regionName;
    private String churchCode;
    private String churchName;
    private LocalDate weekStartDate;
    private String status;
    private String receiptNo;
    private LocalDateTime submittedAt;
    private boolean lateSubmission;
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Override
    public LinkedHashMap<String, Object> columns() {
        return columns("Region", text(regionName), "Church Code", text(churchCode), "Church", text(churchName),
                "Week Start", date(weekStartDate), "Status", text(status), "Receipt No", text(receiptNo),
                "Submitted At", dateTime(submittedAt), "Late Submission", lateSubmission ? "Yes" : "No",
                "Grand Total", zero(grandTotal));
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public boolean isLateSubmission() { return lateSubmission; }
    public void setLateSubmission(boolean lateSubmission) { this.lateSubmission = lateSubmission; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = zero(grandTotal); }
}
