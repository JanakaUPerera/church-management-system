package com.churchmanagement.config;

public final class AppConfig {
    public static final String APPLICATION_NAME = "Church Management System";
    public static final String APPLICATION_VERSION = "1.1.0";
    public static final String STARTUP_VIEW = "/com/churchmanagement/view/startup-view.fxml";
    public static final String LOGIN_VIEW = "/com/churchmanagement/view/login-view.fxml";
    public static final String DASHBOARD_VIEW = "/com/churchmanagement/view/dashboard-view.fxml";
    public static final String FORCE_PASSWORD_CHANGE_VIEW = "/com/churchmanagement/view/force-password-change-view.fxml";

    public static final double STARTUP_WIDTH = 420;
    public static final double STARTUP_HEIGHT = 480;
    public static final double LOGIN_WIDTH = 520;
    public static final double LOGIN_HEIGHT = 470;
    public static final double FORCE_PASSWORD_CHANGE_WIDTH = 520;
    public static final double FORCE_PASSWORD_CHANGE_HEIGHT = 480;
    public static final double DASHBOARD_WIDTH = 1100;
    public static final double DASHBOARD_HEIGHT = 720;

    private AppConfig() {
    }
}
