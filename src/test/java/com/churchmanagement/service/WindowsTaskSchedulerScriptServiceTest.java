package com.churchmanagement.service;

import com.churchmanagement.dto.BackupSettingsDto;
import com.churchmanagement.dto.BackupScheduleDto;
import com.churchmanagement.repository.BackupScheduleRepository;
import com.churchmanagement.repository.BackupSettingsRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsTaskSchedulerScriptServiceTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void generatedBatContainsJarCommand() throws Exception {
        AuthContext.setCurrentUser(settingsManager());
        WindowsTaskSchedulerScriptService service = service(settings("secret-password"));

        Path scriptPath = service.generateBackupBatScript();

        String content = Files.readString(scriptPath);
        assertTrue(content.contains("java -jar \"target\\church-management-system-1.0.0.jar\" --backup-auto"));
        assertTrue(Files.isDirectory(tempDir.resolve("logs")));
        assertTrue(Files.isDirectory(tempDir.resolve("backups")));
    }

    @Test
    void generatedBatDoesNotContainDbPassword() throws Exception {
        AuthContext.setCurrentUser(settingsManager());
        WindowsTaskSchedulerScriptService service = service(settings("secret-password"));

        Path scriptPath = service.generateBackupBatScript();

        assertFalse(Files.readString(scriptPath).contains("secret-password"));
    }

    @Test
    void generatedSchtasksCommandUsesSelectedTime() {
        WindowsTaskSchedulerScriptService service = service(settings("secret-password"),
                List.of(schedule("Morning", LocalTime.of(6, 5), true)));

        String command = service.buildSchtasksCommand();

        assertTrue(command.contains("/ST 06:05"));
        assertTrue(command.contains("Church Collection Auto Backup - Morning"));
        assertTrue(command.contains("backup-auto.bat"));
    }

    @Test
    void multipleTaskSchedulerCommandsGenerated() {
        WindowsTaskSchedulerScriptService service = service(settings("secret-password"),
                List.of(schedule("Morning", LocalTime.of(6, 5), true),
                        schedule("Evening", LocalTime.of(18, 0), true),
                        schedule("Disabled", LocalTime.of(23, 0), false)));

        List<String> commands = service.buildSchtasksCommands();

        assertEquals(2, commands.size());
        assertTrue(commands.get(0).contains("Church Collection Auto Backup - Morning"));
        assertTrue(commands.get(1).contains("Church Collection Auto Backup - Evening"));
    }

    @Test
    void userWithoutPermissionCannotGenerateScript() {
        AuthContext.setCurrentUser(new AuthenticatedUser(2L, "viewer", "Viewer", 2L, "Viewer", List.of()));
        WindowsTaskSchedulerScriptService service = service(settings("secret-password"));

        WindowsTaskSchedulerScriptService.ScriptGenerationException exception = assertThrows(
                WindowsTaskSchedulerScriptService.ScriptGenerationException.class,
                service::generateBackupBatScript);

        assertEquals("You do not have permission to manage backup settings.", exception.getMessage());
        assertFalse(Files.exists(tempDir.resolve("backup-auto.bat")));
    }

    private WindowsTaskSchedulerScriptService service(BackupSettingsDto settings) {
        return service(settings, List.of(schedule("Default", LocalTime.of(18, 0), true)));
    }

    private WindowsTaskSchedulerScriptService service(BackupSettingsDto settings, List<BackupScheduleDto> schedules) {
        return new WindowsTaskSchedulerScriptService(new FakeBackupSettingsRepository(settings),
                new FakeBackupScheduleRepository(schedules),
                new BackupFileService(), new FakeActivityLogService(), tempDir,
                "target\\church-management-system-1.0.0.jar");
    }

    private BackupSettingsDto settings(String passwordThatMustNotBeWritten) {
        BackupSettingsDto settings = new BackupSettingsDto();
        settings.setBackupFolder(tempDir.resolve("backups").toString());
        settings.setRetentionDays(30);
        settings.setMysqldumpPath(passwordThatMustNotBeWritten);
        return settings;
    }

    private BackupScheduleDto schedule(String name, LocalTime time, boolean enabled) {
        BackupScheduleDto schedule = new BackupScheduleDto();
        schedule.setScheduleName(name);
        schedule.setBackupTime(time);
        schedule.setEnabled(enabled);
        return schedule;
    }

    private AuthenticatedUser settingsManager() {
        return new AuthenticatedUser(1L, "admin", "Admin", 1L, "Admin",
                List.of("backup.settings.manage"));
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

    private static class FakeBackupScheduleRepository extends BackupScheduleRepository {
        private final List<BackupScheduleDto> schedules;

        private FakeBackupScheduleRepository(List<BackupScheduleDto> schedules) {
            super((DataSource) null);
            this.schedules = new ArrayList<>(schedules);
        }

        @Override
        public List<BackupScheduleDto> findEnabled() {
            return schedules.stream().filter(BackupScheduleDto::isEnabled).toList();
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logAutoBackupScriptGenerated(Long userId, String scriptPath) {
        }
    }
}
