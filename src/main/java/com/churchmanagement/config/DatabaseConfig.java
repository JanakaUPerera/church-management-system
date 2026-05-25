package com.churchmanagement.config;

import com.churchmanagement.exception.DatabaseException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
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

    public static synchronized void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
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
