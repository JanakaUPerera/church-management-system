package com.churchmanagement.util;

import javafx.collections.ListChangeListener;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.net.URL;

public final class DialogStyler {
    private static final String APP_STYLESHEET = "/com/churchmanagement/view/app.css";
    private static final String APP_DIALOG_PANE = "app-dialog-pane";
    private static final String DIALOG_TITLE_BAR = "dialog-title-bar";
    private static final String DIALOG_TITLE_LABEL = "dialog-title-label";
    private static final String DIALOG_CLOSE_BUTTON = "dialog-close-button";
    private static final String DIALOG_FIELD_LABEL = "dialog-field-label";
    private static final String DIALOG_BUTTON_ICONS_APPLIED = "dialog-button-icons-applied";

    private DialogStyler() {
    }

    public static <T extends Dialog<?>> T apply(T dialog) {
        dialog.initStyle(StageStyle.TRANSPARENT);
        apply(dialog.getDialogPane());
        applyCustomHeader(dialog);
        dialog.addEventHandler(DialogEvent.DIALOG_SHOWING, event -> makeSceneTransparent(dialog.getDialogPane()));
        return dialog;
    }

    public static void apply(DialogPane dialogPane) {
        URL stylesheet = DialogStyler.class.getResource(APP_STYLESHEET);
        if (stylesheet != null) {
            String stylesheetUrl = stylesheet.toExternalForm();
            if (!dialogPane.getStylesheets().contains(stylesheetUrl)) {
                dialogPane.getStylesheets().add(stylesheetUrl);
            }
        }

        if (!dialogPane.getStyleClass().contains(APP_DIALOG_PANE)) {
            dialogPane.getStyleClass().add(APP_DIALOG_PANE);
        }

        applyButtonIcons(dialogPane);
    }

    private static void applyCustomHeader(Dialog<?> dialog) {
        DialogPane dialogPane = dialog.getDialogPane();

        Label title = new Label();
        title.getStyleClass().add(DIALOG_TITLE_LABEL);
        title.textProperty().bind(Bindings.createStringBinding(() -> {
            String headerText = dialog.getHeaderText();
            if (headerText != null && !headerText.isBlank()) {
                return headerText;
            }
            String dialogTitle = dialog.getTitle();
            return dialogTitle == null ? "" : dialogTitle;
        }, dialog.headerTextProperty(), dialog.titleProperty()));

        Button closeButton = new Button();
        closeButton.setText("");
        closeButton.getStyleClass().add(DIALOG_CLOSE_BUTTON);
        ButtonIconUtil.applyIcon(closeButton, "fas-times");
        closeButton.setOnAction(event -> close(dialog));

        Region spacer = new Region();

        HBox titleBar = new HBox(12, title, spacer, closeButton);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleBar.getStyleClass().add(DIALOG_TITLE_BAR);
        enableWindowDrag(titleBar);
        dialogPane.setHeader(titleBar);
    }

    public static Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add(DIALOG_FIELD_LABEL);
        return label;
    }

    private static void applyButtonIcons(DialogPane dialogPane) {
        decorateButtons(dialogPane);
        if (dialogPane.getProperties().putIfAbsent(DIALOG_BUTTON_ICONS_APPLIED, Boolean.TRUE) == null) {
            dialogPane.getButtonTypes().addListener((ListChangeListener<ButtonType>) change -> decorateButtons(dialogPane));
        }
    }

    private static void decorateButtons(DialogPane dialogPane) {
        for (ButtonType buttonType : dialogPane.getButtonTypes()) {
            Node buttonNode = dialogPane.lookupButton(buttonType);
            if (buttonNode instanceof Button button) {
                ButtonIconUtil.applyIcon(button, iconFor(buttonType));
            }
        }
    }

    private static String iconFor(ButtonType buttonType) {
        String text = buttonType.getText() == null ? "" : buttonType.getText().toLowerCase();
        if (text.contains("pdf")) {
            return "fas-file-pdf";
        }
        if (text.contains("print")) {
            return "fas-print";
        }
        if (text.contains("send") || text.contains("resend") || text.contains("sms")) {
            return "fas-paper-plane";
        }
        if (text.contains("save")) {
            return "fas-save";
        }
        if (text.contains("update")) {
            return "fas-edit";
        }
        if (text.contains("re-create") || text.contains("recreate")) {
            return "fas-redo";
        }
        if (text.contains("cancel receipt")) {
            return "fas-ban";
        }
        if (text.contains("cancel") || text.contains("close")) {
            return "fas-times";
        }
        if (buttonType.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
            return "fas-check";
        }
        if (buttonType.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
            return "fas-times";
        }
        return "fas-check";
    }

    private static void close(Dialog<?> dialog) {
        dialog.setResult(null);
        if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
            dialog.getDialogPane().getScene().getWindow().hide();
            return;
        }
        dialog.close();
    }

    private static void makeSceneTransparent(DialogPane dialogPane) {
        if (dialogPane.getScene() != null) {
            dialogPane.getScene().setFill(Color.TRANSPARENT);
        }
    }

    private static void enableWindowDrag(Node dragHandle) {
        final double[] dragOffset = new double[2];

        dragHandle.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            Window window = dragHandle.getScene() == null ? null : dragHandle.getScene().getWindow();
            if (window == null) {
                return;
            }
            dragOffset[0] = event.getScreenX() - window.getX();
            dragOffset[1] = event.getScreenY() - window.getY();
        });

        dragHandle.addEventHandler(MouseEvent.MOUSE_DRAGGED, event -> {
            Window window = dragHandle.getScene() == null ? null : dragHandle.getScene().getWindow();
            if (window == null) {
                return;
            }
            window.setX(event.getScreenX() - dragOffset[0]);
            window.setY(event.getScreenY() - dragOffset[1]);
        });
    }
}
