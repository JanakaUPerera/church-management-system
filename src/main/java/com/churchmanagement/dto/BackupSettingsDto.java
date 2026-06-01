package com.churchmanagement.dto;

public class BackupSettingsDto {
    private String backupFolder;
    private int retentionDays = 30;
    private String mysqldumpPath;
    private String mysqlClientPath;

    public String getBackupFolder() {
        return backupFolder;
    }

    public void setBackupFolder(String backupFolder) {
        this.backupFolder = backupFolder;
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
