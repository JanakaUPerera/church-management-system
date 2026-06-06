package com.churchmanagement.dto;

import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.enums.SmsSendStatus;

public class SmsParsedResponse {
    private final boolean success;
    private final SmsSendStatus sendStatus;
    private final SmsDeliveryStatus deliveryStatus;
    private final String modemMessageReference;
    private final String modemRawResponse;
    private final String errorCode;
    private final String errorMessage;

    public SmsParsedResponse(boolean success, SmsSendStatus sendStatus, SmsDeliveryStatus deliveryStatus,
                             String modemMessageReference, String modemRawResponse, String errorCode,
                             String errorMessage) {
        this.success = success;
        this.sendStatus = sendStatus;
        this.deliveryStatus = deliveryStatus;
        this.modemMessageReference = modemMessageReference;
        this.modemRawResponse = modemRawResponse;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccess() {
        return success;
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
}
