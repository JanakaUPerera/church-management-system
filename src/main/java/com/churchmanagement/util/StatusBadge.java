package com.churchmanagement.util;

import javafx.scene.control.Label;

/** The coloured pill labels used for statuses (styled per theme by .status-badge and its tone classes). */
public final class StatusBadge {
    public enum Tone {
        SUCCESS("status-active"),
        DANGER("status-inactive"),
        WARNING("status-skipped");

        private final String styleClass;

        Tone(String styleClass) {
            this.styleClass = styleClass;
        }
    }

    private StatusBadge() {
    }

    public static Label create() {
        Label badge = new Label();
        badge.getStyleClass().add("status-badge");
        return badge;
    }

    public static Label create(String text, Tone tone) {
        Label badge = create();
        apply(badge, text, tone);
        return badge;
    }

    /** Sets the text and colour, replacing any previous tone. */
    public static void apply(Label badge, String text, Tone tone) {
        if (!badge.getStyleClass().contains("status-badge")) {
            badge.getStyleClass().add("status-badge");
        }
        for (Tone each : Tone.values()) {
            badge.getStyleClass().remove(each.styleClass);
        }
        badge.getStyleClass().add(tone.styleClass);
        badge.setText(text);
    }
}
