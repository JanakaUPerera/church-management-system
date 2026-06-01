package com.churchmanagement.service;

import com.churchmanagement.dto.BackupSettingsDto;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupCommandBuilderTest {
    private final BackupCommandBuilder builder = new BackupCommandBuilder(
            new BackupCommandBuilder.DatabaseCredentials("localhost", 3306,
                    "church_collection", "root", "secret"));

    @Test
    void buildsMysqldumpCommandWithDatabaseConnectionValues() {
        BackupCommandBuilder.CommandSpec command = builder.buildDumpCommand(new BackupSettingsDto(),
                Path.of("backup.sql"));

        assertTrue(command.command().contains("--host=localhost"));
        assertTrue(command.command().contains("--port=3306"));
        assertTrue(command.command().contains("--user=root"));
        assertTrue(command.command().contains("church_collection"));
    }

    @Test
    void safeLogOutputDoesNotExposePassword() {
        BackupCommandBuilder.CommandSpec command = builder.buildDumpCommand(new BackupSettingsDto(),
                Path.of("backup.sql"));

        assertTrue(command.command().contains("--password=secret"));
        assertFalse(command.safeCommandForLogging().contains("--password=secret"));
        assertTrue(command.safeCommandForLogging().contains("--password=****"));
    }

    @Test
    void usesCustomMysqldumpPathWhenConfigured() {
        BackupSettingsDto settings = new BackupSettingsDto();
        settings.setMysqldumpPath("C:/mysql/bin/mysqldump.exe");

        BackupCommandBuilder.CommandSpec command = builder.buildDumpCommand(settings, Path.of("backup.sql"));

        assertEquals("C:/mysql/bin/mysqldump.exe", command.command().getFirst());
    }

    @Test
    void resolvesMysqldumpDirectoryPathToExecutable() {
        BackupSettingsDto settings = new BackupSettingsDto();
        settings.setMysqldumpPath("C:/mysql/bin/");

        BackupCommandBuilder.CommandSpec command = builder.buildDumpCommand(settings, Path.of("backup.sql"));

        assertTrue(command.command().getFirst().replace('\\', '/').endsWith("/mysqldump.exe")
                || command.command().getFirst().replace('\\', '/').endsWith("/mysqldump"));
    }

    @Test
    void resolvesMysqlClientDirectoryPathToExecutable() {
        BackupSettingsDto settings = new BackupSettingsDto();
        settings.setMysqlClientPath("C:/mysql/bin/");

        BackupCommandBuilder.CommandSpec command = builder.buildRestoreCommand(settings);

        assertTrue(command.command().getFirst().replace('\\', '/').endsWith("/mysql.exe")
                || command.command().getFirst().replace('\\', '/').endsWith("/mysql"));
    }
}
