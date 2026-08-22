package com.churchmanagement.config;

import com.churchmanagement.exception.DatabaseException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

public final class MigrationRunner {
    private MigrationRunner() {
    }

    public static void runMigrations() {
        try {
            DatabaseConfig.testConnection();

            // Secondary clients connect to the shared database but never apply
            // schema changes — see PrimaryMachine.
            if (!PrimaryMachine.isPrimary()) {
                return;
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(DatabaseConfig.getDataSource())
                    .locations(DatabaseConfig.getProperty("flyway.locations"))
                    .baselineOnMigrate(true)
                    .ignoreMigrationPatterns("*:missing")
                    .load();

            // Remove any FAILED migration entries left by a previous failed run
            // so the corrected migration is retried automatically on next startup.
            flyway.repair();
            flyway.migrate();
        } catch (FlywayException | DatabaseException exception) {
            throw new DatabaseException("Database migration failed.", exception);
        }
    }
}
