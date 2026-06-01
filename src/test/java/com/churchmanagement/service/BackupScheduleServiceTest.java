package com.churchmanagement.service;

import com.churchmanagement.dto.BackupScheduleDto;
import com.churchmanagement.repository.BackupScheduleRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackupScheduleServiceTest {
    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void duplicateTimesAllowed() {
        AuthContext.setCurrentUser(settingsManager());
        FakeBackupScheduleRepository repository = new FakeBackupScheduleRepository();
        BackupScheduleService service = new BackupScheduleService(repository);

        service.addSchedule(schedule("Morning A", LocalTime.of(8, 0)));
        service.addSchedule(schedule("Morning B", LocalTime.of(8, 0)));

        assertEquals(2, repository.schedules.size());
        assertEquals(LocalTime.of(8, 0), repository.schedules.get(0).getBackupTime());
        assertEquals(LocalTime.of(8, 0), repository.schedules.get(1).getBackupTime());
    }

    @Test
    void deletesSelectedSchedule() {
        AuthContext.setCurrentUser(settingsManager());
        FakeBackupScheduleRepository repository = new FakeBackupScheduleRepository();
        BackupScheduleService service = new BackupScheduleService(repository);
        BackupScheduleDto saved = service.addSchedule(schedule("Morning", LocalTime.of(8, 0)));

        service.deleteSchedule(saved);

        assertEquals(0, repository.schedules.size());
    }

    private BackupScheduleDto schedule(String name, LocalTime time) {
        BackupScheduleDto schedule = new BackupScheduleDto();
        schedule.setScheduleName(name);
        schedule.setBackupTime(time);
        schedule.setEnabled(true);
        return schedule;
    }

    private AuthenticatedUser settingsManager() {
        return new AuthenticatedUser(1L, "admin", "Admin", 1L, "Admin",
                List.of("backup.settings.manage"));
    }

    private static class FakeBackupScheduleRepository extends BackupScheduleRepository {
        private final List<BackupScheduleDto> schedules = new ArrayList<>();

        private FakeBackupScheduleRepository() {
            super((DataSource) null);
        }

        @Override
        public BackupScheduleDto insert(BackupScheduleDto schedule) {
            schedule.setId((long) schedules.size() + 1);
            schedules.add(schedule);
            return schedule;
        }

        @Override
        public void deleteById(long id) {
            schedules.removeIf(schedule -> schedule.getId() == id);
        }
    }
}
