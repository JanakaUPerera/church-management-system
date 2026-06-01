package com.churchmanagement.dto;

import com.churchmanagement.enums.BackupType;

public class BackupRequest {
    private BackupType backupType;
    private String backupFolder;

    public BackupType getBackupType() {
        return backupType;
    }

    public void setBackupType(BackupType backupType) {
        this.backupType = backupType;
    }

    public String getBackupFolder() {
        return backupFolder;
    }

    public void setBackupFolder(String backupFolder) {
        this.backupFolder = backupFolder;
    }
}
