package com.churchmanagement.config;

import com.churchmanagement.exception.DatabaseException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public final class DatabaseConfig {
    private static final String PROPERTIES_FILE = "/application.properties";
    private static HikariDataSource dataSource;

    private DatabaseConfig() {
    }

    public static synchronized DataSource getDataSource() {
        if (dataSource == null) {
            dataSource = createDataSource();
        }

        return dataSource;
    }

    public static void testConnection() {
        try (Connection connection = getDataSource().getConnection()) {
            if (!connection.isValid(5)) {
                throw new DatabaseException("Database connection validation failed.");
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to connect to the database.", exception);
        }
    }

    public static String getProperty(String key) {
        return loadProperties().getProperty(key);
    }

    /**
     * Persists a single property into this machine's external
     * {@code application.properties}, preserving every other key already
     * present. Use this for settings that must stay <em>per-machine</em>
     * (e.g. a local export folder) rather than shared system-wide via the
     * database — each installation keeps its own copy instead of every
     * client on the LAN reading one value out of the shared database.
     *
     * <p>No-op in dev / IDE mode ({@code app.home} absent) — the bundled
     * classpath properties remain the active source and there is no
     * external file to write, mirroring {@code DatabaseSetupService.save()}.</p>
     */
    public static synchronized void setProperty(String key, String value) {
        Path externalConfig = AppHome.configFile();
        if (externalConfig == null) {
            return; // dev mode — nothing to persist
        }
        Properties properties = loadProperties();
        properties.setProperty(key, value);
        try {
            Files.createDirectories(externalConfig.getParent());
            try (OutputStream out = Files.newOutputStream(externalConfig)) {
                properties.store(out, "Church Management System — machine configuration");
            }
        } catch (IOException exception) {
            throw new DatabaseException("Unable to save local setting: " + key, exception);
        }
    }

    public static synchronized void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * Closes the current pool and clears the singleton so the next call to
     * {@link #getDataSource()} re-reads {@code application.properties} and
     * creates a fresh pool.  Call this after the database setup wizard saves
     * new connection settings.
     */
    public static synchronized void reset() {
        closeDataSource();
        dataSource = null;
    }

    private static HikariDataSource createDataSource() {
        Properties properties = loadProperties();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getProperty("db.url"));
        config.setUsername(properties.getProperty("db.username"));
        config.setPassword(properties.getProperty("db.password"));
        config.setDriverClassName(properties.getProperty("db.driver"));
        config.setPoolName(properties.getProperty("db.pool.name", "ChurchManagementPool"));
        config.setMaximumPoolSize(Integer.parseInt(properties.getProperty("db.pool.maximum-size", "10")));
        config.setMinimumIdle(Integer.parseInt(properties.getProperty("db.pool.minimum-idle", "2")));
        config.setConnectionTimeout(Long.parseLong(properties.getProperty("db.pool.connection-timeout", "30000")));
        config.setIdleTimeout(Long.parseLong(properties.getProperty("db.pool.idle-timeout", "600000")));
        config.setMaxLifetime(Long.parseLong(properties.getProperty("db.pool.max-lifetime", "1800000")));

        try {
            return new HikariDataSource(config);
        } catch (RuntimeException exception) {
            throw new DatabaseException("Failed to initialize database connection pool.", exception);
        }
    }

    private static Properties loadProperties() {
        // In production (app.home set) prefer the user-writable external config
        // file resolved by AppHome — stored in %APPDATA% on Windows so that
        // normal (non-admin) users can write it without elevated privileges.
        Path externalConfig = AppHome.configFile();
        if (externalConfig != null && Files.exists(externalConfig)) {
            try (InputStream inputStream = Files.newInputStream(externalConfig)) {
                Properties properties = new Properties();
                properties.load(inputStream);
                return properties;
            } catch (IOException exception) {
                throw new DatabaseException(
                        "Unable to load external application.properties from: " + externalConfig,
                        exception);
            }
        }

        // Dev mode or first launch before wizard saves — fall back to bundled classpath file.
        try (InputStream inputStream = DatabaseConfig.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (inputStream == null) {
                throw new DatabaseException("Missing " + PROPERTIES_FILE);
            }

            Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        } catch (IOException exception) {
            throw new DatabaseException("Unable to load application properties.", exception);
        }
    }
}
