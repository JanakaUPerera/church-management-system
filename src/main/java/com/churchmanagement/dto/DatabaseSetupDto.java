package com.churchmanagement.dto;

public class DatabaseSetupDto {
    private String host;
    private int port;
    private String databaseName;

    // Admin credentials — used once during setup to create the app user and
    // grant privileges.  Never written to application.properties.
    private String adminUsername;
    private String adminPassword;

    // Application credentials — stored in application.properties and used by
    // the app on every launch.
    private String username;
    private String password;

    private boolean runMigrations;

    public DatabaseSetupDto() {
        this.host          = "localhost";
        this.port          = 3306;
        this.databaseName  = "church_management_system";
        this.adminUsername = "root";
        this.adminPassword = "";
        this.username      = "";
        this.password      = "";
        this.runMigrations = true;
    }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String databaseName) { this.databaseName = databaseName; }

    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }

    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isRunMigrations() { return runMigrations; }
    public void setRunMigrations(boolean runMigrations) { this.runMigrations = runMigrations; }
}
