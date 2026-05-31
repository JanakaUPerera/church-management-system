package com.churchmanagement.dto;

public class SmsResendRequest {
    private Long smsLogId;
    private String resendReason;

    public Long getSmsLogId() {
        return smsLogId;
    }

    public void setSmsLogId(Long smsLogId) {
        this.smsLogId = smsLogId;
    }

    public String getResendReason() {
        return resendReason;
    }

    public void setResendReason(String resendReason) {
        this.resendReason = resendReason;
    }
}
