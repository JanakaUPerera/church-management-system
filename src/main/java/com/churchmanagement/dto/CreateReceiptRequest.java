package com.churchmanagement.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CreateReceiptRequest {
    private Long churchId;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private String submittedByName;
    private String lateSubmissionReason;
    private Long correctedFromReceiptId;
    private List<ReceiptItemDto> items = new ArrayList<>();

    public Long getChurchId() {
        return churchId;
    }

    public void setChurchId(Long churchId) {
        this.churchId = churchId;
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

    public String getSubmittedByName() {
        return submittedByName;
    }

    public void setSubmittedByName(String submittedByName) {
        this.submittedByName = submittedByName;
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

    public List<ReceiptItemDto> getItems() {
        return items;
    }

    public void setItems(List<ReceiptItemDto> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }
}
