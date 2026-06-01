package com.churchmanagement.dto;

public class RestoreRequest {
    private String backupFilePath;
    private String confirmRestoreText;

    public String getBackupFilePath() {
        return backupFilePath;
    }

    public void setBackupFilePath(String backupFilePath) {
        this.backupFilePath = backupFilePath;
    }

    public String getConfirmRestoreText() {
        return confirmRestoreText;
    }

    public void setConfirmRestoreText(String confirmRestoreText) {
        this.confirmRestoreText = confirmRestoreText;
    }
}
