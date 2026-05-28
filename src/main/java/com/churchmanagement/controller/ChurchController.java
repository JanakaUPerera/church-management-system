package com.churchmanagement.controller;

import com.churchmanagement.dto.ChurchDto;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Region;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.ChurchService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.Optional;

public class ChurchController {
    private final ChurchService churchService = new ChurchService();
    private final ObservableList<ChurchDto> churches = FXCollections.observableArrayList();
    private final ObservableList<Region> activeRegions = FXCollections.observableArrayList();

    private AuthenticatedUser currentUser;
    private PermissionGuard permissionGuard;
    private ChurchDto selectedChurch;

    @FXML
    private TextField churchCodeField;

    @FXML
    private TextField churchNameField;

    @FXML
    private ComboBox<Region> regionComboBox;

    @FXML
    private ComboBox<Church.Status> statusComboBox;

    @FXML
    private Button saveButton;

    @FXML
    private Button updateButton;

    @FXML
    private Button clearButton;

    @FXML
    private TextField searchField;

    @FXML
    private TableView<ChurchDto> churchTable;

    @FXML
    private TableColumn<ChurchDto, String> codeColumn;

    @FXML
    private TableColumn<ChurchDto, String> nameColumn;

    @FXML
    private TableColumn<ChurchDto, String> regionCodeColumn;

    @FXML
    private TableColumn<ChurchDto, String> regionNameColumn;

    @FXML
    private TableColumn<ChurchDto, String> statusColumn;

    @FXML
    private TableColumn<ChurchDto, String> createdAtColumn;

    @FXML
    private TableColumn<ChurchDto, Void> actionColumn;

    @FXML
    private Label messageLabel;

    @FXML
    private void initialize() {
        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty()) {
            setMessage("Please sign in to manage churches.");
            return;
        }

        currentUser = user.get();
        permissionGuard = new PermissionGuard(currentUser);

        if (!permissionGuard.can("church.view")) {
            setMessage("You do not have permission to view churches.");
            setFormDisabled(true);
            return;
        }

        configureForm();
        configureTable();
        applyPermissions();
        refreshRegions();
        refreshChurches();
    }

    @FXML
    private void handleSave() {
        try {
            churchService.create(churchCodeField.getText(), churchNameField.getText(), selectedRegionId(),
                    statusComboBox.getValue(), currentUser.getUserId());
            setMessage("Church created successfully.");
            clearForm();
            refreshChurches();
        } catch (ChurchService.ChurchException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    @FXML
    private void handleUpdate() {
        if (selectedChurch == null) {
            showFriendlyError("Select a church before updating.");
            return;
        }

        try {
            churchService.update(selectedChurch.getId(), churchCodeField.getText(), churchNameField.getText(),
                    selectedRegionId(), statusComboBox.getValue(), currentUser.getUserId());
            setMessage("Church updated successfully.");
            clearForm();
            refreshChurches();
        } catch (ChurchService.ChurchException exception) {
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
        refreshChurches();
    }

    @FXML
    private void handleSearch() {
        try {
            churches.setAll(churchService.search(searchField.getText()).stream()
                    .map(ChurchDto::fromChurch)
                    .toList());
            setMessage("Showing " + churches.size() + " church(es).");
        } catch (ChurchService.ChurchException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void configureForm() {
        statusComboBox.setItems(FXCollections.observableArrayList(Church.Status.ACTIVE, Church.Status.INACTIVE));
        statusComboBox.setValue(Church.Status.ACTIVE);
        regionComboBox.setItems(activeRegions);
        regionComboBox.setCellFactory(listView -> new RegionListCell());
        regionComboBox.setButtonCell(new RegionListCell());
    }

    private void configureTable() {
        codeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getChurchCode()));
        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getChurchName()));
        regionCodeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRegionCode()));
        regionNameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRegionName()));
        statusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus()));
        statusColumn.setCellFactory(column -> new StatusBadgeCell());
        createdAtColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getCreatedAt()));
        createdAtColumn.setCellFactory(column -> new CenteredTextCell());
        actionColumn.setCellFactory(column -> new ActionButtonCell());

        churchTable.setItems(churches);
        churchTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                loadSelectedChurch(newValue);
            }
        });
    }

    private void applyPermissions() {
        saveButton.setVisible(permissionGuard.can("church.create"));
        saveButton.setManaged(saveButton.isVisible());
        updateButton.setVisible(permissionGuard.can("church.update"));
        updateButton.setManaged(updateButton.isVisible());
        actionColumn.setVisible(permissionGuard.can("church.delete"));
        updateActionButtons();
    }

    private void refreshRegions() {
        try {
            activeRegions.setAll(churchService.findActiveRegions());
        } catch (ChurchService.ChurchException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void refreshChurches() {
        try {
            churches.setAll(churchService.findAll().stream()
                    .map(ChurchDto::fromChurch)
                    .toList());
            setMessage("Showing " + churches.size() + " church(es).");
        } catch (ChurchService.ChurchException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void loadSelectedChurch(ChurchDto church) {
        selectedChurch = church;
        churchCodeField.setText(church.getChurchCode());
        churchNameField.setText(church.getChurchName());
        statusComboBox.setValue(Church.Status.valueOf(church.getStatus()));
        regionComboBox.getSelectionModel().select(findRegion(church.getRegionId()));
        setMessage("Selected church " + church.getChurchCode() + ".");
        updateActionButtons();
    }

    private Region findRegion(Long regionId) {
        return activeRegions.stream()
                .filter(region -> region.getId().equals(regionId))
                .findFirst()
                .orElse(null);
    }

    private Long selectedRegionId() {
        Region selectedRegion = regionComboBox.getSelectionModel().getSelectedItem();
        return selectedRegion == null ? null : selectedRegion.getId();
    }

    private void toggleChurchStatus(ChurchDto church) {
        if (!permissionGuard.can("church.delete")) {
            showFriendlyError("You do not have permission to activate or deactivate churches.");
            return;
        }

        if (church.isActive() && !confirmDeactivate(church)) {
            return;
        }

        try {
            if (church.isActive()) {
                churchService.deactivate(church.getId(), currentUser.getUserId());
                setMessage("Church deactivated successfully.");
            } else {
                churchService.activate(church.getId(), currentUser.getUserId());
                setMessage("Church activated successfully.");
            }
            clearForm();
            refreshChurches();
        } catch (ChurchService.ChurchException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private boolean confirmDeactivate(ChurchDto church) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Deactivate Church");
        alert.setHeaderText("Deactivate " + church.getChurchCode() + "?");
        alert.setContentText("This church will be hidden from active workflows, but it will not be deleted.");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void clearForm() {
        selectedChurch = null;
        churchTable.getSelectionModel().clearSelection();
        churchCodeField.clear();
        churchNameField.clear();
        regionComboBox.getSelectionModel().clearSelection();
        statusComboBox.setValue(Church.Status.ACTIVE);
        setMessage("");
        updateActionButtons();
    }

    private void updateActionButtons() {
        if (permissionGuard == null) {
            return;
        }

        boolean editing = selectedChurch != null;
        saveButton.setDisable(editing || !permissionGuard.can("church.create"));
        updateButton.setDisable(!editing || !permissionGuard.can("church.update"));
    }

    private void setFormDisabled(boolean disabled) {
        churchCodeField.setDisable(disabled);
        churchNameField.setDisable(disabled);
        regionComboBox.setDisable(disabled);
        statusComboBox.setDisable(disabled);
        saveButton.setDisable(disabled);
        updateButton.setDisable(disabled);
        clearButton.setDisable(disabled);
        searchField.setDisable(disabled);
        churchTable.setDisable(disabled);
    }

    private void setMessage(String message) {
        messageLabel.setText(message);
    }

    private void showFriendlyError(String message) {
        setMessage(message);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Church Management");
        alert.setHeaderText("Unable to complete action");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static class RegionListCell extends ListCell<Region> {
        @Override
        protected void updateItem(Region region, boolean empty) {
            super.updateItem(region, empty);
            setText(empty || region == null ? null : region.getRegionCode() + " - " + region.getRegionName());
        }
    }

    private static class CenteredTextCell extends TableCell<ChurchDto, String> {
        private CenteredTextCell() {
            getStyleClass().add("centered-table-cell");
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            setText(empty ? null : value);
        }
    }

    private static class StatusBadgeCell extends TableCell<ChurchDto, String> {
        private final Label badge = new Label();

        private StatusBadgeCell() {
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
    }

    private class ActionButtonCell extends TableCell<ChurchDto, Void> {
        private final Button actionButton = new Button();

        private ActionButtonCell() {
            getStyleClass().add("centered-table-cell");
            setAlignment(Pos.CENTER);
            actionButton.getStyleClass().add("table-action-button");
            actionButton.setOnAction(event -> toggleChurchStatus(getTableView().getItems().get(getIndex())));
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getIndex() >= getTableView().getItems().size()) {
                setGraphic(null);
                return;
            }

            ChurchDto church = getTableView().getItems().get(getIndex());
            actionButton.setText(church.isActive() ? "Deactivate" : "Activate");
            actionButton.setDisable(!permissionGuard.can("church.delete"));
            setGraphic(actionButton);
        }
    }
}
