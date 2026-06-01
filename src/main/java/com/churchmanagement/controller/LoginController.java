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
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;

public class LoginController {
    private final AuthService authService = new AuthService();
    private boolean passwordVisible;
    private double windowDragOffsetX;
    private double windowDragOffsetY;

    @FXML
    private StackPane loginRoot;

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
    private Button closeLoginButton;

    @FXML
    private Label errorLabel;

    @FXML
    private void initialize() {
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        ButtonIconUtil.applyIcon(loginButton, "fas-sign-in-alt");
        ButtonIconUtil.applyIcon(closeLoginButton, "fas-times");
        closeLoginButton.setText("");
        configureWindowDrag();
        setPasswordVisible(false);
    }

    @FXML
    private void handleLogin() throws IOException {
        errorLabel.setText("");

        try {
            AuthenticatedUser authenticatedUser = authService.login(usernameField.getText(), passwordField.getText());
            AuthContext.setCurrentUser(authenticatedUser);
            if (authenticatedUser.isForcePasswordChange()) {
                openForcePasswordChange();
            } else {
                openDashboard();
            }
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

    @FXML
    private void closeLogin() {
        Platform.exit();
    }

    private void configureWindowDrag() {
        loginRoot.setOnMousePressed(event -> {
            windowDragOffsetX = event.getSceneX();
            windowDragOffsetY = event.getSceneY();
        });
        loginRoot.setOnMouseDragged(event -> {
            Stage stage = (Stage) loginRoot.getScene().getWindow();
            stage.setX(event.getScreenX() - windowDragOffsetX);
            stage.setY(event.getScreenY() - windowDragOffsetY);
        });
    }

    private FontIcon createPasswordIcon(String iconLiteral) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("password-toggle-icon");
        return icon;
    }

    private void openDashboard() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(AppConfig.DASHBOARD_VIEW));
        Scene scene = new Scene(loader.load(), AppConfig.DASHBOARD_WIDTH, AppConfig.DASHBOARD_HEIGHT);
        Stage loginStage = (Stage) usernameField.getScene().getWindow();
        Stage stage = new Stage(StageStyle.UNDECORATED);

        stage.setTitle(AppConfig.APPLICATION_NAME + " - Dashboard");
        stage.setScene(scene);
        stage.setMinWidth(AppConfig.DASHBOARD_WIDTH);
        stage.setMinHeight(AppConfig.DASHBOARD_HEIGHT);
        stage.centerOnScreen();
        stage.show();
        loginStage.close();
        Platform.runLater(() -> stage.setMaximized(true));
    }

    private void openForcePasswordChange() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(AppConfig.FORCE_PASSWORD_CHANGE_VIEW));
        Scene scene = new Scene(loader.load(), AppConfig.FORCE_PASSWORD_CHANGE_WIDTH,
                AppConfig.FORCE_PASSWORD_CHANGE_HEIGHT);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        Stage loginStage = (Stage) usernameField.getScene().getWindow();
        Stage stage = new Stage(StageStyle.TRANSPARENT);

        stage.setTitle(AppConfig.APPLICATION_NAME + " - Change Password");
        stage.setScene(scene);
        stage.setMinWidth(AppConfig.FORCE_PASSWORD_CHANGE_WIDTH);
        stage.setMinHeight(AppConfig.FORCE_PASSWORD_CHANGE_HEIGHT);
        stage.centerOnScreen();
        stage.show();
        loginStage.close();
    }
}
