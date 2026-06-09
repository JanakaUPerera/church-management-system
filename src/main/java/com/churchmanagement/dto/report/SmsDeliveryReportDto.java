package com.churchmanagement.dto.report;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

public class SmsDeliveryReportDto extends AbstractReportRow {
    private Long smsLogId;
    private String receiptNo;
    private String churchName;
    private String mobileNumber;
    private String sendStatus;
    private String deliveryStatus;
    private int retryCount;
    private String modemReference;
    private LocalDateTime createdAt;

    @Override
    public LinkedHashMap<String, Object> columns() {
        return columns("Receipt No", text(receiptNo), "Church", text(churchName), "Mobile", text(mobileNumber),
                "Send Status", text(sendStatus), "Delivery Status", text(deliveryStatus), "Retry Count", retryCount,
                "Modem Ref", text(modemReference), "Created At", dateTime(createdAt));
    }

    @Override
    public Long detailId() { return smsLogId; }
    public Long getSmsLogId() { return smsLogId; }
    public void setSmsLogId(Long smsLogId) { this.smsLogId = smsLogId; }
    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }
    public String getChurchName() { return churchName; }
    public void setChurchName(String churchName) { this.churchName = churchName; }
    public String getMobileNumber() { return mobileNumber; }
    public void setMobileNumber(String mobileNumber) { this.mobileNumber = mobileNumber; }
    public String getSendStatus() { return sendStatus; }
    public void setSendStatus(String sendStatus) { this.sendStatus = sendStatus; }
    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public String getModemReference() { return modemReference; }
    public void setModemReference(String modemReference) { this.modemReference = modemReference; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
