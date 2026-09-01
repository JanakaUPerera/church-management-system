package com.churchmanagement.util;

import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.function.Supplier;

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

    public static void disableFutureDates(DatePicker datePicker) {
        applySystemDateFormat(datePicker);
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isAfter(LocalDate.now()));
            }
        });
        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.isAfter(LocalDate.now())) {
                datePicker.setValue(oldValue);
            }
        });
    }

    public static void enableMondaysOnlyAndDisableFutureDates(DatePicker datePicker) {
        applySystemDateFormat(datePicker);
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || !isMonday(date) || date.isAfter(LocalDate.now()));
            }
        });
        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && (!isMonday(newValue) || newValue.isAfter(LocalDate.now()))) {
                datePicker.setValue(oldValue);
            }
        });
    }

    public static void enableDayOfWeekOnly(DatePicker datePicker, DayOfWeek allowedDay) {
        applySystemDateFormat(datePicker);
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.getDayOfWeek() != allowedDay);
            }
        });
        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.getDayOfWeek() != allowedDay) {
                datePicker.setValue(oldValue);
            }
        });
    }

    public static void enableDayOfWeekOnlyAndDisableFutureDates(DatePicker datePicker, DayOfWeek allowedDay) {
        applySystemDateFormat(datePicker);
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.getDayOfWeek() != allowedDay || date.isAfter(LocalDate.now()));
            }
        });
        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && (newValue.getDayOfWeek() != allowedDay || newValue.isAfter(LocalDate.now()))) {
                datePicker.setValue(oldValue);
            }
        });
    }

    public static void restrictToRange(DatePicker datePicker, Supplier<LocalDate> minDate, Supplier<LocalDate> maxDate) {
        applySystemDateFormat(datePicker);
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || isOutsideRange(date, minDate.get(), maxDate.get()));
            }
        });
        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && isOutsideRange(newValue, minDate.get(), maxDate.get())) {
                datePicker.setValue(oldValue);
            }
        });
    }

    private static boolean isOutsideRange(LocalDate date, LocalDate minDate, LocalDate maxDate) {
        return (minDate != null && date.isBefore(minDate)) || (maxDate != null && date.isAfter(maxDate));
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
