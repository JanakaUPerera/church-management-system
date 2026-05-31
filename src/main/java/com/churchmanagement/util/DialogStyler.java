package com.churchmanagement.util;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;

import java.net.URL;

public final class DialogStyler {
    private static final String APP_STYLESHEET = "/com/churchmanagement/view/app.css";
    private static final String APP_DIALOG_PANE = "app-dialog-pane";
    private static final String DIALOG_FIELD_LABEL = "dialog-field-label";
    private static final String DIALOG_BUTTON_ICONS_APPLIED = "dialog-button-icons-applied";

    private DialogStyler() {
    }

    public static <T extends Dialog<?>> T apply(T dialog) {
        apply(dialog.getDialogPane());
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
}
