package com.churchmanagement.service;

import com.churchmanagement.dto.BackupScheduleDto;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AutoBackupScheduler {
    private static final AutoBackupScheduler INSTANCE = new AutoBackupScheduler();

    private final BackupScheduleService backupScheduleService;
    private final BackupService backupService;
    private final ScheduledExecutorService executorService;
    private final List<ScheduledFuture<?>> scheduledBackups = new ArrayList<>();

    public AutoBackupScheduler() {
        this(new BackupScheduleService(), new BackupService());
    }

    public static AutoBackupScheduler getInstance() {
        return INSTANCE;
    }

    public AutoBackupScheduler(BackupScheduleService backupScheduleService, BackupService backupService) {
        this.backupScheduleService = backupScheduleService;
        this.backupService = backupService;
        this.executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "auto-backup-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void reloadSchedule() {
        cancel();
        for (BackupScheduleDto schedule : backupScheduleService.getEnabledSchedules()) {
            scheduleDailyBackup(schedule);
        }
    }

    protected void scheduleDailyBackup(BackupScheduleDto schedule) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = LocalDateTime.of(LocalDate.now(), schedule.getBackupTime());
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }
        long initialDelaySeconds = Duration.between(now, nextRun).toSeconds();
        ScheduledFuture<?> scheduledBackup = executorService.scheduleAtFixedRate(
                backupService::createAutoBackup,
                initialDelaySeconds,
                Duration.ofDays(1).toSeconds(),
                TimeUnit.SECONDS
        );
        scheduledBackups.add(scheduledBackup);
    }

    public synchronized void cancel() {
        for (ScheduledFuture<?> scheduledBackup : scheduledBackups) {
            scheduledBackup.cancel(false);
        }
        scheduledBackups.clear();
    }
}
