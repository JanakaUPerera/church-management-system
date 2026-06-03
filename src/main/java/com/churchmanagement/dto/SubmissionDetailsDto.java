package com.churchmanagement.dto;

import com.churchmanagement.enums.ReceiptStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SubmissionDetailsDto {
    private Long receiptId;
    private String receiptNo;
    private String churchCode;
    private String churchName;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private String bearerName;
    private String submittedBy;
    private BigDecimal offertoryAmount = BigDecimal.ZERO;
    private BigDecimal tithesAmount = BigDecimal.ZERO;
    private BigDecimal otherDonationsAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private boolean lateSubmission;
    private String smsStatus;
    private boolean originalPrinted;
    private ReceiptStatus receiptStatus;
    private LocalDateTime submittedDate;
    private List<String> auditHistory = new ArrayList<>();

    public Long getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(Long receiptId) {
        this.receiptId = receiptId;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public String getChurchCode() {
        return churchCode;
    }

    public void setChurchCode(String churchCode) {
        this.churchCode = churchCode;
    }

    public String getChurchName() {
        return churchName;
    }

    public void setChurchName(String churchName) {
        this.churchName = churchName;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public void setWeekStartDate(LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
    }

    public LocalDate getWeekEndDate() {
        return weekEndDate;
    }

    public void setWeekEndDate(LocalDate weekEndDate) {
        this.weekEndDate = weekEndDate;
    }

    public String getBearerName() {
        return bearerName;
    }

    public void setBearerName(String bearerName) {
        this.bearerName = bearerName;
    }

    public String getSubmittedBy() {
        return submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public BigDecimal getOffertoryAmount() {
        return offertoryAmount;
    }

    public void setOffertoryAmount(BigDecimal offertoryAmount) {
        this.offertoryAmount = safeAmount(offertoryAmount);
    }

    public BigDecimal getTithesAmount() {
        return tithesAmount;
    }

    public void setTithesAmount(BigDecimal tithesAmount) {
        this.tithesAmount = safeAmount(tithesAmount);
    }

    public BigDecimal getOtherDonationsAmount() {
        return otherDonationsAmount;
    }

    public void setOtherDonationsAmount(BigDecimal otherDonationsAmount) {
        this.otherDonationsAmount = safeAmount(otherDonationsAmount);
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = safeAmount(totalAmount);
    }

    public boolean isLateSubmission() {
        return lateSubmission;
    }

    public void setLateSubmission(boolean lateSubmission) {
        this.lateSubmission = lateSubmission;
    }

    public String getSmsStatus() {
        return smsStatus;
    }

    public void setSmsStatus(String smsStatus) {
        this.smsStatus = smsStatus;
    }

    public boolean isOriginalPrinted() {
        return originalPrinted;
    }

    public void setOriginalPrinted(boolean originalPrinted) {
        this.originalPrinted = originalPrinted;
    }

    public ReceiptStatus getReceiptStatus() {
        return receiptStatus;
    }

    public void setReceiptStatus(ReceiptStatus receiptStatus) {
        this.receiptStatus = receiptStatus;
    }

    public LocalDateTime getSubmittedDate() {
        return submittedDate;
    }

    public void setSubmittedDate(LocalDateTime submittedDate) {
        this.submittedDate = submittedDate;
    }

    public List<String> getAuditHistory() {
        return auditHistory;
    }

    public void setAuditHistory(List<String> auditHistory) {
        this.auditHistory = auditHistory == null ? new ArrayList<>() : new ArrayList<>(auditHistory);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
