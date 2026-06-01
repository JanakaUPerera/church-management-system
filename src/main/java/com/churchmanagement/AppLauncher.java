package com.churchmanagement;

public final class AppLauncher {
    private AppLauncher() {
    }

    public static void main(String[] args) {
        if (BackupCliRunner.isAutoBackupCommand(args)) {
            System.exit(new BackupCliRunner().runAutoBackup());
        }
        MainApplication.launchUi(args);
    }
}
