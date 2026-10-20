package com.churchmanagement.dto;

public record OfficialSmsRecipient(long churchId, String churchCode, String churchName, String regionName,
                                   String mobileNumber, Status status) {
    public enum Status {
        OK,
        MISSING,
        INVALID
    }

    public boolean isSendable() {
        return status == Status.OK;
    }
}
