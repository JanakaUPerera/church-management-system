package com.churchmanagement.controller;

import com.churchmanagement.config.AppConfig;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {
    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label messageLabel;

    @FXML
    private void handleLogin() throws IOException {
        if (usernameField.getText().isBlank() || passwordField.getText().isBlank()) {
            messageLabel.setText("Enter username and password.");
            return;
        }

        // Temporary navigation until database-backed authentication is implemented.
        FXMLLoader loader = new FXMLLoader(getClass().getResource(AppConfig.DASHBOARD_VIEW));
        Scene scene = new Scene(loader.load(), AppConfig.DASHBOARD_WIDTH, AppConfig.DASHBOARD_HEIGHT);
        Stage stage = (Stage) usernameField.getScene().getWindow();

        stage.setTitle(AppConfig.APPLICATION_NAME + " - Dashboard");
        stage.setScene(scene);
        stage.setMinWidth(AppConfig.DASHBOARD_WIDTH);
        stage.setMinHeight(AppConfig.DASHBOARD_HEIGHT);
        stage.centerOnScreen();
    }
}
