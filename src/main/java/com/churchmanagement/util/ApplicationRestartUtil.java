package com.churchmanagement.util;

import com.churchmanagement.AppLauncher;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.service.ActivityLogService;
import com.churchmanagement.service.AutoBackupScheduler;
import com.churchmanagement.service.RestoreService;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ApplicationRestartUtil {
    private static boolean restartOnExit;

    private ApplicationRestartUtil() {
    }

    public static void logoutAndRestart() {
        logoutCurrentUser();
        requestRestartOnExit();
    }

    public static void requestRestartOnExit() {
        restartOnExit = true;
    }

    public static void restartIfRequested() {
        if (!restartOnExit) {
            return;
        }
        restartOnExit = false;
        restart();
    }

    public static void restart() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(restartCommand());
            processBuilder.directory(new File(System.getProperty("user.dir")));
            processBuilder.start();
        } catch (IOException | URISyntaxException exception) {
            throw new RestoreService.RestoreException("Restore completed, but automatic restart failed.", exception);
        }
    }

    private static void logoutCurrentUser() {
        AutoBackupScheduler.getInstance().cancel();
        AuthContext.getCurrentUser()
                .ifPresent(user -> new ActivityLogService().logLogout(user.getUserId(), user.getUsername()));
        AuthContext.clear();
    }

    private static List<String> restartCommand() throws URISyntaxException {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        Path appPath = Path.of(AppLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (appPath.toString().endsWith(".jar")) {
            command.add("-jar");
            command.add(appPath.toString());
            return command;
        }
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(AppLauncher.class.getName());
        return command;
    }

    private static String javaExecutable() {
        String executable = isWindows() ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
