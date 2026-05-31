package com.churchmanagement.dto;

import com.churchmanagement.repository.SmsLogRepository;

import java.time.LocalDate;

public class SmsLogSearchCriteria {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Long churchId;
    private SmsLogRepository.SmsStatus status;
    private String mobileNumber;
    private String receiptNo;
    private Integer limit;

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }

    public Long getChurchId() {
        return churchId;
    }

    public void setChurchId(Long churchId) {
        this.churchId = churchId;
    }

    public SmsLogRepository.SmsStatus getStatus() {
        return status;
    }

    public void setStatus(SmsLogRepository.SmsStatus status) {
        this.status = status;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public int limitOrDefault(int defaultLimit) {
        return limit == null || limit <= 0 ? defaultLimit : limit;
    }
}
