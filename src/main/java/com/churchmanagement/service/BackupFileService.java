package com.churchmanagement.service;

import com.churchmanagement.enums.BackupType;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BackupFileService {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private final Clock clock;

    public BackupFileService() {
        this(Clock.systemDefaultZone());
    }

    public BackupFileService(Clock clock) {
        this.clock = clock;
    }

    public Path ensureBackupFolder(String backupFolder) {
        if (backupFolder == null || backupFolder.isBlank()) {
            throw new BackupService.BackupException("Backup folder is required.");
        }
        Path folder = Path.of(backupFolder.strip());
        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            throw new BackupService.BackupException("Backup folder cannot be created.", exception);
        }
        if (!Files.isDirectory(folder)) {
            throw new BackupService.BackupException("Backup folder cannot be created.");
        }
        return folder;
    }

    public String buildFileName(BackupType backupType) {
        String type = switch (backupType) {
            case MANUAL -> "manual";
            case AUTO -> "auto";
            case PRE_RESTORE -> "pre_restore";
        };
        return "church_collection_" + type + "_" + LocalDateTime.now(clock).format(FILE_TIMESTAMP) + ".sql";
    }

    public long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return 0;
        }
    }

    public boolean existsWithContent(Path path) {
        return Files.exists(path) && fileSize(path) > 0;
    }

    public int deleteOldBackups(int retentionDays, String backupFolder) {
        if (retentionDays <= 0 || backupFolder == null || backupFolder.isBlank()) {
            return 0;
        }
        Path folder = Path.of(backupFolder.strip());
        if (!Files.isDirectory(folder)) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        int deleted = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "church_collection_*.sql")) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)
                        && Files.getLastModifiedTime(file).toInstant().isBefore(cutoff.atZone(clock.getZone()).toInstant())) {
                    Files.deleteIfExists(file);
                    deleted++;
                }
            }
        } catch (IOException exception) {
            throw new BackupService.BackupException("Unable to clean old backup files.", exception);
        }
        return deleted;
    }
}
