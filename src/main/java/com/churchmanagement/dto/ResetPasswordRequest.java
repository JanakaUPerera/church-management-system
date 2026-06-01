package com.churchmanagement.dto;

public class ResetPasswordRequest {
    private String temporaryPassword;

    public ResetPasswordRequest(String temporaryPassword) {
        this.temporaryPassword = temporaryPassword;
    }

    public String getTemporaryPassword() {
        return temporaryPassword;
    }
}
