package com.churchmanagement.dto;

import java.time.LocalDateTime;

public class SmsLogDto {
    private Long id;
    private String smsLogUuid;
    private Long receiptId;
    private Long churchId;
    private String receiptNo;
    private String churchCode;
    private String churchName;
    private String mobileNumber;
    private String message;
    private String provider;
    private String status;
    private String sendStatus;
    private String deliveryStatus;
    private String modemMessageReference;
    private String modemRawResponse;
    private String deliveryReportRaw;
    private String errorMessage;
    private String errorCode;
    private int attemptCount = 1;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private Long resendOfSmsLogId;
    private String resendOfSmsLogUuid;
    private String resentByUserFullName;
    private String resendReason;
    private boolean canResend;
    private String resendDisabledReason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSmsLogUuid() {
        return smsLogUuid;
    }

    public void setSmsLogUuid(String smsLogUuid) {
        this.smsLogUuid = smsLogUuid;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(Long receiptId) {
        this.receiptId = receiptId;
    }

    public Long getChurchId() {
        return churchId;
    }

    public void setChurchId(Long churchId) {
        this.churchId = churchId;
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

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.sendStatus = status;
    }

    public String getSendStatus() {
        return sendStatus == null ? status : sendStatus;
    }

    public void setSendStatus(String sendStatus) {
        this.sendStatus = sendStatus;
        this.status = sendStatus;
    }

    public String getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public String getModemMessageReference() {
        return modemMessageReference;
    }

    public void setModemMessageReference(String modemMessageReference) {
        this.modemMessageReference = modemMessageReference;
    }

    public String getModemRawResponse() {
        return modemRawResponse;
    }

    public void setModemRawResponse(String modemRawResponse) {
        this.modemRawResponse = modemRawResponse;
    }

    public String getDeliveryReportRaw() {
        return deliveryReportRaw;
    }

    public void setDeliveryReportRaw(String deliveryReportRaw) {
        this.deliveryReportRaw = deliveryReportRaw;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(LocalDateTime lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getResendOfSmsLogId() {
        return resendOfSmsLogId;
    }

    public void setResendOfSmsLogId(Long resendOfSmsLogId) {
        this.resendOfSmsLogId = resendOfSmsLogId;
    }

    public String getResendOfSmsLogUuid() {
        return resendOfSmsLogUuid;
    }

    public void setResendOfSmsLogUuid(String resendOfSmsLogUuid) {
        this.resendOfSmsLogUuid = resendOfSmsLogUuid;
    }

    public String getResentByUserFullName() {
        return resentByUserFullName;
    }

    public void setResentByUserFullName(String resentByUserFullName) {
        this.resentByUserFullName = resentByUserFullName;
    }

    public String getResendReason() {
        return resendReason;
    }

    public void setResendReason(String resendReason) {
        this.resendReason = resendReason;
    }

    public boolean isCanResend() {
        return canResend;
    }

    public void setCanResend(boolean canResend) {
        this.canResend = canResend;
    }

    public String getResendDisabledReason() {
        return resendDisabledReason;
    }

    public void setResendDisabledReason(String resendDisabledReason) {
        this.resendDisabledReason = resendDisabledReason;
    }
}
