package com.churchmanagement.service;

import com.churchmanagement.dto.BackupLogDto;
import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.dto.RestoreLogDto;
import com.churchmanagement.dto.RestoreRequest;
import com.churchmanagement.enums.BackupStatus;
import com.churchmanagement.enums.RestoreStatus;
import com.churchmanagement.repository.BackupSettingsRepository;
import com.churchmanagement.repository.RestoreRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class RestoreService {
    private final RestoreRepository restoreRepository;
    private final BackupSettingsRepository backupSettingsRepository;
    private final BackupService backupService;
    private final BackupCommandBuilder commandBuilder;
    private final ActivityLogService activityLogService;
    private final BackupService.CommandRunner commandRunner;

    public RestoreService() {
        this(new RestoreRepository(), new BackupSettingsRepository(), new BackupService(), new BackupCommandBuilder(),
                new ActivityLogService(), new BackupService.ProcessCommandRunner());
    }

    public RestoreService(RestoreRepository restoreRepository, BackupSettingsRepository backupSettingsRepository,
                          BackupService backupService, BackupCommandBuilder commandBuilder,
                          ActivityLogService activityLogService, BackupService.CommandRunner commandRunner) {
        this.restoreRepository = restoreRepository;
        this.backupSettingsRepository = backupSettingsRepository;
        this.backupService = backupService;
        this.commandBuilder = commandBuilder;
        this.activityLogService = activityLogService;
        this.commandRunner = commandRunner;
    }

    public RestoreLogDto restoreBackup(RestoreRequest request) {
        AuthenticatedUser currentUser = requireRestorePermission();
        validateRequest(request);
        Path backupFile = Path.of(request.getBackupFilePath().strip());
        BackupLogDto preRestoreBackup = createPreRestoreBackup();
        activityLogService.logRestoreStarted(currentUser.getUserId(), backupFile.toAbsolutePath().toString(),
                preRestoreBackup.getId());
        try {
            BackupSettingsDto settings = backupSettingsRepository.getSettings();
            int exitCode = commandRunner.run(commandBuilder.buildRestoreCommand(settings), backupFile);
            if (exitCode != 0) {
                return failedRestore(backupFile, preRestoreBackup.getId(), currentUser,
                        "Restore failed. Please check selected SQL file.");
            }
            RestoreLogDto log = restoreRepository.insertRestoreLog(backupFile.getFileName().toString(),
                    backupFile.toAbsolutePath().toString(), preRestoreBackup.getId(), RestoreStatus.SUCCESS,
                    null, currentUser.getUserId(), LocalDateTime.now());
            activityLogService.logRestoreSuccess(currentUser.getUserId(), log.getBackupFilePath(), preRestoreBackup.getId());
            return log;
        } catch (IOException exception) {
            String message = exception.getMessage() != null
                    && exception.getMessage().toLowerCase().contains("cannot run program")
                    ? "mysql command not found."
                    : "Restore failed. Please check selected SQL file.";
            return failedRestore(backupFile, preRestoreBackup.getId(), currentUser, message);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failedRestore(backupFile, preRestoreBackup.getId(), currentUser,
                    "Restore failed. Please check selected SQL file.");
        }
    }

    private BackupLogDto createPreRestoreBackup() {
        try {
            BackupLogDto log = backupService.createPreRestoreBackup();
            if (log.getStatus() != BackupStatus.SUCCESS) {
                throw new RestoreException("Pre-restore backup failed. Restore was blocked.");
            }
            return log;
        } catch (RuntimeException exception) {
            throw new RestoreException("Pre-restore backup failed. Restore was blocked.", exception);
        }
    }

    private RestoreLogDto failedRestore(Path backupFile, Long preRestoreBackupLogId, AuthenticatedUser currentUser,
                                        String message) {
        RestoreLogDto log = restoreRepository.insertRestoreLog(backupFile.getFileName().toString(),
                backupFile.toAbsolutePath().toString(), preRestoreBackupLogId, RestoreStatus.FAILED,
                message, currentUser.getUserId(), LocalDateTime.now());
        activityLogService.logRestoreFailed(currentUser.getUserId(), log.getBackupFilePath(), message);
        throw new RestoreException(message);
    }

    private AuthenticatedUser requireRestorePermission() {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new RestoreException("You do not have permission to restore backups."));
        try {
            new PermissionGuard(currentUser).require("backup.restore");
            return currentUser;
        } catch (SecurityException exception) {
            throw new RestoreException("You do not have permission to restore backups.", exception);
        }
    }

    private void validateRequest(RestoreRequest request) {
        if (request == null || !"RESTORE".equals(request.getConfirmRestoreText())) {
            throw new RestoreException("Restore confirmation text must be RESTORE.");
        }
        if (request.getBackupFilePath() == null || request.getBackupFilePath().isBlank()
                || !Files.isRegularFile(Path.of(request.getBackupFilePath().strip()))) {
            throw new RestoreException("Restore failed. Please check selected SQL file.");
        }
    }

    public static class RestoreException extends RuntimeException {
        public RestoreException(String message) {
            super(message);
        }

        public RestoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
