package com.churchmanagement.controller;

import com.churchmanagement.dto.ChurchCategoryAssignments;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.service.CategoryAssignmentService;
import com.churchmanagement.service.CollectionCategoryService;
import com.churchmanagement.util.DialogStyler;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The branch dialog's Categories tab: one category per collection type for a chosen accounting year. */
public class ChurchCategoryPane extends VBox {
    private static final List<CollectionType> TYPES =
            List.of(CollectionType.TITHES, CollectionType.OFFERTORY, CollectionType.OTHER_DONATIONS);

    private final CategoryAssignmentService assignmentService;
    private final Long churchId;
    private final List<CollectionCategory> allCategories;
    private final ComboBox<Integer> yearComboBox = new ComboBox<>();
    private final Map<CollectionType, ComboBox<CollectionCategory>> combos = new EnumMap<>(CollectionType.class);
    private final Label statusLabel = new Label();
    private Map<CollectionType, Long> loadedIds = Map.of();
    private boolean locked;

    public ChurchCategoryPane(CollectionCategoryService categoryService, CategoryAssignmentService assignmentService,
                              Long churchId, int currentYear) {
        super(12);
        this.assignmentService = assignmentService;
        this.churchId = churchId;
        this.allCategories = categoryService.findAll();
        setPadding(new Insets(12, 0, 0, 0));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(150);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);

        grid.add(DialogStyler.fieldLabel("Accounting Year"), 0, 0);
        grid.add(yearComboBox, 1, 0);
        int row = 1;
        for (CollectionType type : TYPES) {
            ComboBox<CollectionCategory> combo = new ComboBox<>();
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setCellFactory(list -> new CategoryCell());
            combo.setButtonCell(new CategoryCell());
            combos.put(type, combo);
            grid.add(DialogStyler.fieldLabel(type.getDisplayLabel()), 0, row);
            grid.add(combo, 1, row++);
        }
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("field-hint");
        getChildren().addAll(grid, statusLabel);

        if (churchId == null) {
            yearComboBox.setItems(FXCollections.observableArrayList(currentYear));
            yearComboBox.setValue(currentYear);
            yearComboBox.setDisable(true);
            Long defaultId = assignmentService.defaultCategoryId();
            Map<CollectionType, Long> defaults = new EnumMap<>(CollectionType.class);
            TYPES.forEach(type -> defaults.put(type, defaultId));
            showSelection(defaults, false);
            statusLabel.setText("Categories for " + currentYear + ". Default is A; change them if needed.");
        } else {
            yearComboBox.setItems(FXCollections.observableArrayList(assignmentService.selectableYears(churchId)));
            yearComboBox.valueProperty().addListener((obs, oldYear, newYear) -> {
                if (newYear != null) {
                    loadYear(newYear);
                }
            });
            yearComboBox.setValue(currentYear);
        }
    }

    public int selectedYear() {
        return yearComboBox.getValue();
    }

    public Map<CollectionType, Long> selectedCategoryIds() {
        Map<CollectionType, Long> ids = new EnumMap<>(CollectionType.class);
        combos.forEach((type, combo) -> {
            CollectionCategory selected = combo.getValue();
            ids.put(type, selected == null ? null : selected.getId());
        });
        return ids;
    }

    /** True when the shown year is editable and the selection differs from what is stored. */
    public boolean hasEditableChanges() {
        return !locked && !Objects.equals(loadedIds, selectedCategoryIds());
    }

    private void loadYear(int year) {
        ChurchCategoryAssignments assignments = assignmentService.findForChurch(churchId, year);
        locked = assignments.locked();
        loadedIds = assignments.carriedForwardFrom() == null ? assignments.categoryIds() : Map.of();
        showSelection(assignments.categoryIds(), locked);
        if (locked) {
            statusLabel.setText("🔒 " + assignments.lockReason()
                    + " Set up a future year to change categories.");
        } else if (assignments.carriedForwardFrom() != null) {
            statusLabel.setText("Not set. Will carry forward from " + assignments.carriedForwardFrom()
                    + " on the first receipt. Click Update to save these now.");
        } else if (assignments.categoryIds().isEmpty()) {
            statusLabel.setText("Not set. Choose a category for each collection type.");
        } else {
            statusLabel.setText("Editable until the first receipt of " + year + " is entered.");
        }
    }

    private void showSelection(Map<CollectionType, Long> categoryIds, boolean disable) {
        for (CollectionType type : TYPES) {
            ComboBox<CollectionCategory> combo = combos.get(type);
            Long selectedId = categoryIds.get(type);
            // Active categories, plus the assigned one if it is somehow inactive (defensive; R6 normally prevents it).
            combo.setItems(FXCollections.observableArrayList(allCategories.stream()
                    .filter(category -> category.isActive() || category.getId().equals(selectedId))
                    .toList()));
            combo.setValue(allCategories.stream()
                    .filter(category -> category.getId().equals(selectedId))
                    .findFirst().orElse(null));
            combo.setDisable(disable);
        }
    }

    private static class CategoryCell extends ListCell<CollectionCategory> {
        @Override
        protected void updateItem(CollectionCategory category, boolean empty) {
            super.updateItem(category, empty);
            setText(empty || category == null ? null
                    : category.displayText() + (category.isActive() ? "" : " (inactive)"));
        }
    }
}
