package com.churchmanagement.service;

import com.churchmanagement.dto.BackupLogDto;
import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.dto.RestoreRequest;
import com.churchmanagement.enums.BackupStatus;
import com.churchmanagement.enums.BackupType;
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

    private RestoreRequest validRequest() throws Exception {
        Path file = tempDir.resolve("backup.sql");
        Files.writeString(file, "select 1;");
        RestoreRequest request = new RestoreRequest();
        request.setBackupFilePath(file.toString());
        request.setConfirmRestoreText("RESTORE");
        return request;
    }

    private RestoreService restoreService(BackupService backupService, BackupService.CommandRunner commandRunner) {
        BackupSettingsDto settings = new BackupSettingsDto();
        settings.setBackupFolder(tempDir.toString());
        return new RestoreService(new RestoreRepository((DataSource) null),
                new FakeBackupSettingsRepository(settings),
                backupService,
                new BackupCommandBuilder(new BackupCommandBuilder.DatabaseCredentials(
                        "localhost", 3306, "church_collection", "root", "secret")),
                new ActivityLogService(null),
                commandRunner);
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
}
