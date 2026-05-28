package com.churchmanagement.controller;

import com.churchmanagement.dto.RegionDto;
import com.churchmanagement.entity.Region;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.RegionService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.geometry.Pos;

import java.util.Optional;

public class RegionController {
    private final RegionService regionService = new RegionService();
    private final ObservableList<RegionDto> regions = FXCollections.observableArrayList();

    private AuthenticatedUser currentUser;
    private PermissionGuard permissionGuard;
    private RegionDto selectedRegion;

    @FXML
    private TextField regionCodeField;

    @FXML
    private TextField regionNameField;

    @FXML
    private ComboBox<Region.Status> statusComboBox;

    @FXML
    private Button saveButton;

    @FXML
    private Button updateButton;

    @FXML
    private Button clearButton;

    @FXML
    private TextField searchField;

    @FXML
    private TableView<RegionDto> regionTable;

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

        configureForm();
        configureTable();
        applyPermissions();
        refreshRegions();
    }

    @FXML
    private void handleSave() {
        try {
            regionService.create(
                    regionCodeField.getText(),
                    regionNameField.getText(),
                    statusComboBox.getValue(),
                    currentUser.getUserId()
            );
            setMessage("Region created successfully.");
            clearForm();
            refreshRegions();
        } catch (RegionService.RegionException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    @FXML
    private void handleUpdate() {
        if (selectedRegion == null) {
            showFriendlyError("Select a region before updating.");
            return;
        }

        try {
            regionService.update(
                    selectedRegion.getId(),
                    regionCodeField.getText(),
                    regionNameField.getText(),
                    statusComboBox.getValue(),
                    currentUser.getUserId()
            );
            setMessage("Region updated successfully.");
            clearForm();
            refreshRegions();
        } catch (RegionService.RegionException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    @FXML
    private void handleClear() {
        clearForm();
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

    private void configureForm() {
        statusComboBox.setItems(FXCollections.observableArrayList(Region.Status.ACTIVE, Region.Status.INACTIVE));
        statusComboBox.setValue(Region.Status.ACTIVE);
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
            private final Button actionButton = new Button();

            {
                getStyleClass().add("centered-table-cell");
                setAlignment(Pos.CENTER);
                actionButton.getStyleClass().add("table-action-button");
                actionButton.setOnAction(event -> toggleRegionStatus(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }

                RegionDto region = getTableView().getItems().get(getIndex());
                actionButton.setText(region.isActive() ? "Deactivate" : "Activate");
                actionButton.setDisable(!permissionGuard.can("region.delete"));
                setGraphic(actionButton);
            }
        });

        regionTable.setItems(regions);
        regionTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                loadSelectedRegion(newValue);
            }
        });
    }

    private void applyPermissions() {
        saveButton.setVisible(permissionGuard.can("region.create"));
        saveButton.setManaged(saveButton.isVisible());
        updateButton.setVisible(permissionGuard.can("region.update"));
        updateButton.setManaged(updateButton.isVisible());
        actionColumn.setVisible(permissionGuard.can("region.delete"));
        updateActionButtons();
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
        regionCodeField.setText(region.getRegionCode());
        regionNameField.setText(region.getRegionName());
        statusComboBox.setValue(Region.Status.valueOf(region.getStatus()));
        setMessage("Selected region " + region.getRegionCode() + ".");
        updateActionButtons();
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
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Deactivate Region");
        alert.setHeaderText("Deactivate " + region.getRegionCode() + "?");
        alert.setContentText("This region will be hidden from active workflows, but it will not be deleted.");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void clearForm() {
        selectedRegion = null;
        regionTable.getSelectionModel().clearSelection();
        regionCodeField.clear();
        regionNameField.clear();
        statusComboBox.setValue(Region.Status.ACTIVE);
        setMessage("");
        updateActionButtons();
    }

    private void updateActionButtons() {
        if (permissionGuard == null) {
            return;
        }

        boolean editing = selectedRegion != null;
        saveButton.setDisable(editing || !permissionGuard.can("region.create"));
        updateButton.setDisable(!editing || !permissionGuard.can("region.update"));
    }

    private void setFormDisabled(boolean disabled) {
        regionCodeField.setDisable(disabled);
        regionNameField.setDisable(disabled);
        statusComboBox.setDisable(disabled);
        saveButton.setDisable(disabled);
        updateButton.setDisable(disabled);
        clearButton.setDisable(disabled);
        searchField.setDisable(disabled);
        regionTable.setDisable(disabled);
    }

    private void setMessage(String message) {
        messageLabel.setText(message);
    }

    private void showFriendlyError(String message) {
        setMessage(message);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Region Management");
        alert.setHeaderText("Unable to complete action");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
