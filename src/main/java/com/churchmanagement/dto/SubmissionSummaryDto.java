package com.churchmanagement.dto;

import java.math.BigDecimal;

public class SubmissionSummaryDto {
    private long submittedChurches;
    private long pendingChurches;
    private long cancelledReceipts;
    private long totalChurches;
    private long lateSubmissions;
    private BigDecimal totalCollectionAmount = BigDecimal.ZERO;
    private long smsFailedCount;
    private long unprintedReceiptsCount;

    public long getSubmittedChurches() {
        return submittedChurches;
    }

    public void setSubmittedChurches(long submittedChurches) {
        this.submittedChurches = submittedChurches;
    }

    public long getPendingChurches() {
        return pendingChurches;
    }

    public void setPendingChurches(long pendingChurches) {
        this.pendingChurches = pendingChurches;
    }

    public long getCancelledReceipts() {
        return cancelledReceipts;
    }

    public void setCancelledReceipts(long cancelledReceipts) {
        this.cancelledReceipts = cancelledReceipts;
    }

    public long getTotalChurches() {
        return totalChurches;
    }

    public void setTotalChurches(long totalChurches) {
        this.totalChurches = totalChurches;
    }

    public long getLateSubmissions() {
        return lateSubmissions;
    }

    public void setLateSubmissions(long lateSubmissions) {
        this.lateSubmissions = lateSubmissions;
    }

    public BigDecimal getTotalCollectionAmount() {
        return totalCollectionAmount;
    }

    public void setTotalCollectionAmount(BigDecimal totalCollectionAmount) {
        this.totalCollectionAmount = totalCollectionAmount == null ? BigDecimal.ZERO : totalCollectionAmount;
    }

    public long getSmsFailedCount() {
        return smsFailedCount;
    }

    public void setSmsFailedCount(long smsFailedCount) {
        this.smsFailedCount = smsFailedCount;
    }

    public long getUnprintedReceiptsCount() {
        return unprintedReceiptsCount;
    }

    public void setUnprintedReceiptsCount(long unprintedReceiptsCount) {
        this.unprintedReceiptsCount = unprintedReceiptsCount;
    }

    public double getProgress() {
        return totalChurches == 0 ? 0 : (double) submittedChurches / totalChurches;
    }
}
