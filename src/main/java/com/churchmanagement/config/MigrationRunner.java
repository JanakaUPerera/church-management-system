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

            Flyway flyway = Flyway.configure()
                    .dataSource(DatabaseConfig.getDataSource())
                    .locations(DatabaseConfig.getProperty("flyway.locations"))
                    .baselineOnMigrate(true)
                    .ignoreMigrationPatterns("*:missing")
                    .load();

            flyway.migrate();
        } catch (FlywayException | DatabaseException exception) {
            throw new DatabaseException("Database migration failed.", exception);
        }
    }
}
