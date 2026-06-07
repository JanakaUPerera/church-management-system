package com.churchmanagement.util;

import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class DatePickerUtil {
    private DatePickerUtil() {
    }

    public static void enableMondaysOnly(DatePicker datePicker) {
        applySystemDateFormat(datePicker);
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(java.time.LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.getDayOfWeek() != DayOfWeek.MONDAY);
            }
        });
        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && !isMonday(newValue)) {
                datePicker.setValue(oldValue);
            }
        });
    }

    public static void applySystemDateFormat(DatePicker datePicker) {
        if (datePicker == null) {
            return;
        }
        datePicker.setConverter(new StringConverter<>() {
            private final SystemDateTimeFormatter formatter = new SystemDateTimeFormatter();

            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : formatter.formatDate(date);
            }

            @Override
            public LocalDate fromString(String value) {
                if (value == null || value.isBlank()) {
                    return null;
                }
                String text = value.strip();
                try {
                    return LocalDate.parse(text, formatter.dateFormatter());
                } catch (DateTimeParseException exception) {
                    return LocalDate.parse(text);
                }
            }
        });
    }

    private static boolean isMonday(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.MONDAY;
    }
}
