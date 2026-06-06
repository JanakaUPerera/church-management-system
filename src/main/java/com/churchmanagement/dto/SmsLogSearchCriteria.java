package com.churchmanagement.dto;

import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.enums.SmsSendStatus;
import com.churchmanagement.repository.SmsLogRepository;

import java.time.LocalDate;

public class SmsLogSearchCriteria {
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private Long churchId;
    private SmsSendStatus sendStatus;
    private SmsDeliveryStatus deliveryStatus;
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

    public SmsSendStatus getStatus() {
        return sendStatus;
    }

    public void setStatus(SmsSendStatus status) {
        this.sendStatus = status;
    }

    public void setStatus(SmsLogRepository.SmsStatus status) {
        if (status == null) {
            this.sendStatus = null;
        } else if (status == SmsLogRepository.SmsStatus.SUCCESS) {
            this.sendStatus = SmsSendStatus.SENT;
        } else {
            this.sendStatus = SmsSendStatus.valueOf(status.name());
        }
    }

    public SmsSendStatus getSendStatus() {
        return sendStatus;
    }

    public void setSendStatus(SmsSendStatus sendStatus) {
        this.sendStatus = sendStatus;
    }

    public SmsDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(SmsDeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
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
