package com.churchmanagement.controller;

import com.churchmanagement.config.AppConfig;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.service.ActivityLogService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DashboardController {
    private final ActivityLogService activityLogService = new ActivityLogService();

    @FXML
    private Label dateLabel;

    @FXML
    private Label fullNameLabel;

    @FXML
    private Label roleNameLabel;

    @FXML
    private void initialize() {
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));

        AuthContext.getCurrentUser().ifPresent(user -> {
            fullNameLabel.setText(user.getFullName());
            roleNameLabel.setText(user.getRoleName());
        });
    }

    @FXML
    private void handleLogout() throws IOException {
        AuthContext.getCurrentUser()
                .ifPresent(user -> activityLogService.logLogout(user.getUserId(), user.getUsername()));
        AuthContext.clear();

        FXMLLoader loader = new FXMLLoader(getClass().getResource(AppConfig.LOGIN_VIEW));
        Scene scene = new Scene(loader.load(), AppConfig.LOGIN_WIDTH, AppConfig.LOGIN_HEIGHT);
        Stage stage = (Stage) dateLabel.getScene().getWindow();

        stage.setTitle(AppConfig.APPLICATION_NAME);
        stage.setScene(scene);
        stage.setMinWidth(AppConfig.LOGIN_WIDTH);
        stage.setMinHeight(AppConfig.LOGIN_HEIGHT);
        stage.setWidth(AppConfig.LOGIN_WIDTH);
        stage.setHeight(AppConfig.LOGIN_HEIGHT);
        stage.centerOnScreen();
    }
}
