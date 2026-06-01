package com.churchmanagement.service;

import com.churchmanagement.enums.BackupType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupFileServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-31T10:30:00Z"), ZoneId.of("UTC"));
    private final BackupFileService service = new BackupFileService(clock);

    @TempDir
    Path tempDir;

    @Test
    void createsBackupFolder() {
        Path folder = tempDir.resolve("nested").resolve("backups");

        Path created = service.ensureBackupFolder(folder.toString());

        assertTrue(Files.isDirectory(created));
    }

    @Test
    void detectsFileSize() throws Exception {
        Path file = tempDir.resolve("backup.sql");
        Files.writeString(file, "data");

        assertEquals(4, service.fileSize(file));
        assertTrue(service.existsWithContent(file));
    }

    @Test
    void buildsExpectedBackupFileName() {
        assertEquals("church_collection_manual_20260531_103000.sql", service.buildFileName(BackupType.MANUAL));
    }

    @Test
    void deletesOnlyOldChurchCollectionSqlFiles() throws Exception {
        Path oldBackup = tempDir.resolve("church_collection_manual_old.sql");
        Path newBackup = tempDir.resolve("church_collection_auto_new.sql");
        Path unrelated = tempDir.resolve("notes.sql");
        Files.writeString(oldBackup, "old");
        Files.writeString(newBackup, "new");
        Files.writeString(unrelated, "keep");
        Files.setLastModifiedTime(oldBackup,
                java.nio.file.attribute.FileTime.from(clock.instant().minus(40, ChronoUnit.DAYS)));
        Files.setLastModifiedTime(newBackup,
                java.nio.file.attribute.FileTime.from(clock.instant().minus(1, ChronoUnit.DAYS)));
        Files.setLastModifiedTime(unrelated,
                java.nio.file.attribute.FileTime.from(clock.instant().minus(40, ChronoUnit.DAYS)));

        int deleted = service.deleteOldBackups(30, tempDir.toString());

        assertEquals(1, deleted);
        assertFalse(Files.exists(oldBackup));
        assertTrue(Files.exists(newBackup));
        assertTrue(Files.exists(unrelated));
    }
}
