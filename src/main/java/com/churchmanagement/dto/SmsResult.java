package com.churchmanagement.dto;

import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.enums.SmsSendStatus;

import java.time.LocalDateTime;

public class SmsResult {
    private final boolean success;
    private final String message;
    private final String provider;
    private final LocalDateTime sentAt;
    private final SmsSendStatus sendStatus;
    private final SmsDeliveryStatus deliveryStatus;
    private final String modemMessageReference;
    private final String modemRawResponse;
    private final String errorCode;
    private final String errorMessage;
    private final int attemptCount;

    public SmsResult(boolean success, String message, String provider, LocalDateTime sentAt) {
        this(success, message, provider, sentAt,
                success ? SmsSendStatus.SENT : SmsSendStatus.FAILED,
                success ? SmsDeliveryStatus.UNKNOWN : SmsDeliveryStatus.FAILED,
                null, null, null, success ? null : message);
    }

    public SmsResult(boolean success, String message, String provider, LocalDateTime sentAt,
                     SmsSendStatus sendStatus, SmsDeliveryStatus deliveryStatus, String modemMessageReference,
                     String modemRawResponse, String errorCode, String errorMessage) {
        this(success, message, provider, sentAt, sendStatus, deliveryStatus, modemMessageReference, modemRawResponse,
                errorCode, errorMessage, 1);
    }

    public SmsResult(boolean success, String message, String provider, LocalDateTime sentAt,
                     SmsSendStatus sendStatus, SmsDeliveryStatus deliveryStatus, String modemMessageReference,
                     String modemRawResponse, String errorCode, String errorMessage, int attemptCount) {
        this.success = success;
        this.message = message;
        this.provider = provider;
        this.sentAt = sentAt;
        this.sendStatus = sendStatus;
        this.deliveryStatus = deliveryStatus;
        this.modemMessageReference = modemMessageReference;
        this.modemRawResponse = modemRawResponse;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.attemptCount = Math.max(1, attemptCount);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getProvider() {
        return provider;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public SmsSendStatus getSendStatus() {
        return sendStatus;
    }

    public SmsDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public String getModemMessageReference() {
        return modemMessageReference;
    }

    public String getModemRawResponse() {
        return modemRawResponse;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public SmsResult withAttemptCount(int attemptCount) {
        return new SmsResult(success, message, provider, sentAt, sendStatus, deliveryStatus,
                modemMessageReference, modemRawResponse, errorCode, errorMessage, attemptCount);
    }
}
