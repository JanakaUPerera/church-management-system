package com.churchmanagement.util;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Function;

public class SearchableComboBoxSkin<T> extends ComboBoxListViewSkin<T> {

    private final TextField searchField = new TextField();
    private final ListView<T> listView = new ListView<>();
    private VBox popupContent;
    private FilteredList<T> filteredPopupItems;

    public SearchableComboBoxSkin(ComboBox<T> comboBox, Function<T, String> searchExtractor) {
        super(comboBox);

        searchField.setPromptText("Search...");
        searchField.getStyleClass().add("combo-search-field");

        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        listView.setCellFactory(comboBox.getCellFactory());
        comboBox.cellFactoryProperty().addListener((obs, old, factory) -> listView.setCellFactory(factory));

        setupFilteredList(comboBox.getItems());
        comboBox.itemsProperty().addListener((obs, old, newItems) -> setupFilteredList(newItems));

        comboBox.valueProperty().addListener((obs, old, newValue) -> {
            listView.getSelectionModel().select(newValue);
            listView.scrollTo(newValue);
        });

        // Platform.runLater ensures selection is committed after any focus-change events
        // that fire before MOUSE_RELEASED (fixes first-click not registering)
        listView.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                Platform.runLater(() -> commitSelection(comboBox));
            }
        });

        listView.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> commitSelection(comboBox);
                case ESCAPE -> comboBox.hide();
                default -> { }
            }
        });

        searchField.textProperty().addListener((obs, old, text) -> {
            if (filteredPopupItems == null) return;
            String lower = text == null ? "" : text.toLowerCase();
            filteredPopupItems.setPredicate(item ->
                    lower.isEmpty() || searchExtractor.apply(item).toLowerCase().contains(lower));
        });

        comboBox.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                Platform.runLater(() -> {
                    searchField.clear();
                    listView.getSelectionModel().select(comboBox.getValue());
                    listView.scrollTo(comboBox.getValue());
                    searchField.requestFocus();
                });
            } else {
                searchField.clear();
                if (filteredPopupItems != null) {
                    filteredPopupItems.setPredicate(null);
                }
            }
        });
    }

    @Override
    public Node getPopupContent() {
        if (popupContent == null) {
            searchField.setMaxWidth(Double.MAX_VALUE);

            VBox.setVgrow(listView, Priority.ALWAYS);
            listView.setMinHeight(160);
            listView.setPrefHeight(220);
            listView.setMaxHeight(260);

            popupContent = new VBox(6, searchField, listView);
            popupContent.getStyleClass().add("combo-search-popup");
            popupContent.setPadding(new Insets(8));
            popupContent.setMinHeight(220);
            popupContent.setPrefHeight(300);
            popupContent.prefWidthProperty().bind(getSkinnable().widthProperty());
        }
        return popupContent;
    }

    private void setupFilteredList(ObservableList<T> source) {
        filteredPopupItems = new FilteredList<>(
                source != null ? source : FXCollections.emptyObservableList(),
                t -> true);
        listView.setItems(filteredPopupItems);
    }

    private void commitSelection(ComboBox<T> comboBox) {
        T selected = listView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            comboBox.setValue(selected);
            comboBox.hide();
        }
    }
}
