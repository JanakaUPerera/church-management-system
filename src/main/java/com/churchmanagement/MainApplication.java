package com.churchmanagement;

import com.churchmanagement.config.AppConfig;
import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.controller.StartupController;
import com.churchmanagement.service.AutoBackupScheduler;
import com.churchmanagement.util.ApplicationRestartUtil;
import com.churchmanagement.util.ThemeService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

public class MainApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setTitle(AppConfig.APPLICATION_NAME);

        showStartupScreen(primaryStage);
    }

    @Override
    public void stop() {
        try {
            AutoBackupScheduler.getInstance().cancel();
        } catch (Exception ignored) {
        }
        DatabaseConfig.closeDataSource();
        ApplicationRestartUtil.restartIfRequested();
    }

    public static void main(String[] args) {
        launchUi(args);
    }

    public static void launchUi(String[] args) {
        launch(args);
    }

    private void showStartupScreen(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource(AppConfig.STARTUP_VIEW));
        StackPane root = loader.load();
        StartupController controller = loader.getController();

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root, screen.getWidth(), screen.getHeight());
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.setX(screen.getMinX());
        stage.setY(screen.getMinY());
        stage.show();

        controller.setOnReady(() -> transitionToLogin(stage));
        controller.startChecks();
    }

    private void transitionToLogin(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource(AppConfig.LOGIN_VIEW));
            Scene scene = new Scene(loader.load(), AppConfig.LOGIN_WIDTH, AppConfig.LOGIN_HEIGHT);
            new ThemeService().applyConfiguredTheme(scene.getRoot());
            scene.setFill(Color.TRANSPARENT);
            stage.setScene(scene);
            stage.setMinWidth(AppConfig.LOGIN_WIDTH);
            stage.setMinHeight(AppConfig.LOGIN_HEIGHT);
            stage.centerOnScreen();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load login view.", e);
        }
    }
}
