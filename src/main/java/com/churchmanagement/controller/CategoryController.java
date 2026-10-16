package com.churchmanagement.controller;

import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.CollectionCategoryService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ProcessingDialog;
import com.churchmanagement.util.TablePaginationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class CategoryController {
    private final CollectionCategoryService categoryService = new CollectionCategoryService();
    private final ObservableList<CollectionCategory> categories = FXCollections.observableArrayList();
    private Map<Long, Integer> churchCounts = new HashMap<>();

    private AuthenticatedUser currentUser;
    private PermissionGuard permissionGuard;

    @FXML private Button saveButton;
    @FXML private Button searchButton;
    @FXML private Button refreshButton;
    @FXML private TextField searchField;
    @FXML private TableView<CollectionCategory> categoryTable;
    @FXML private Pagination categoryPagination;
    @FXML private ComboBox<Integer> categoryItemsPerPageComboBox;
    @FXML private Label categoryPaginationSummaryLabel;
    @FXML private TableColumn<CollectionCategory, String> codeColumn;
    @FXML private TableColumn<CollectionCategory, String> nameColumn;
    @FXML private TableColumn<CollectionCategory, String> descriptionColumn;
    @FXML private TableColumn<CollectionCategory, String> statusColumn;
    @FXML private TableColumn<CollectionCategory, String> inUseColumn;
    @FXML private TableColumn<CollectionCategory, Void> actionColumn;

    @FXML
    private void initialize() {
        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty()) {
            return;
        }
        currentUser = user.get();
        permissionGuard = new PermissionGuard(currentUser);
        if (!permissionGuard.can("category.menu.view")) {
            setFormDisabled(true);
            return;
        }

        ButtonIconUtil.applyIcon(saveButton, "fas-plus");
        ButtonIconUtil.applyIcon(searchButton, "fas-search");
        ButtonIconUtil.applyIcon(refreshButton, "fas-sync-alt");
        configureTable();
        saveButton.setVisible(permissionGuard.can("category.create"));
        saveButton.setManaged(saveButton.isVisible());
        actionColumn.setVisible(permissionGuard.can("category.update") || permissionGuard.can("category.delete"));
        refreshCategories();
    }

    @FXML
    private void handleSave() {
        showCategoryDialog(null);
    }

    @FXML
    private void handleSearch() {
        String searchText = searchField.getText();
        ProcessingDialog.run("Search Categories", "Searching categories...",
                () -> categoryService.search(searchText),
                results -> categories.setAll(results),
                throwable -> showFriendlyError(throwable.getMessage() == null
                        ? "Unable to search categories." : throwable.getMessage()));
    }

    @FXML
    private void handleRefresh() {
        searchField.clear();
        refreshCategories();
    }

    private void configureTable() {
        codeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCode()));
        nameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getName()));
        descriptionColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getDescription() == null ? "" : cell.getValue().getDescription()));
        statusColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus().name()));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();

            {
                getStyleClass().add("centered-table-cell");
                setAlignment(Pos.CENTER);
                badge.getStyleClass().add("status-badge");
            }

            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    return;
                }
                badge.setText(status);
                badge.getStyleClass().removeAll("status-active", "status-inactive");
                badge.getStyleClass().add("ACTIVE".equals(status) ? "status-active" : "status-inactive");
                setGraphic(badge);
            }
        });
        inUseColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                String.valueOf(churchCounts.getOrDefault(cell.getValue().getId(), 0))));
        actionColumn.setCellFactory(column -> new TableCell<>() {
            private final Button editButton = new Button();
            private final Button statusButton = new Button();
            private final Button deleteButton = new Button();
            private final HBox actionBox = new HBox(6, editButton, statusButton, deleteButton);

            {
                getStyleClass().add("centered-table-cell");
                setAlignment(Pos.CENTER);
                actionBox.setAlignment(Pos.CENTER);
                editButton.getStyleClass().add("table-action-button");
                statusButton.getStyleClass().add("table-action-button");
                deleteButton.getStyleClass().add("table-action-button");
                ButtonIconUtil.applyTableActionIcon(editButton, "fas-edit", "Edit category");
                ButtonIconUtil.applyTableActionIcon(deleteButton, "fas-trash", "Delete category");
                editButton.setOnAction(event -> showCategoryDialog(getTableView().getItems().get(getIndex())));
                statusButton.setOnAction(event -> toggleStatus(getTableView().getItems().get(getIndex())));
                deleteButton.setOnAction(event -> deleteCategory(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                CollectionCategory category = getTableView().getItems().get(getIndex());
                editButton.setVisible(permissionGuard.can("category.update"));
                editButton.setManaged(editButton.isVisible());
                boolean canDelete = permissionGuard.can("category.delete");
                statusButton.setVisible(canDelete);
                statusButton.setManaged(canDelete);
                deleteButton.setVisible(canDelete);
                deleteButton.setManaged(canDelete);
                ButtonIconUtil.applyTableActionIcon(statusButton,
                        category.isActive() ? "fas-toggle-off" : "fas-toggle-on",
                        category.isActive() ? "Deactivate category" : "Activate category");
                setGraphic(actionBox);
            }
        });

        TablePaginationUtil.configure(categoryTable, categories, categoryPagination, categoryItemsPerPageComboBox,
                categoryPaginationSummaryLabel, "categories");
    }

    private void refreshCategories() {
        try {
            churchCounts = categoryService.churchCountsForYear(LocalDate.now().getYear());
            categories.setAll(categoryService.findAll());
        } catch (CollectionCategoryService.CollectionCategoryException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void showCategoryDialog(CollectionCategory category) {
        boolean editing = category != null;
        Dialog<ButtonType> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle(editing ? "Update Category" : "Create Category");
        dialog.setHeaderText(editing ? "Update " + category.getCode() : "Create a new collection category");

        ButtonType saveButtonType = new ButtonType(editing ? "Update" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField codeField = new TextField();
        codeField.setPromptText("F");
        TextField nameField = new TextField();
        nameField.setPromptText("Building Fund");
        TextField descriptionField = new TextField();
        ComboBox<CollectionCategory.Status> statusComboBox = new ComboBox<>(FXCollections.observableArrayList(
                CollectionCategory.Status.ACTIVE, CollectionCategory.Status.INACTIVE));
        statusComboBox.setMaxWidth(Double.MAX_VALUE);
        if (editing) {
            codeField.setText(category.getCode());
            nameField.setText(category.getName());
            descriptionField.setText(category.getDescription() == null ? "" : category.getDescription());
            statusComboBox.setValue(category.getStatus());
        } else {
            statusComboBox.setValue(CollectionCategory.Status.ACTIVE);
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setMinWidth(260);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);
        grid.add(DialogStyler.fieldLabel("Code"), 0, 0);
        grid.add(codeField, 1, 0);
        grid.add(DialogStyler.fieldLabel("Name"), 0, 1);
        grid.add(nameField, 1, 1);
        grid.add(DialogStyler.fieldLabel("Description"), 0, 2);
        grid.add(descriptionField, 1, 2);
        grid.add(DialogStyler.fieldLabel("Status"), 0, 3);
        grid.add(statusComboBox, 1, 3);
        dialog.getDialogPane().setContent(grid);

        Button submitButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        submitButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            submitButton.setDisable(true);
            ProcessingDialog.run(editing ? "Update Category" : "Create Category",
                    editing ? "Updating category..." : "Creating category...",
                    () -> {
                if (editing) {
                    categoryService.update(category.getId(), codeField.getText(), nameField.getText(),
                            descriptionField.getText(), statusComboBox.getValue(), currentUser.getUserId());
                } else {
                    categoryService.create(codeField.getText(), nameField.getText(), descriptionField.getText(),
                            statusComboBox.getValue(), currentUser.getUserId());
                }
                    },
                    () -> {
                refreshCategories();
                dialog.close();
                    },
                    throwable -> {
                submitButton.setDisable(false);
                showFriendlyError(throwable.getMessage());
                    });
        });

        dialog.showAndWait();
    }

    private void toggleStatus(CollectionCategory category) {
        if (category.isActive() && !confirm("Deactivate Category", "Deactivate " + category.getCode() + "?",
                "It can no longer be chosen for branch assignments.")) {
            return;
        }
        ProcessingDialog.run(category.isActive() ? "Deactivate Category" : "Activate Category",
                "Updating category...",
                () -> {
            if (category.isActive()) {
                categoryService.deactivate(category.getId(), currentUser.getUserId());
            } else {
                categoryService.activate(category.getId(), currentUser.getUserId());
            }
                },
                this::refreshCategories,
                throwable -> showFriendlyError(throwable.getMessage()));
    }

    private void deleteCategory(CollectionCategory category) {
        if (!confirm("Delete Category", "Delete " + category.getCode() + "?",
                "This cannot be undone.")) {
            return;
        }
        ProcessingDialog.run("Delete Category", "Deleting category...",
                () -> categoryService.delete(category.getId(), currentUser.getUserId()),
                this::refreshCategories,
                throwable -> showFriendlyError(throwable.getMessage()));
    }

    private boolean confirm(String title, String header, String content) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.CONFIRMATION));
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void setFormDisabled(boolean disabled) {
        saveButton.setDisable(disabled);
        searchField.setDisable(disabled);
        categoryTable.setDisable(disabled);
        categoryPagination.setDisable(disabled);
        categoryItemsPerPageComboBox.setDisable(disabled);
    }

    private void showFriendlyError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Category Management");
        alert.setHeaderText("Unable to complete action");
        alert.setContentText(message == null ? "Action failed. Please try again." : message);
        alert.showAndWait();
    }
}
