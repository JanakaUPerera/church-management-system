package com.churchmanagement.dto;

import com.churchmanagement.enums.ReceiptStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReceiptResponseDto {
    private Long id;
    private String receiptNo;
    private String churchCode;
    private String churchName;
    private String regionCode;
    private String regionName;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private LocalDateTime receiptDateTime;
    private String submittedByName;
    private String issuedByFullName;
    private ReceiptStatus status;
    private boolean lateSubmission;
    private String lateSubmissionReason;
    private Long correctedFromReceiptId;
    private String correctedFromReceiptNo;
    private String correctionReceiptNo;
    private String cancelReason;
    private String cancelledByFullName;
    private LocalDateTime cancelledAt;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private List<ReceiptItemDto> items = new ArrayList<>();

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

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
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

    public String getIssuedByFullName() {
        return issuedByFullName;
    }

    public void setIssuedByFullName(String issuedByFullName) {
        this.issuedByFullName = issuedByFullName;
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

    public String getCorrectedFromReceiptNo() {
        return correctedFromReceiptNo;
    }

    public void setCorrectedFromReceiptNo(String correctedFromReceiptNo) {
        this.correctedFromReceiptNo = correctedFromReceiptNo;
    }

    public String getCorrectionReceiptNo() {
        return correctionReceiptNo;
    }

    public void setCorrectionReceiptNo(String correctionReceiptNo) {
        this.correctionReceiptNo = correctionReceiptNo;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public String getCancelledByFullName() {
        return cancelledByFullName;
    }

    public void setCancelledByFullName(String cancelledByFullName) {
        this.cancelledByFullName = cancelledByFullName;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
    }

    public List<ReceiptItemDto> getItems() {
        return items;
    }

    public void setItems(List<ReceiptItemDto> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }
}
