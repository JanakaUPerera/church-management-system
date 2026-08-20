package com.churchmanagement.dto;

public class DatabaseSetupDto {
    private String host;
    private int port;
    private String databaseName;
    private String username;
    private String password;
    private boolean runMigrations;

    public DatabaseSetupDto() {
        this.host = "localhost";
        this.port = 3306;
        this.databaseName = "church_management_system";
        this.username = "";
        this.password = "";
        this.runMigrations = true;
    }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isRunMigrations() { return runMigrations; }
    public void setRunMigrations(boolean runMigrations) { this.runMigrations = runMigrations; }
}
