package com.churchmanagement.util;

import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class DatePickerUtil {
    private DatePickerUtil() {
    }

    public static void enableMondaysOnly(DatePicker datePicker) {
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

    private static boolean isMonday(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.MONDAY;
    }
}
