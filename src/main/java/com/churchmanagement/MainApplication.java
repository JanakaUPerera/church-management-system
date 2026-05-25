package com.churchmanagement;

import com.churchmanagement.config.AppConfig;
import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.config.MigrationRunner;
import com.churchmanagement.exception.DatabaseException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApplication extends Application {
    private DatabaseException startupException;

    @Override
    public void init() {
        try {
            MigrationRunner.runMigrations();
        } catch (DatabaseException exception) {
            startupException = exception;
        }
    }

    @Override
    public void start(Stage stage) throws IOException {
        if (startupException != null) {
            showStartupError(startupException);
            Platform.exit();
            return;
        }

        FXMLLoader loader = new FXMLLoader(MainApplication.class.getResource(AppConfig.LOGIN_VIEW));
        Scene scene = new Scene(loader.load(), AppConfig.LOGIN_WIDTH, AppConfig.LOGIN_HEIGHT);

        stage.setTitle(AppConfig.APPLICATION_NAME);
        stage.setScene(scene);
        stage.setMinWidth(AppConfig.LOGIN_WIDTH);
        stage.setMinHeight(AppConfig.LOGIN_HEIGHT);
        stage.show();
    }

    @Override
    public void stop() {
        DatabaseConfig.closeDataSource();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void showStartupError(DatabaseException exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Database Startup Error");
        alert.setHeaderText("Unable to start the database layer");
        alert.setContentText(exception.getMessage());
        alert.showAndWait();
    }
}
