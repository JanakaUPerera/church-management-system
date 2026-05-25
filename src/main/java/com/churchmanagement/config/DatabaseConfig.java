package com.churchmanagement.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class DatabaseConfig {
    private static final String PROPERTIES_FILE = "/application.properties";

    private DatabaseConfig() {
    }

    public static DataSource createDataSource() {
        Properties properties = loadProperties();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getProperty("db.url"));
        config.setUsername(properties.getProperty("db.username"));
        config.setPassword(properties.getProperty("db.password"));
        config.setDriverClassName(properties.getProperty("db.driver"));
        config.setMaximumPoolSize(Integer.parseInt(properties.getProperty("db.pool.maximum-size", "10")));

        return new HikariDataSource(config);
    }

    private static Properties loadProperties() {
        try (InputStream inputStream = DatabaseConfig.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing " + PROPERTIES_FILE);
            }

            Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load application properties.", exception);
        }
    }
}
