package com.churchmanagement.controller;

import com.churchmanagement.dto.RegionDto;
import com.churchmanagement.entity.Region;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.RegionService;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.TablePaginationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
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
import javafx.geometry.Pos;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.Optional;

public class RegionController {
    private final RegionService regionService = new RegionService();
    private final ObservableList<RegionDto> regions = FXCollections.observableArrayList();

    private AuthenticatedUser currentUser;
    private PermissionGuard permissionGuard;
    private RegionDto selectedRegion;

    @FXML
    private Button saveButton;

    @FXML
    private TextField searchField;

    @FXML
    private TableView<RegionDto> regionTable;

    @FXML
    private Pagination regionPagination;

    @FXML
    private ComboBox<Integer> regionItemsPerPageComboBox;

    @FXML
    private Label regionPaginationSummaryLabel;

    @FXML
    private TableColumn<RegionDto, String> codeColumn;

    @FXML
    private TableColumn<RegionDto, String> nameColumn;

    @FXML
    private TableColumn<RegionDto, String> statusColumn;

    @FXML
    private TableColumn<RegionDto, String> createdAtColumn;

    @FXML
    private TableColumn<RegionDto, Void> actionColumn;

    @FXML
    private Label messageLabel;

    @FXML
    private void initialize() {
        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty()) {
            setMessage("Please sign in to manage regions.");
            return;
        }

        currentUser = user.get();
        permissionGuard = new PermissionGuard(currentUser);

        if (!permissionGuard.can("region.view")) {
            setMessage("You do not have permission to view regions.");
            setFormDisabled(true);
            return;
        }

        configureTable();
        applyPermissions();
        refreshRegions();
    }

    @FXML
    private void handleSave() {
        showRegionDialog(null);
    }

    @FXML
    private void handleRefresh() {
        searchField.clear();
        refreshRegions();
    }

    @FXML
    private void handleSearch() {
        try {
            regions.setAll(regionService.search(searchField.getText()).stream()
                    .map(RegionDto::fromRegion)
                    .toList());
            setMessage("Showing " + regions.size() + " region(s).");
        } catch (RegionService.RegionException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void configureTable() {
        codeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRegionCode()));
        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRegionName()));
        statusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus()));
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
        createdAtColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getCreatedAt()));
        createdAtColumn.setCellFactory(column -> new TableCell<>() {
            {
                getStyleClass().add("centered-table-cell");
                setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(String createdAt, boolean empty) {
                super.updateItem(createdAt, empty);
                setText(empty ? null : createdAt);
            }
        });
        actionColumn.setCellFactory(column -> new TableCell<>() {
            private final Button editButton = new Button("Edit");
            private final Button statusButton = new Button();
            private final HBox actionBox = new HBox(6, editButton, statusButton);

            {
                getStyleClass().add("centered-table-cell");
                setAlignment(Pos.CENTER);
                actionBox.setAlignment(Pos.CENTER);
                editButton.getStyleClass().add("table-action-button");
                statusButton.getStyleClass().add("table-action-button");
                editButton.setOnAction(event -> showRegionDialog(getTableView().getItems().get(getIndex())));
                statusButton.setOnAction(event -> toggleRegionStatus(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }

                RegionDto region = getTableView().getItems().get(getIndex());
                editButton.setVisible(permissionGuard.can("region.update"));
                editButton.setManaged(editButton.isVisible());
                statusButton.setVisible(permissionGuard.can("region.delete"));
                statusButton.setManaged(statusButton.isVisible());
                statusButton.setText(region.isActive() ? "Deactivate" : "Activate");
                statusButton.setDisable(!permissionGuard.can("region.delete"));
                setGraphic(actionBox);
            }
        });

        TablePaginationUtil.configure(regionTable, regions, regionPagination, regionItemsPerPageComboBox,
                regionPaginationSummaryLabel, "regions");
        regionTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                loadSelectedRegion(newValue);
            }
        });
    }

    private void applyPermissions() {
        saveButton.setVisible(permissionGuard.can("region.create"));
        saveButton.setManaged(saveButton.isVisible());
        actionColumn.setVisible(permissionGuard.can("region.update") || permissionGuard.can("region.delete"));
    }

    private void refreshRegions() {
        try {
            regions.setAll(regionService.findAll().stream()
                    .map(RegionDto::fromRegion)
                    .toList());
            setMessage("Showing " + regions.size() + " region(s).");
        } catch (RegionService.RegionException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void loadSelectedRegion(RegionDto region) {
        selectedRegion = region;
        setMessage("Selected region " + region.getRegionCode() + ".");
    }

    private void showRegionDialog(RegionDto region) {
        boolean editing = region != null;
        Dialog<ButtonType> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle(editing ? "Update Region" : "Create Region");
        dialog.setHeaderText(editing ? "Update " + region.getRegionCode() : "Create a new region");

        ButtonType saveButtonType = new ButtonType(editing ? "Update" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(regionDialogContent(region));
        dialog.getDialogPane().setPrefWidth(460);

        Button submitButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        submitButton.addEventFilter(ActionEvent.ACTION, event -> {
            RegionDialogFields fields = (RegionDialogFields) dialog.getDialogPane().getContent().getUserData();
            try {
                if (editing) {
                    regionService.update(region.getId(), fields.regionCodeField().getText(),
                            fields.regionNameField().getText(), fields.statusComboBox().getValue(),
                            currentUser.getUserId());
                    clearForm();
                    setMessage("Region updated successfully.");
                } else {
                    regionService.create(fields.regionCodeField().getText(), fields.regionNameField().getText(),
                            fields.statusComboBox().getValue(), currentUser.getUserId());
                    clearForm();
                    setMessage("Region created successfully.");
                }
                refreshRegions();
            } catch (RegionService.RegionException exception) {
                event.consume();
                showFriendlyError(exception.getMessage());
            }
        });

        dialog.showAndWait();
    }

    private GridPane regionDialogContent(RegionDto region) {
        TextField regionCodeField = new TextField();
        regionCodeField.setPromptText("REG001");
        TextField regionNameField = new TextField();
        regionNameField.setPromptText("Colombo Region");
        ComboBox<Region.Status> statusComboBox = new ComboBox<>(
                FXCollections.observableArrayList(Region.Status.ACTIVE, Region.Status.INACTIVE));
        statusComboBox.setMaxWidth(Double.MAX_VALUE);

        if (region == null) {
            statusComboBox.setValue(Region.Status.ACTIVE);
        } else {
            regionCodeField.setText(region.getRegionCode());
            regionNameField.setText(region.getRegionName());
            statusComboBox.setValue(Region.Status.valueOf(region.getStatus()));
        }

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setMinWidth(230);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);
        grid.add(new Label("Region Code"), 0, 0);
        grid.add(regionCodeField, 1, 0);
        grid.add(new Label("Region Name"), 0, 1);
        grid.add(regionNameField, 1, 1);
        grid.add(new Label("Status"), 0, 2);
        grid.add(statusComboBox, 1, 2);
        grid.setUserData(new RegionDialogFields(regionCodeField, regionNameField, statusComboBox));
        return grid;
    }

    private void toggleRegionStatus(RegionDto region) {
        if (!permissionGuard.can("region.delete")) {
            showFriendlyError("You do not have permission to activate or deactivate regions.");
            return;
        }

        if (region.isActive() && !confirmDeactivate(region)) {
            return;
        }

        try {
            if (region.isActive()) {
                regionService.deactivate(region.getId(), currentUser.getUserId());
                setMessage("Region deactivated successfully.");
            } else {
                regionService.activate(region.getId(), currentUser.getUserId());
                setMessage("Region activated successfully.");
            }
            clearForm();
            refreshRegions();
        } catch (RegionService.RegionException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private boolean confirmDeactivate(RegionDto region) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.CONFIRMATION));
        alert.setTitle("Deactivate Region");
        alert.setHeaderText("Deactivate " + region.getRegionCode() + "?");
        alert.setContentText("This region will be hidden from active workflows, but it will not be deleted.");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void clearForm() {
        selectedRegion = null;
        regionTable.getSelectionModel().clearSelection();
        setMessage("");
    }

    private void setFormDisabled(boolean disabled) {
        saveButton.setDisable(disabled);
        searchField.setDisable(disabled);
        regionTable.setDisable(disabled);
        regionPagination.setDisable(disabled);
        regionItemsPerPageComboBox.setDisable(disabled);
    }

    private void setMessage(String message) {
        messageLabel.setText(message);
    }

    private void showFriendlyError(String message) {
        setMessage(message);
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Region Management");
        alert.setHeaderText("Unable to complete action");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private record RegionDialogFields(TextField regionCodeField, TextField regionNameField,
                                      ComboBox<Region.Status> statusComboBox) {
    }
}
