package com.churchmanagement.service;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.BackupSettingsDto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BackupCommandBuilder {
    private final DatabaseCredentials credentials;

    public BackupCommandBuilder() {
        this(DatabaseCredentials.fromApplicationProperties());
    }

    public BackupCommandBuilder(DatabaseCredentials credentials) {
        this.credentials = credentials;
    }

    public CommandSpec buildDumpCommand(BackupSettingsDto settings, Path resultFile) {
        String executable = configuredOrDefault(settings == null ? null : settings.getMysqldumpPath(),
                "mysqldump", "mysqldump.exe");
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("--host=" + credentials.host());
        command.add("--port=" + credentials.port());
        command.add("--user=" + credentials.user());
        command.add("--password=" + credentials.password());
        command.add("--databases");
        command.add(credentials.databaseName());
        command.add("--routines");
        command.add("--events");
        command.add("--single-transaction");
        command.add("--default-character-set=utf8mb4");
        command.add("--result-file=" + resultFile.toAbsolutePath());
        return new CommandSpec(command);
    }

    public CommandSpec buildRestoreCommand(BackupSettingsDto settings) {
        String executable = configuredOrDefault(settings == null ? null : settings.getMysqlClientPath(),
                "mysql", "mysql.exe");
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("--host=" + credentials.host());
        command.add("--port=" + credentials.port());
        command.add("--user=" + credentials.user());
        command.add("--password=" + credentials.password());
        command.add(credentials.databaseName());
        return new CommandSpec(command);
    }

    private String configuredOrDefault(String configuredPath, String defaultExecutable, String windowsExecutable) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return defaultExecutable;
        }

        String strippedPath = configuredPath.strip();
        Path path = Path.of(strippedPath);
        if (Files.isDirectory(path) || looksLikeDirectoryPath(strippedPath)) {
            return path.resolve(isWindows() ? windowsExecutable : defaultExecutable).toString();
        }
        return strippedPath;
    }

    private boolean looksLikeDirectoryPath(String configuredPath) {
        return configuredPath.endsWith("/") || configuredPath.endsWith("\\");
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public record CommandSpec(List<String> command) {
        public List<String> safeCommandForLogging() {
            return command.stream()
                    .map(value -> value.startsWith("--password=") ? "--password=****" : value)
                    .toList();
        }
    }

    public record DatabaseCredentials(String host, int port, String databaseName, String user, String password) {
        public static DatabaseCredentials fromApplicationProperties() {
            String host = propertyOrDefault("db.host", "localhost");
            int port = parsePort(propertyOrDefault("db.port", "3306"));
            String name = propertyOrDefault("db.name", parseDatabaseName(DatabaseConfig.getProperty("db.url")));
            String user = propertyOrDefault("db.user", propertyOrDefault("db.username", ""));
            String password = propertyOrDefault("db.password", "");
            return new DatabaseCredentials(host, port, name, user, password);
        }

        private static String propertyOrDefault(String key, String defaultValue) {
            String value = DatabaseConfig.getProperty(key);
            return value == null || value.isBlank() ? defaultValue : value.strip();
        }

        private static int parsePort(String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                return 3306;
            }
        }

        private static String parseDatabaseName(String jdbcUrl) {
            if (jdbcUrl == null || jdbcUrl.isBlank()) {
                return "";
            }
            int slashIndex = jdbcUrl.lastIndexOf('/');
            if (slashIndex < 0 || slashIndex == jdbcUrl.length() - 1) {
                return "";
            }
            String tail = jdbcUrl.substring(slashIndex + 1);
            int queryIndex = tail.indexOf('?');
            return queryIndex >= 0 ? tail.substring(0, queryIndex) : tail;
        }
    }
}
