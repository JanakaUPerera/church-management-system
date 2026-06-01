package com.churchmanagement.service;

import com.churchmanagement.dto.BackupSettingsDto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AutoBackupScheduler {
    private static final AutoBackupScheduler INSTANCE = new AutoBackupScheduler();

    private final BackupSettingsService backupSettingsService;
    private final BackupService backupService;
    private final ScheduledExecutorService executorService;
    private ScheduledFuture<?> scheduledBackup;

    public AutoBackupScheduler() {
        this(new BackupSettingsService(), new BackupService());
    }

    public static AutoBackupScheduler getInstance() {
        return INSTANCE;
    }

    public AutoBackupScheduler(BackupSettingsService backupSettingsService, BackupService backupService) {
        this.backupSettingsService = backupSettingsService;
        this.backupService = backupService;
        this.executorService = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "auto-backup-scheduler");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void reloadSchedule() {
        cancel();
        BackupSettingsDto settings = backupSettingsService.getSettings();
        if (!settings.isAutoBackupEnabled() || settings.getAutoBackupTime() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.with(settings.getAutoBackupTime());
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }
        long initialDelaySeconds = Duration.between(now, nextRun).toSeconds();
        scheduledBackup = executorService.scheduleAtFixedRate(
                backupService::createAutoBackup,
                initialDelaySeconds,
                Duration.ofDays(1).toSeconds(),
                TimeUnit.SECONDS
        );
    }

    public synchronized void cancel() {
        if (scheduledBackup != null) {
            scheduledBackup.cancel(false);
            scheduledBackup = null;
        }
    }
}
