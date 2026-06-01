package com.churchmanagement.service;

import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.repository.BackupSettingsRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

public class BackupSettingsService {
    private final BackupSettingsRepository backupSettingsRepository;
    private final BackupFileService backupFileService;
    private final ActivityLogService activityLogService;

    public BackupSettingsService() {
        this(new BackupSettingsRepository(), new BackupFileService(), new ActivityLogService());
    }

    public BackupSettingsService(BackupSettingsRepository backupSettingsRepository, BackupFileService backupFileService,
                                 ActivityLogService activityLogService) {
        this.backupSettingsRepository = backupSettingsRepository;
        this.backupFileService = backupFileService;
        this.activityLogService = activityLogService;
    }

    public BackupSettingsDto getSettings() {
        return backupSettingsRepository.getSettings();
    }

    public BackupSettingsDto updateSettings(BackupSettingsDto settings) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new BackupSettingsException("You do not have permission to manage backup settings."));
        try {
            new PermissionGuard(currentUser).require("backup.settings.manage");
        } catch (SecurityException exception) {
            throw new BackupSettingsException("You do not have permission to manage backup settings.", exception);
        }
        validate(settings);
        BackupSettingsDto saved = backupSettingsRepository.updateSettings(settings);
        activityLogService.logBackupSettingsUpdated(currentUser.getUserId(), saved.getBackupFolder(), saved.isAutoBackupEnabled());
        return saved;
    }

    public void validate(BackupSettingsDto settings) {
        if (settings == null || settings.getBackupFolder() == null || settings.getBackupFolder().isBlank()) {
            throw new BackupSettingsException("Backup folder is required.");
        }
        if (settings.getRetentionDays() <= 0) {
            throw new BackupSettingsException("Retention days must be greater than zero.");
        }
        backupFileService.ensureBackupFolder(settings.getBackupFolder());
    }

    public static class BackupSettingsException extends RuntimeException {
        public BackupSettingsException(String message) {
            super(message);
        }

        public BackupSettingsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
