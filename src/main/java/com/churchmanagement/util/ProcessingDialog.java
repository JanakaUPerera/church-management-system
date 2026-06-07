package com.churchmanagement.util;

import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.net.URL;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public final class ProcessingDialog {
    private static final String APP_STYLESHEET = "/com/churchmanagement/view/app.css";

    private ProcessingDialog() {
    }

    public static <T> void run(String title, String message, Callable<T> action,
                               Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Window owner = focusedWindow();
        Stage overlayStage = new Stage(StageStyle.TRANSPARENT);
        overlayStage.setTitle(title);
        if (owner != null) {
            overlayStage.initOwner(owner);
            overlayStage.initModality(Modality.WINDOW_MODAL);
        }

        StackPane overlay = content(message, owner);
        new ThemeService().applyConfiguredTheme(overlay);
        Scene scene = new Scene(overlay, overlay.getPrefWidth(), overlay.getPrefHeight());
        scene.setFill(Color.TRANSPARENT);
        URL stylesheet = ProcessingDialog.class.getResource(APP_STYLESHEET);
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        overlayStage.setScene(scene);
        sizeToOwner(overlayStage, overlay, owner);

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return action.call();
            }
        };
        task.setOnSucceeded(event -> {
            overlayStage.hide();
            onSuccess.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            overlayStage.hide();
            onFailure.accept(task.getException());
        });

        Thread worker = new Thread(task, "processing-dialog-worker");
        worker.setDaemon(true);
        overlayStage.setOnShown(event -> worker.start());
        overlayStage.show();
    }

    public static void run(String title, String message, ThrowingRunnable action,
                           Runnable onSuccess, Consumer<Throwable> onFailure) {
        run(title, message, () -> {
            action.run();
            return null;
        }, ignored -> onSuccess.run(), onFailure);
    }

    private static StackPane content(String message, Window owner) {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setPrefSize(42, 42);
        Label label = new Label(message);
        label.getStyleClass().add("processing-dialog-label");
        VBox card = new VBox(14, indicator, label);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(280);
        card.setPrefHeight(132);
        card.getStyleClass().add("processing-dialog-card");

        StackPane overlay = new StackPane(card);
        overlay.setAlignment(Pos.CENTER);
        overlay.getStyleClass().add("processing-dialog-overlay");
        overlay.setPrefWidth(owner == null ? 420 : owner.getWidth());
        overlay.setPrefHeight(owner == null ? 260 : owner.getHeight());
        return overlay;
    }

    private static void sizeToOwner(Stage overlayStage, StackPane overlay, Window owner) {
        if (owner == null) {
            return;
        }
        overlayStage.setX(owner.getX());
        overlayStage.setY(owner.getY());
        overlayStage.setWidth(owner.getWidth());
        overlayStage.setHeight(owner.getHeight());
        overlay.setMinSize(owner.getWidth(), owner.getHeight());
        overlay.setPrefSize(owner.getWidth(), owner.getHeight());
        overlay.setMaxSize(owner.getWidth(), owner.getHeight());
    }

    private static Window focusedWindow() {
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .filter(Window::isFocused)
                .max(Comparator.comparingDouble(Window::getX))
                .orElseGet(() -> Window.getWindows().stream()
                        .filter(Window::isShowing)
                        .findFirst()
                        .orElse(null));
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
