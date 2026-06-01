package com.churchmanagement.dto;

import java.time.LocalTime;

public class BackupSettingsDto {
    private String backupFolder;
    private boolean autoBackupEnabled;
    private LocalTime autoBackupTime;
    private int retentionDays = 30;
    private String mysqldumpPath;
    private String mysqlClientPath;

    public String getBackupFolder() {
        return backupFolder;
    }

    public void setBackupFolder(String backupFolder) {
        this.backupFolder = backupFolder;
    }

    public boolean isAutoBackupEnabled() {
        return autoBackupEnabled;
    }

    public void setAutoBackupEnabled(boolean autoBackupEnabled) {
        this.autoBackupEnabled = autoBackupEnabled;
    }

    public LocalTime getAutoBackupTime() {
        return autoBackupTime;
    }

    public void setAutoBackupTime(LocalTime autoBackupTime) {
        this.autoBackupTime = autoBackupTime;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public String getMysqldumpPath() {
        return mysqldumpPath;
    }

    public void setMysqldumpPath(String mysqldumpPath) {
        this.mysqldumpPath = mysqldumpPath;
    }

    public String getMysqlClientPath() {
        return mysqlClientPath;
    }

    public void setMysqlClientPath(String mysqlClientPath) {
        this.mysqlClientPath = mysqlClientPath;
    }
}
