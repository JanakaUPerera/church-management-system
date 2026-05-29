package com.churchmanagement.util;

import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

import java.net.URL;

public final class DialogStyler {
    private static final String APP_STYLESHEET = "/com/churchmanagement/view/app.css";
    private static final String APP_DIALOG_PANE = "app-dialog-pane";

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
    }
}
