package com.churchmanagement.util;

import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;

public final class TablePaginationUtil {
    private static final int DEFAULT_PAGE_SIZE = 25;

    private TablePaginationUtil() {
    }

    public static <T> void configure(TableView<T> table, ObservableList<T> sourceItems, Pagination pagination) {
        configure(table, sourceItems, pagination, null, null, "items", DEFAULT_PAGE_SIZE);
    }

    public static <T> void configure(TableView<T> table, ObservableList<T> sourceItems, Pagination pagination,
                                     int pageSize) {
        configure(table, sourceItems, pagination, null, null, "items", pageSize);
    }

    public static <T> void configure(TableView<T> table, ObservableList<T> sourceItems, Pagination pagination,
                                     ComboBox<Integer> itemsPerPageComboBox, Label summaryLabel) {
        configure(table, sourceItems, pagination, itemsPerPageComboBox, summaryLabel, "items", DEFAULT_PAGE_SIZE);
    }

    public static <T> void configure(TableView<T> table, ObservableList<T> sourceItems, Pagination pagination,
                                     ComboBox<Integer> itemsPerPageComboBox, Label summaryLabel, String itemLabel) {
        configure(table, sourceItems, pagination, itemsPerPageComboBox, summaryLabel, itemLabel, DEFAULT_PAGE_SIZE);
    }

    public static <T> void configure(TableView<T> table, ObservableList<T> sourceItems, Pagination pagination,
                                     ComboBox<Integer> itemsPerPageComboBox, Label summaryLabel, String itemLabel,
                                     int defaultPageSize) {
        ObservableList<T> pageItems = FXCollections.observableArrayList();
        table.setItems(pageItems);
        int[] pageSize = {defaultPageSize};

        if (itemsPerPageComboBox != null) {
            itemsPerPageComboBox.setItems(FXCollections.observableArrayList(10, 25, 50, 100));
            itemsPerPageComboBox.setValue(defaultPageSize);
        }

        Runnable updatePageCount = () -> {
            int pageCount = Math.max(1, (int) Math.ceil((double) sourceItems.size() / pageSize[0]));
            pagination.setPageCount(pageCount);
            if (pagination.getCurrentPageIndex() >= pageCount) {
                pagination.setCurrentPageIndex(pageCount - 1);
            }
        };

        Runnable updateVisibleItems = () -> {
            updatePageCount.run();
            int fromIndex = Math.min(pagination.getCurrentPageIndex() * pageSize[0], sourceItems.size());
            int toIndex = Math.min(fromIndex + pageSize[0], sourceItems.size());
            pageItems.setAll(sourceItems.subList(fromIndex, toIndex));
            updateSummary(summaryLabel, fromIndex, toIndex, sourceItems.size(), itemLabel);
        };

        pagination.setMaxPageIndicatorCount(7);
        pagination.setPageFactory(pageIndex -> {
            updateVisibleItems.run();
            Region emptyPage = new Region();
            emptyPage.setMinHeight(0);
            emptyPage.setPrefHeight(0);
            emptyPage.setMaxHeight(0);
            return emptyPage;
        });

        ChangeListener<Number> pageListener = (observable, oldValue, newValue) -> updateVisibleItems.run();
        pagination.currentPageIndexProperty().addListener(pageListener);
        if (itemsPerPageComboBox != null) {
            itemsPerPageComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == null || newValue <= 0) {
                    return;
                }
                pageSize[0] = newValue;
                pagination.setCurrentPageIndex(0);
                updateVisibleItems.run();
            });
        }

        sourceItems.addListener((ListChangeListener<T>) change -> {
            pagination.setCurrentPageIndex(0);
            updateVisibleItems.run();
        });

        updateVisibleItems.run();
    }

    private static void updateSummary(Label summaryLabel, int fromIndex, int toIndex, int totalItems, String itemLabel) {
        if (summaryLabel == null) {
            return;
        }

        if (totalItems == 0) {
            summaryLabel.setText("0 of 0 " + itemLabel);
            return;
        }

        summaryLabel.setText((fromIndex + 1) + "-" + toIndex + " of " + totalItems + " " + itemLabel);
    }
}
