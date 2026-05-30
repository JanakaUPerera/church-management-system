package com.churchmanagement.util;

import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.javafx.FontIcon;

public final class ButtonIconUtil {
    private ButtonIconUtil() {
    }

    public static void applyIcon(Button button, String iconLiteral) {
        button.setGraphic(createIcon(iconLiteral, "button-icon"));
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(8);
    }

    public static void applyTableActionIcon(Button button, String iconLiteral, String tooltipText) {
        button.setText("");
        button.setGraphic(createIcon(iconLiteral, "table-action-icon"));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setTooltip(new Tooltip(tooltipText));
        button.setMinWidth(34);
        button.setPrefWidth(34);
    }

    private static FontIcon createIcon(String iconLiteral, String styleClass) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add(styleClass);
        return icon;
    }
}
