package com.churchmanagement.service;

import com.churchmanagement.dto.BackupScheduleDto;
import com.churchmanagement.repository.BackupScheduleRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

import java.util.List;

public class BackupScheduleService {
    private final BackupScheduleRepository backupScheduleRepository;

    public BackupScheduleService() {
        this(new BackupScheduleRepository());
    }

    public BackupScheduleService(BackupScheduleRepository backupScheduleRepository) {
        this.backupScheduleRepository = backupScheduleRepository;
    }

    public List<BackupScheduleDto> getSchedules() {
        return backupScheduleRepository.findAll();
    }

    public List<BackupScheduleDto> getEnabledSchedules() {
        return backupScheduleRepository.findEnabled();
    }

    public BackupScheduleDto addSchedule(BackupScheduleDto schedule) {
        requireSettingsManager();
        validate(schedule);
        return backupScheduleRepository.insert(schedule);
    }

    public BackupScheduleDto updateSchedule(BackupScheduleDto schedule) {
        requireSettingsManager();
        if (schedule.getId() == null) {
            throw new BackupScheduleException("Select a schedule to update.");
        }
        validate(schedule);
        return backupScheduleRepository.update(schedule);
    }

    public BackupScheduleDto setScheduleEnabled(BackupScheduleDto schedule, boolean enabled) {
        requireSettingsManager();
        if (schedule == null || schedule.getId() == null) {
            throw new BackupScheduleException("Select a schedule to update.");
        }
        return backupScheduleRepository.setEnabled(schedule.getId(), enabled);
    }

    public void deleteSchedule(BackupScheduleDto schedule) {
        requireSettingsManager();
        if (schedule == null || schedule.getId() == null) {
            throw new BackupScheduleException("Select a schedule to delete.");
        }
        backupScheduleRepository.deleteById(schedule.getId());
    }

    private void validate(BackupScheduleDto schedule) {
        if (schedule == null || schedule.getScheduleName() == null || schedule.getScheduleName().isBlank()) {
            throw new BackupScheduleException("Schedule name is required.");
        }
        if (schedule.getBackupTime() == null) {
            throw new BackupScheduleException("Backup time is required.");
        }
    }

    private AuthenticatedUser requireSettingsManager() {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new BackupScheduleException(
                        "You do not have permission to manage backup settings."));
        try {
            new PermissionGuard(currentUser).require("backup.settings.manage");
        } catch (SecurityException exception) {
            throw new BackupScheduleException("You do not have permission to manage backup settings.", exception);
        }
        return currentUser;
    }

    public static class BackupScheduleException extends RuntimeException {
        public BackupScheduleException(String message) {
            super(message);
        }

        public BackupScheduleException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
