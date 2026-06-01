package com.churchmanagement.dto;

public class ResetPasswordRequest {
    private String temporaryPassword;
    private boolean forcePasswordChange;

    public ResetPasswordRequest(String temporaryPassword) {
        this(temporaryPassword, true);
    }

    public ResetPasswordRequest(String temporaryPassword, boolean forcePasswordChange) {
        this.temporaryPassword = temporaryPassword;
        this.forcePasswordChange = forcePasswordChange;
    }

    public String getTemporaryPassword() {
        return temporaryPassword;
    }

    public boolean isForcePasswordChange() {
        return forcePasswordChange;
    }
}
