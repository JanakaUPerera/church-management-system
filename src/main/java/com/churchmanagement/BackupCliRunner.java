package com.churchmanagement;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.config.MigrationRunner;
import com.churchmanagement.config.PrimaryMachine;
import com.churchmanagement.dto.BackupLogDto;
import com.churchmanagement.enums.BackupStatus;
import com.churchmanagement.service.BackupService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class BackupCliRunner {
    private static final String AUTO_BACKUP_ARGUMENT = "--backup-auto";
    private static final Path LOG_FILE = Path.of("logs", "auto-backup.log");

    private final BackupService backupService;

    public BackupCliRunner() {
        this(new BackupService());
    }

    public BackupCliRunner(BackupService backupService) {
        this.backupService = backupService;
    }

    public static boolean isAutoBackupCommand(String[] args) {
        return args != null && Arrays.asList(args).contains(AUTO_BACKUP_ARGUMENT);
    }

    public int runAutoBackup() {
        try {
            ensureLogFolder();
            if (!PrimaryMachine.isPrimary()) {
                info("Skipping automatic database backup — this machine is a secondary client. "
                        + "Automatic backups run only on the designated primary machine.");
                return 0;
            }
            info("Starting automatic database backup.");
            MigrationRunner.runMigrations();
            BackupLogDto log = backupService.createAutoBackup();
            if (log.getStatus() == BackupStatus.SUCCESS) {
                info("Automatic database backup completed: " + log.getFilePath());
                return 0;
            }
            error("Automatic database backup failed.");
            return 1;
        } catch (RuntimeException exception) {
            error("Automatic database backup failed: " + exception.getMessage());
            return 1;
        } finally {
            DatabaseConfig.closeDataSource();
        }
    }

    private void ensureLogFolder() {
        try {
            Files.createDirectories(LOG_FILE.getParent());
        } catch (java.io.IOException exception) {
            error("Unable to create log folder: " + exception.getMessage());
        }
    }

    private void info(String message) {
        System.out.println(message);
    }

    private void error(String message) {
        System.err.println(message);
    }
}
