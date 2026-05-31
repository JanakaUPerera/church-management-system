package com.churchmanagement.entity;

import com.churchmanagement.enums.ReceiptStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Receipt {
    private Long id;
    private String receiptNo;
    private Long churchId;
    private Long regionId;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private LocalDateTime receiptDateTime;
    private String submittedByName;
    private Long issuedByUserId;
    private ReceiptStatus status;
    private boolean lateSubmission;
    private String lateSubmissionReason;
    private Long correctedFromReceiptId;
    private String pdfFilePath;
    private boolean originalPrinted;
    private LocalDateTime originalPrintedAt;
    private Long originalPrintedByUserId;
    private int printAttemptCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public Long getChurchId() {
        return churchId;
    }

    public void setChurchId(Long churchId) {
        this.churchId = churchId;
    }

    public Long getRegionId() {
        return regionId;
    }

    public void setRegionId(Long regionId) {
        this.regionId = regionId;
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

    public LocalDateTime getReceiptDateTime() {
        return receiptDateTime;
    }

    public void setReceiptDateTime(LocalDateTime receiptDateTime) {
        this.receiptDateTime = receiptDateTime;
    }

    public String getSubmittedByName() {
        return submittedByName;
    }

    public void setSubmittedByName(String submittedByName) {
        this.submittedByName = submittedByName;
    }

    public Long getIssuedByUserId() {
        return issuedByUserId;
    }

    public void setIssuedByUserId(Long issuedByUserId) {
        this.issuedByUserId = issuedByUserId;
    }

    public ReceiptStatus getStatus() {
        return status;
    }

    public void setStatus(ReceiptStatus status) {
        this.status = status;
    }

    public boolean isLateSubmission() {
        return lateSubmission;
    }

    public void setLateSubmission(boolean lateSubmission) {
        this.lateSubmission = lateSubmission;
    }

    public String getLateSubmissionReason() {
        return lateSubmissionReason;
    }

    public void setLateSubmissionReason(String lateSubmissionReason) {
        this.lateSubmissionReason = lateSubmissionReason;
    }

    public Long getCorrectedFromReceiptId() {
        return correctedFromReceiptId;
    }

    public void setCorrectedFromReceiptId(Long correctedFromReceiptId) {
        this.correctedFromReceiptId = correctedFromReceiptId;
    }

    public String getPdfFilePath() {
        return pdfFilePath;
    }

    public void setPdfFilePath(String pdfFilePath) {
        this.pdfFilePath = pdfFilePath;
    }

    public boolean isOriginalPrinted() {
        return originalPrinted;
    }

    public void setOriginalPrinted(boolean originalPrinted) {
        this.originalPrinted = originalPrinted;
    }

    public LocalDateTime getOriginalPrintedAt() {
        return originalPrintedAt;
    }

    public void setOriginalPrintedAt(LocalDateTime originalPrintedAt) {
        this.originalPrintedAt = originalPrintedAt;
    }

    public Long getOriginalPrintedByUserId() {
        return originalPrintedByUserId;
    }

    public void setOriginalPrintedByUserId(Long originalPrintedByUserId) {
        this.originalPrintedByUserId = originalPrintedByUserId;
    }

    public int getPrintAttemptCount() {
        return printAttemptCount;
    }

    public void setPrintAttemptCount(int printAttemptCount) {
        this.printAttemptCount = printAttemptCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
