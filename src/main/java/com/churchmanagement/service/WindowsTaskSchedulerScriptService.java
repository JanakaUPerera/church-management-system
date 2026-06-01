package com.churchmanagement.service;

import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.dto.BackupScheduleDto;
import com.churchmanagement.repository.BackupScheduleRepository;
import com.churchmanagement.repository.BackupSettingsRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class WindowsTaskSchedulerScriptService {
    private static final String BAT_FILE_NAME = "backup-auto.bat";
    private static final String DEFAULT_JAR_FILE_NAME = "target\\church-management-system-1.0.0.jar";
    private static final DateTimeFormatter TASK_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final BackupSettingsRepository backupSettingsRepository;
    private final BackupScheduleRepository backupScheduleRepository;
    private final BackupFileService backupFileService;
    private final ActivityLogService activityLogService;
    private final Path applicationDirectory;
    private final String jarFileName;

    public WindowsTaskSchedulerScriptService() {
        this(new BackupSettingsRepository(), new BackupScheduleRepository(), new BackupFileService(), new ActivityLogService(),
                Path.of(System.getProperty("user.dir")), DEFAULT_JAR_FILE_NAME);
    }

    public WindowsTaskSchedulerScriptService(BackupSettingsRepository backupSettingsRepository,
                                             BackupScheduleRepository backupScheduleRepository,
                                             BackupFileService backupFileService,
                                             ActivityLogService activityLogService,
                                             Path applicationDirectory,
                                             String jarFileName) {
        this.backupSettingsRepository = backupSettingsRepository;
        this.backupScheduleRepository = backupScheduleRepository;
        this.backupFileService = backupFileService;
        this.activityLogService = activityLogService;
        this.applicationDirectory = applicationDirectory.toAbsolutePath().normalize();
        this.jarFileName = jarFileName;
    }

    public Path generateBackupBatScript() {
        AuthenticatedUser currentUser = requireSettingsManager();
        BackupSettingsDto settings = backupSettingsRepository.getSettings();
        backupFileService.ensureBackupFolder(settings.getBackupFolder());
        Path logsFolder = applicationDirectory.resolve("logs");
        Path scriptPath = applicationDirectory.resolve(BAT_FILE_NAME);
        try {
            Files.createDirectories(applicationDirectory);
            Files.createDirectories(logsFolder);
            Files.writeString(scriptPath, batContent());
            activityLogService.logAutoBackupScriptGenerated(currentUser.getUserId(), scriptPath.toString());
            return scriptPath;
        } catch (IOException exception) {
            throw new ScriptGenerationException("Unable to generate automatic backup script.", exception);
        }
    }

    public List<String> buildSchtasksCommands() {
        return backupScheduleRepository.findEnabled().stream()
                .map(this::buildSchtasksCommand)
                .toList();
    }

    public String buildSchtasksCommand() {
        return buildSchtasksCommands().stream().findFirst().orElse("");
    }

    private String buildSchtasksCommand(BackupScheduleDto schedule) {
        return "schtasks /Create /TN \"Church Collection Auto Backup - "
                + schedule.getScheduleName()
                + "\" /TR \"\\\""
                + applicationDirectory.resolve(BAT_FILE_NAME)
                + "\\\"\" /SC DAILY /ST "
                + schedule.getBackupTime().format(TASK_TIME_FORMAT)
                + " /RL HIGHEST /F";
    }

    private AuthenticatedUser requireSettingsManager() {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new ScriptGenerationException(
                        "You do not have permission to manage backup settings."));
        try {
            new PermissionGuard(currentUser).require("backup.settings.manage");
        } catch (SecurityException exception) {
            throw new ScriptGenerationException("You do not have permission to manage backup settings.", exception);
        }
        return currentUser;
    }

    private String batContent() {
        return String.join(System.lineSeparator(),
                "@echo off",
                "cd /d \"" + applicationDirectory + "\"",
                "java -jar \"" + jarFileName + "\" --backup-auto >> \"logs\\auto-backup.log\" 2>&1",
                "");
    }

    public static class ScriptGenerationException extends RuntimeException {
        public ScriptGenerationException(String message) {
            super(message);
        }

        public ScriptGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
