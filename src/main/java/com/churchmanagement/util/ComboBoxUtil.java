package com.churchmanagement.util;

import com.churchmanagement.entity.Church;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

import java.util.function.Function;

public final class ComboBoxUtil {
    private ComboBoxUtil() {}

    public static <T> void makeSearchable(ComboBox<T> comboBox, Function<T, String> displayTextExtractor) {
        comboBox.setCellFactory(listView -> textCell(displayTextExtractor));
        comboBox.setButtonCell(textCell(displayTextExtractor));
        comboBox.setSkin(new SearchableComboBoxSkin<>(comboBox, displayTextExtractor));
    }

    public static void makeChurchSearchable(ComboBox<Church> comboBox, ObservableList<Church> allItems) {
        comboBox.setItems(new FilteredList<>(allItems, c -> true));
        comboBox.setPromptText("Select church...");
        makeSearchable(comboBox, church -> church.getChurchCode() + " - " + church.getChurchName());
    }

    private static <T> ListCell<T> textCell(Function<T, String> displayTextExtractor) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : displayTextExtractor.apply(item));
            }
        };
    }
}
