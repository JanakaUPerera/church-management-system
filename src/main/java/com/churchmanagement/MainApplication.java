package com.churchmanagement;

import com.churchmanagement.config.AppConfig;
import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.config.MigrationRunner;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.service.AutoBackupScheduler;
import com.churchmanagement.util.DialogStyler;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

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
        scene.setFill(Color.TRANSPARENT);

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle(AppConfig.APPLICATION_NAME);
        stage.setScene(scene);
        stage.setMinWidth(AppConfig.LOGIN_WIDTH);
        stage.setMinHeight(AppConfig.LOGIN_HEIGHT);
        stage.show();
    }

    @Override
    public void stop() {
        AutoBackupScheduler.getInstance().cancel();
        DatabaseConfig.closeDataSource();
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void showStartupError(DatabaseException exception) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Database Startup Error");
        alert.setHeaderText("Unable to start the database layer");
        alert.setContentText(exception.getMessage());
        alert.showAndWait();
    }
}
