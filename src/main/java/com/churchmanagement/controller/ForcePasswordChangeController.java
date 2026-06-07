package com.churchmanagement.controller;

import com.churchmanagement.config.AppConfig;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.service.ActivityLogService;
import com.churchmanagement.service.PasswordChangeService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ThemeService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

public class ForcePasswordChangeController {
    private final PasswordChangeService passwordChangeService = new PasswordChangeService();
    private final ActivityLogService activityLogService = new ActivityLogService();
    private double windowDragOffsetX;
    private double windowDragOffsetY;

    @FXML private StackPane forcePasswordRoot;
    @FXML private Label userLabel;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button changePasswordButton;
    @FXML private Button logoutButton;
    @FXML private Label messageLabel;

    @FXML
    private void initialize() {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser().orElse(null);
        if (currentUser == null) {
            Platform.runLater(this::returnToLogin);
            return;
        }

        userLabel.setText(currentUser.getFullName() + " (" + currentUser.getUsername() + ")");
        ButtonIconUtil.applyIcon(changePasswordButton, "fas-key");
        ButtonIconUtil.applyIcon(logoutButton, "fas-sign-out-alt");
        configureWindowDrag();
    }

    @FXML
    private void handleChangePassword() throws IOException {
        messageLabel.setText("");

        try {
            passwordChangeService.changeForcedPassword(currentPasswordField.getText(), newPasswordField.getText(),
                    confirmPasswordField.getText());
            showSuccess();
            openDashboard();
        } catch (PasswordChangeService.PasswordChangeException exception) {
            messageLabel.setText(exception.getMessage());
        }
    }

    @FXML
    private void handleLogout() {
        AuthContext.getCurrentUser()
                .ifPresent(user -> activityLogService.logForcePasswordChangeLogout(user.getUserId(),
                        user.getUsername()));
        AuthContext.clear();
        returnToLogin();
    }

    private void configureWindowDrag() {
        forcePasswordRoot.setOnMousePressed(event -> {
            windowDragOffsetX = event.getSceneX();
            windowDragOffsetY = event.getSceneY();
        });
        forcePasswordRoot.setOnMouseDragged(event -> {
            Stage stage = (Stage) forcePasswordRoot.getScene().getWindow();
            stage.setX(event.getScreenX() - windowDragOffsetX);
            stage.setY(event.getScreenY() - windowDragOffsetY);
        });
    }

    private void showSuccess() {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.INFORMATION));
        alert.setTitle("Password Changed");
        alert.setHeaderText("Password changed successfully");
        alert.setContentText("You can now continue to the dashboard.");
        alert.showAndWait();
    }

    private void openDashboard() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(AppConfig.DASHBOARD_VIEW));
        Scene scene = new Scene(loader.load(), AppConfig.DASHBOARD_WIDTH, AppConfig.DASHBOARD_HEIGHT);
        new ThemeService().applyConfiguredTheme(scene.getRoot());
        Stage currentStage = (Stage) forcePasswordRoot.getScene().getWindow();
        Stage stage = new Stage(StageStyle.UNDECORATED);

        stage.setTitle(AppConfig.APPLICATION_NAME + " - Dashboard");
        stage.setScene(scene);
        stage.setMinWidth(AppConfig.DASHBOARD_WIDTH);
        stage.setMinHeight(AppConfig.DASHBOARD_HEIGHT);
        stage.centerOnScreen();
        stage.show();
        currentStage.close();
        Platform.runLater(() -> stage.setMaximized(true));
    }

    private void returnToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(AppConfig.LOGIN_VIEW));
            Scene scene = new Scene(loader.load(), AppConfig.LOGIN_WIDTH, AppConfig.LOGIN_HEIGHT);
            new ThemeService().applyConfiguredTheme(scene.getRoot());
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            Stage currentStage = forcePasswordRoot == null || forcePasswordRoot.getScene() == null
                    ? null
                    : (Stage) forcePasswordRoot.getScene().getWindow();
            Stage stage = new Stage(StageStyle.TRANSPARENT);

            stage.setTitle(AppConfig.APPLICATION_NAME);
            stage.setScene(scene);
            stage.setMinWidth(AppConfig.LOGIN_WIDTH);
            stage.setMinHeight(AppConfig.LOGIN_HEIGHT);
            stage.centerOnScreen();
            stage.show();
            if (currentStage != null) {
                currentStage.close();
            }
        } catch (IOException exception) {
            Platform.exit();
        }
    }
}
