package com.churchmanagement.util;

import com.churchmanagement.entity.Church;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

public final class ComboBoxUtil {
    private ComboBoxUtil() {}

    public static void makeChurchSearchable(ComboBox<Church> comboBox, ObservableList<Church> allItems) {
        comboBox.setItems(new FilteredList<>(allItems, c -> true));
        comboBox.setPromptText("Select church...");
        comboBox.setCellFactory(lv -> churchCell());
        comboBox.setButtonCell(churchCell());
        comboBox.setSkin(new SearchableComboBoxSkin<>(comboBox,
                church -> church.getChurchCode() + " - " + church.getChurchName()));
    }

    private static ListCell<Church> churchCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Church church, boolean empty) {
                super.updateItem(church, empty);
                setText(empty || church == null ? null : church.getChurchCode() + " - " + church.getChurchName());
            }
        };
    }
}
