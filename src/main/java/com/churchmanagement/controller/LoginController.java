package com.churchmanagement.controller;

import com.churchmanagement.config.AppConfig;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.service.AuthService;
import com.churchmanagement.util.ButtonIconUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;

public class LoginController {
    private final AuthService authService = new AuthService();
    private boolean passwordVisible;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField visiblePasswordField;

    @FXML
    private Button togglePasswordButton;

    @FXML
    private Button loginButton;

    @FXML
    private Label errorLabel;

    @FXML
    private void initialize() {
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        ButtonIconUtil.applyIcon(loginButton, "fas-sign-in-alt");
        setPasswordVisible(false);
    }

    @FXML
    private void handleLogin() throws IOException {
        errorLabel.setText("");

        try {
            AuthenticatedUser authenticatedUser = authService.login(usernameField.getText(), passwordField.getText());
            AuthContext.setCurrentUser(authenticatedUser);
            openDashboard();
        } catch (AuthService.AuthException exception) {
            errorLabel.setText(exception.getMessage());
        }
    }

    @FXML
    private void togglePasswordVisibility() {
        setPasswordVisible(!passwordVisible);
    }

    private void setPasswordVisible(boolean visible) {
        passwordVisible = visible;
        visiblePasswordField.setVisible(visible);
        visiblePasswordField.setManaged(visible);
        passwordField.setVisible(!visible);
        passwordField.setManaged(!visible);
        togglePasswordButton.setGraphic(createPasswordIcon(visible ? "fas-eye-slash" : "fas-eye"));
    }

    private FontIcon createPasswordIcon(String iconLiteral) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("password-toggle-icon");
        return icon;
    }

    private void openDashboard() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(AppConfig.DASHBOARD_VIEW));
        Scene scene = new Scene(loader.load(), AppConfig.DASHBOARD_WIDTH, AppConfig.DASHBOARD_HEIGHT);
        Stage stage = (Stage) usernameField.getScene().getWindow();

        stage.setTitle(AppConfig.APPLICATION_NAME + " - Dashboard");
        stage.setScene(scene);
        stage.setMinWidth(AppConfig.DASHBOARD_WIDTH);
        stage.setMinHeight(AppConfig.DASHBOARD_HEIGHT);
        stage.centerOnScreen();
        Platform.runLater(() -> stage.setMaximized(true));
    }
}
