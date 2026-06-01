package com.churchmanagement.dto;

import com.churchmanagement.enums.RestoreStatus;

import java.time.LocalDateTime;

public class RestoreLogDto {
    private Long id;
    private String backupFileName;
    private String backupFilePath;
    private Long preRestoreBackupLogId;
    private RestoreStatus status;
    private String errorMessage;
    private String restoredByFullName;
    private LocalDateTime restoredAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBackupFileName() {
        return backupFileName;
    }

    public void setBackupFileName(String backupFileName) {
        this.backupFileName = backupFileName;
    }

    public String getBackupFilePath() {
        return backupFilePath;
    }

    public void setBackupFilePath(String backupFilePath) {
        this.backupFilePath = backupFilePath;
    }

    public Long getPreRestoreBackupLogId() {
        return preRestoreBackupLogId;
    }

    public void setPreRestoreBackupLogId(Long preRestoreBackupLogId) {
        this.preRestoreBackupLogId = preRestoreBackupLogId;
    }

    public RestoreStatus getStatus() {
        return status;
    }

    public void setStatus(RestoreStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRestoredByFullName() {
        return restoredByFullName;
    }

    public void setRestoredByFullName(String restoredByFullName) {
        this.restoredByFullName = restoredByFullName;
    }

    public LocalDateTime getRestoredAt() {
        return restoredAt;
    }

    public void setRestoredAt(LocalDateTime restoredAt) {
        this.restoredAt = restoredAt;
    }
}
