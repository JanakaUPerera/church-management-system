package com.churchmanagement.service;

import com.churchmanagement.dto.BackupLogDto;
import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.enums.BackupStatus;
import com.churchmanagement.enums.BackupType;
import com.churchmanagement.repository.BackupRepository;
import com.churchmanagement.repository.BackupSettingsRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class BackupService {
    private final BackupRepository backupRepository;
    private final BackupSettingsRepository backupSettingsRepository;
    private final BackupCommandBuilder commandBuilder;
    private final BackupFileService backupFileService;
    private final ActivityLogService activityLogService;
    private final CommandRunner commandRunner;

    public BackupService() {
        this(new BackupRepository(), new BackupSettingsRepository(), new BackupCommandBuilder(),
                new BackupFileService(), new ActivityLogService(), new ProcessCommandRunner());
    }

    public BackupService(BackupRepository backupRepository, BackupSettingsRepository backupSettingsRepository,
                         BackupCommandBuilder commandBuilder, BackupFileService backupFileService,
                         ActivityLogService activityLogService, CommandRunner commandRunner) {
        this.backupRepository = backupRepository;
        this.backupSettingsRepository = backupSettingsRepository;
        this.commandBuilder = commandBuilder;
        this.backupFileService = backupFileService;
        this.activityLogService = activityLogService;
        this.commandRunner = commandRunner;
    }

    public BackupLogDto createManualBackup(String selectedFolder) {
        AuthenticatedUser currentUser = requireUser();
        try {
            new PermissionGuard(currentUser).require("backup.create");
        } catch (SecurityException exception) {
            throw new BackupException("You do not have permission to create backups.", exception);
        }
        return createBackup(BackupType.MANUAL, selectedFolder, currentUser);
    }

    public BackupLogDto createAutoBackup() {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser().orElse(null);
        BackupSettingsDto settings = backupSettingsRepository.getSettings();
        BackupLogDto log = createBackup(BackupType.AUTO, settings.getBackupFolder(), currentUser);
        if (log.getStatus() == BackupStatus.SUCCESS) {
            int deleted = backupFileService.deleteOldBackups(settings.getRetentionDays(), settings.getBackupFolder());
            activityLogService.logBackupRetentionCleanup(userId(currentUser), settings.getBackupFolder(), deleted);
        }
        return log;
    }

    public BackupLogDto createPreRestoreBackup() {
        AuthenticatedUser currentUser = requireUser();
        BackupSettingsDto settings = backupSettingsRepository.getSettings();
        return createBackup(BackupType.PRE_RESTORE, settings.getBackupFolder(), currentUser);
    }

    private BackupLogDto createBackup(BackupType backupType, String selectedFolder, AuthenticatedUser currentUser) {
        BackupSettingsDto settings = backupSettingsRepository.getSettings();
        if (selectedFolder != null && !selectedFolder.isBlank()) {
            settings.setBackupFolder(selectedFolder.strip());
        }
        Path folder = backupFileService.ensureBackupFolder(settings.getBackupFolder());
        String fileName = backupFileService.buildFileName(backupType);
        Path backupFile = folder.resolve(fileName);
        try {
            BackupCommandBuilder.CommandSpec command = commandBuilder.buildDumpCommand(settings, backupFile);
            int exitCode = commandRunner.run(command, null);
            if (exitCode != 0 || !backupFileService.existsWithContent(backupFile)) {
                return failedBackup(backupType, fileName, backupFile, currentUser, backupFailureMessage());
            }
            long fileSize = backupFileService.fileSize(backupFile);
            BackupLogDto log = backupRepository.insertBackupLog(backupType, fileName, backupFile.toAbsolutePath().toString(),
                    fileSize, BackupStatus.SUCCESS, null, userId(currentUser), LocalDateTime.now());
            activityLogService.logBackupCreated(userId(currentUser), successAction(backupType),
                    log.getFilePath(), log.getFileSizeBytes());
            return log;
        } catch (IOException exception) {
            String message = commandNotFoundMessage(backupType, exception, "mysqldump command not found.");
            return failedBackup(backupType, fileName, backupFile, currentUser, message);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failedBackup(backupType, fileName, backupFile, currentUser,
                    "Backup failed. Please check database credentials and tool paths.");
        }
    }

    /**
     * Builds the failure message for a non-zero mysqldump exit (or an empty
     * output file), including the real tool error when the command runner
     * captured one — e.g. "Backup failed: mysqldump: Couldn't execute 'FLUSH
     * ... TABLES': Access denied; you need ... the RELOAD or FLUSH_TABLES
     * privilege(s)" — instead of a generic message that gives no clue why.
     */
    private String backupFailureMessage() {
        String detail = capturedErrorDetail();
        return detail == null
                ? "Backup failed. Please check database credentials and tool paths."
                : "Backup failed: " + detail;
    }

    /** Condenses captured stderr into one length-capped line suitable for the UI/activity log. */
    private String capturedErrorDetail() {
        String stderr = commandRunner.lastErrorOutput();
        if (stderr == null || stderr.isBlank()) {
            return null;
        }
        String condensed = stderr.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .reduce((first, second) -> first + " | " + second)
                .orElse(null);
        if (condensed == null) {
            return null;
        }
        return condensed.length() > 400 ? condensed.substring(0, 400) + "…" : condensed;
    }

    private BackupLogDto failedBackup(BackupType backupType, String fileName, Path backupFile,
                                      AuthenticatedUser currentUser, String message) {
        BackupLogDto log = backupRepository.insertBackupLog(backupType, fileName, backupFile.toAbsolutePath().toString(),
                backupFileService.fileSize(backupFile), BackupStatus.FAILED, message, userId(currentUser),
                LocalDateTime.now());
        activityLogService.logBackupFailed(userId(currentUser), failureAction(backupType), log.getFilePath(), message);
        throw new BackupException(message);
    }

    private Long userId(AuthenticatedUser user) {
        return user == null ? null : user.getUserId();
    }

    private AuthenticatedUser requireUser() {
        return AuthContext.getCurrentUser()
                .orElseThrow(() -> new BackupException("You do not have permission to create backups."));
    }

    private String successAction(BackupType backupType) {
        return switch (backupType) {
            case MANUAL -> ActivityLogService.BACKUP_CREATED;
            case AUTO -> ActivityLogService.AUTO_BACKUP_CREATED;
            case PRE_RESTORE -> ActivityLogService.PRE_RESTORE_BACKUP_CREATED;
        };
    }

    private String failureAction(BackupType backupType) {
        return switch (backupType) {
            case MANUAL -> ActivityLogService.BACKUP_FAILED;
            case AUTO -> ActivityLogService.AUTO_BACKUP_FAILED;
            case PRE_RESTORE -> ActivityLogService.PRE_RESTORE_BACKUP_FAILED;
        };
    }

    private String commandNotFoundMessage(BackupType backupType, IOException exception, String fallback) {
        String message = exception.getMessage();
        if (message != null && message.toLowerCase().contains("cannot run program")) {
            return fallback;
        }
        return backupType == BackupType.PRE_RESTORE
                ? "Backup failed. Please check database credentials and tool paths."
                : fallback;
    }

    public interface CommandRunner {
        int run(BackupCommandBuilder.CommandSpec command, Path inputFile) throws IOException, InterruptedException;

        /**
         * Returns the stderr captured by the most recent {@link #run} call, or
         * {@code null} if none is available. Lets callers surface the real
         * mysqldump/mysql error instead of guessing from just an exit code.
         */
        default String lastErrorOutput() {
            return null;
        }
    }

    public static class ProcessCommandRunner implements CommandRunner {
        private volatile String lastErrorOutput;

        @Override
        public int run(BackupCommandBuilder.CommandSpec command, Path inputFile) throws IOException, InterruptedException {
            ProcessBuilder processBuilder = new ProcessBuilder(command.command());
            processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            if (inputFile != null) {
                processBuilder.redirectInput(inputFile.toFile());
            }
            Process process = processBuilder.start();
            // Drain stderr fully before waitFor() — the child's stderr pipe has
            // a bounded buffer, so reading it after waitFor() could deadlock if
            // the tool writes more than that buffer holds.
            lastErrorOutput = readFully(process.getErrorStream());
            return process.waitFor();
        }

        @Override
        public String lastErrorOutput() {
            return lastErrorOutput;
        }

        private String readFully(InputStream stream) throws IOException {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
        }
    }

    public static class BackupException extends RuntimeException {
        public BackupException(String message) {
            super(message);
        }

        public BackupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
