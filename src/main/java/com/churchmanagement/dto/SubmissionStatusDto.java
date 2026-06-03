package com.churchmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SubmissionStatusDto {
    private Long churchId;
    private String churchCode;
    private String churchName;
    private String regionName;
    private BigDecimal offertoryAmount = BigDecimal.ZERO;
    private BigDecimal tithesAmount = BigDecimal.ZERO;
    private BigDecimal otherDonationsAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private String status;
    private String receiptNo;
    private LocalDateTime submittedDate;
    private boolean lateSubmission;
    private Long receiptId;

    public Long getChurchId() {
        return churchId;
    }

    public void setChurchId(Long churchId) {
        this.churchId = churchId;
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

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public LocalDateTime getSubmittedDate() {
        return submittedDate;
    }

    public void setSubmittedDate(LocalDateTime submittedDate) {
        this.submittedDate = submittedDate;
    }

    public boolean isLateSubmission() {
        return lateSubmission;
    }

    public void setLateSubmission(boolean lateSubmission) {
        this.lateSubmission = lateSubmission;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(Long receiptId) {
        this.receiptId = receiptId;
    }

    public boolean isSubmitted() {
        return "SUBMITTED".equals(status);
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isCancelled() {
        return "CANCELLED".equals(status);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
