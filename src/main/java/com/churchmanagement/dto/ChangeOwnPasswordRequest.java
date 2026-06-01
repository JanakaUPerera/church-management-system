package com.churchmanagement.dto;

public class ChangeOwnPasswordRequest {
    private final String currentPassword;
    private final String newPassword;
    private final String confirmNewPassword;

    public ChangeOwnPasswordRequest(String currentPassword, String newPassword, String confirmNewPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.confirmNewPassword = confirmNewPassword;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public String getConfirmNewPassword() {
        return confirmNewPassword;
    }
}
