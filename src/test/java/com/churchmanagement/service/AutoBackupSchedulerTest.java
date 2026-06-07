package com.churchmanagement.service;

import com.churchmanagement.dto.BackupScheduleDto;
import com.churchmanagement.repository.BackupScheduleRepository;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoBackupSchedulerTest {
    @Test
    void multipleSchedulesLoaded() {
        CapturingScheduler scheduler = scheduler(List.of(
                schedule("Morning", LocalTime.of(6, 0), true),
                schedule("Evening", LocalTime.of(18, 0), true)));

        scheduler.reloadSchedule();

        assertEquals(List.of(LocalTime.of(6, 0), LocalTime.of(18, 0)), scheduler.scheduledTimes);
    }

    @Test
    void disabledSchedulesIgnored() {
        CapturingScheduler scheduler = scheduler(List.of(
                schedule("Morning", LocalTime.of(6, 0), true),
                schedule("Disabled", LocalTime.of(12, 0), false)));

        scheduler.reloadSchedule();

        assertEquals(List.of(LocalTime.of(6, 0)), scheduler.scheduledTimes);
    }

    private CapturingScheduler scheduler(List<BackupScheduleDto> schedules) {
        return new CapturingScheduler(new BackupScheduleService(new FakeBackupScheduleRepository(schedules)));
    }

    private BackupScheduleDto schedule(String name, LocalTime time, boolean enabled) {
        BackupScheduleDto schedule = new BackupScheduleDto();
        schedule.setScheduleName(name);
        schedule.setBackupTime(time);
        schedule.setEnabled(enabled);
        return schedule;
    }

    private static class CapturingScheduler extends AutoBackupScheduler {
        private final List<LocalTime> scheduledTimes = new ArrayList<>();

        private CapturingScheduler(BackupScheduleService backupScheduleService) {
            super(backupScheduleService, null);
        }

        @Override
        protected void scheduleDailyBackup(BackupScheduleDto schedule) {
            scheduledTimes.add(schedule.getBackupTime());
        }
    }

    private static class FakeBackupScheduleRepository extends BackupScheduleRepository {
        private final List<BackupScheduleDto> schedules;

        private FakeBackupScheduleRepository(List<BackupScheduleDto> schedules) {
            super((DataSource) null);
            this.schedules = schedules;
        }

        @Override
        public List<BackupScheduleDto> findEnabled() {
            return schedules.stream().filter(BackupScheduleDto::isEnabled).toList();
        }
    }
}
