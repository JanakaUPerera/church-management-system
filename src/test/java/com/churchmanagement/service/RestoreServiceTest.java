package com.churchmanagement.service;

import com.churchmanagement.dto.BackupLogDto;
import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.dto.RestoreLogDto;
import com.churchmanagement.dto.RestoreRequest;
import com.churchmanagement.enums.BackupStatus;
import com.churchmanagement.enums.BackupType;
import com.churchmanagement.enums.RestoreStatus;
import com.churchmanagement.repository.BackupRepository;
import com.churchmanagement.repository.BackupSettingsRepository;
import com.churchmanagement.repository.RestoreRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestoreServiceTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        AuthContext.setCurrentUser(new AuthenticatedUser(7L, "user", "User", 2L,
                "User", List.of("backup.restore")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void rejectsRestoreWithoutPermission() throws Exception {
        AuthContext.setCurrentUser(new AuthenticatedUser(8L, "user", "User", 2L,
                "User", List.of("backup.view")));
        RestoreService service = restoreService(new SuccessfulPreRestoreBackupService(), (command, inputFile) -> 0);

        RestoreService.RestoreException exception = assertThrows(
                RestoreService.RestoreException.class,
                () -> service.restoreBackup(validRequest()));

        assertEquals("You do not have permission to restore backups.", exception.getMessage());
    }

    @Test
    void rejectsRestoreWithoutConfirmationText() {
        RestoreService service = restoreService(new SuccessfulPreRestoreBackupService(), (command, inputFile) -> 0);
        RestoreRequest request = new RestoreRequest();
        request.setBackupFilePath("missing.sql");
        request.setConfirmRestoreText("restore");

        RestoreService.RestoreException exception = assertThrows(
                RestoreService.RestoreException.class,
                () -> service.restoreBackup(request));

        assertEquals("Restore confirmation text must be RESTORE.", exception.getMessage());
    }

    @Test
    void rejectsMissingSqlFile() {
        RestoreService service = restoreService(new SuccessfulPreRestoreBackupService(), (command, inputFile) -> 0);
        RestoreRequest request = new RestoreRequest();
        request.setBackupFilePath(tempDir.resolve("missing.sql").toString());
        request.setConfirmRestoreText("RESTORE");

        RestoreService.RestoreException exception = assertThrows(
                RestoreService.RestoreException.class,
                () -> service.restoreBackup(request));

        assertEquals("Restore failed. Please check selected SQL file.", exception.getMessage());
    }

    @Test
    void blocksRestoreIfPreRestoreBackupFails() throws Exception {
        RestoreService service = restoreService(new FailingPreRestoreBackupService(), (command, inputFile) -> 0);

        RestoreService.RestoreException exception = assertThrows(
                RestoreService.RestoreException.class,
                () -> service.restoreBackup(validRequest()));

        assertEquals("Pre-restore backup failed. Restore was blocked.", exception.getMessage());
    }

    @Test
    void runsMigrationsBeforeSavingSuccessfulRestoreLog() throws Exception {
        TrackingRestoreRepository restoreRepository = new TrackingRestoreRepository();
        RestoreService service = restoreService(restoreRepository, new SuccessfulPreRestoreBackupService(),
                (command, inputFile) -> 0, () -> restoreRepository.migrationsRan = true);

        service.restoreBackup(validRequest());

        assertEquals(true, restoreRepository.migrationsRanBeforeInsert);
        assertEquals(100L, restoreRepository.savedPreRestoreBackupLogId);
    }

    private RestoreRequest validRequest() throws Exception {
        Path file = tempDir.resolve("backup.sql");
        Files.writeString(file, "select 1;");
        RestoreRequest request = new RestoreRequest();
        request.setBackupFilePath(file.toString());
        request.setConfirmRestoreText("RESTORE");
        return request;
    }

    private RestoreService restoreService(BackupService backupService, BackupService.CommandRunner commandRunner) {
        return restoreService(new RestoreRepository((DataSource) null), backupService, commandRunner, () -> {
        });
    }

    private RestoreService restoreService(RestoreRepository restoreRepository, BackupService backupService,
                                          BackupService.CommandRunner commandRunner, Runnable migrationRunner) {
        BackupSettingsDto settings = new BackupSettingsDto();
        settings.setBackupFolder(tempDir.toString());
        return new RestoreService(restoreRepository,
                new TrackingBackupRepository(),
                new FakeBackupSettingsRepository(settings),
                backupService,
                new BackupCommandBuilder(new BackupCommandBuilder.DatabaseCredentials(
                        "localhost", 3306, "church_collection", "root", "secret")),
                new FakeActivityLogService(),
                commandRunner,
                migrationRunner);
    }

    private static class SuccessfulPreRestoreBackupService extends BackupService {
        private SuccessfulPreRestoreBackupService() {
            super(new BackupRepository((DataSource) null), new FakeBackupSettingsRepository(new BackupSettingsDto()),
                    new BackupCommandBuilder(new BackupCommandBuilder.DatabaseCredentials(
                            "localhost", 3306, "church_collection", "root", "secret")),
                    new BackupFileService(), new ActivityLogService(null), (command, inputFile) -> 0);
        }

        @Override
        public BackupLogDto createPreRestoreBackup() {
            BackupLogDto log = new BackupLogDto();
            log.setId(99L);
            log.setBackupType(BackupType.PRE_RESTORE);
            log.setFileName("church_collection_pre_restore_20260601_120000.sql");
            log.setFilePath("D:/backups/church_collection_pre_restore_20260601_120000.sql");
            log.setFileSizeBytes(128L);
            log.setStatus(BackupStatus.SUCCESS);
            log.setCreatedAt(LocalDateTime.now());
            return log;
        }
    }

    private static class FailingPreRestoreBackupService extends SuccessfulPreRestoreBackupService {
        @Override
        public BackupLogDto createPreRestoreBackup() {
            throw new BackupService.BackupException("Backup failed.");
        }
    }

    private static class FakeBackupSettingsRepository extends BackupSettingsRepository {
        private final BackupSettingsDto settings;

        private FakeBackupSettingsRepository(BackupSettingsDto settings) {
            super((DataSource) null);
            this.settings = settings;
        }

        @Override
        public BackupSettingsDto getSettings() {
            return settings;
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logRestoreStarted(Long userId, String backupFilePath, Long preRestoreBackupLogId) {
        }

        @Override
        public void logRestoreSuccess(Long userId, String backupFilePath, Long preRestoreBackupLogId) {
        }

        @Override
        public void logRestoreFailed(Long userId, String backupFilePath, String reason) {
        }
    }

    private static class TrackingRestoreRepository extends RestoreRepository {
        private boolean migrationsRan;
        private boolean migrationsRanBeforeInsert;
        private Long savedPreRestoreBackupLogId;

        private TrackingRestoreRepository() {
            super((DataSource) null);
        }

        @Override
        public RestoreLogDto insertRestoreLog(String backupFileName, String backupFilePath, Long preRestoreBackupLogId,
                                              RestoreStatus status, String errorMessage, long restoredByUserId,
                                              LocalDateTime restoredAt) {
            migrationsRanBeforeInsert = migrationsRan;
            savedPreRestoreBackupLogId = preRestoreBackupLogId;
            RestoreLogDto log = new RestoreLogDto();
            log.setId(1L);
            log.setBackupFileName(backupFileName);
            log.setBackupFilePath(backupFilePath);
            log.setPreRestoreBackupLogId(preRestoreBackupLogId);
            log.setStatus(status);
            log.setErrorMessage(errorMessage);
            log.setRestoredAt(restoredAt);
            return log;
        }
    }

    private static class TrackingBackupRepository extends BackupRepository {
        private TrackingBackupRepository() {
            super((DataSource) null);
        }

        @Override
        public BackupLogDto insertBackupLog(BackupType backupType, String fileName, String filePath, Long fileSizeBytes,
                                            BackupStatus status, String errorMessage, Long createdByUserId,
                                            LocalDateTime createdAt) {
            BackupLogDto log = new BackupLogDto();
            log.setId(100L);
            log.setBackupType(backupType);
            log.setFileName(fileName);
            log.setFilePath(filePath);
            log.setFileSizeBytes(fileSizeBytes);
            log.setStatus(status);
            log.setErrorMessage(errorMessage);
            log.setCreatedAt(createdAt);
            return log;
        }
    }
}
