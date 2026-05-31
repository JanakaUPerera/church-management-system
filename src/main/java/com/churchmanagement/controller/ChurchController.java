package com.churchmanagement.controller;

import com.churchmanagement.dto.ChurchDto;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Region;
import com.churchmanagement.enums.AuthorizedPersonPosition;
import com.churchmanagement.enums.ReceiptLanguage;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.ChurchService;
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
import javafx.scene.control.ListCell;
import javafx.scene.control.Pagination;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.Optional;
import java.util.Locale;

public class ChurchController {
    private final ChurchService churchService = new ChurchService();
    private final ObservableList<ChurchDto> allChurches = FXCollections.observableArrayList();
    private final ObservableList<ChurchDto> churches = FXCollections.observableArrayList();
    private final ObservableList<Region> activeRegions = FXCollections.observableArrayList();
    private final ObservableList<Region> regionFilterOptions = FXCollections.observableArrayList();

    private AuthenticatedUser currentUser;
    private PermissionGuard permissionGuard;
    private ChurchDto selectedChurch;

    @FXML
    private Button saveButton;

    @FXML
    private Button searchButton;

    @FXML
    private Button refreshButton;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<Region> regionFilterComboBox;

    @FXML
    private TableView<ChurchDto> churchTable;

    @FXML
    private Pagination churchPagination;

    @FXML
    private ComboBox<Integer> churchItemsPerPageComboBox;

    @FXML
    private Label churchPaginationSummaryLabel;

    @FXML
    private TableColumn<ChurchDto, String> codeColumn;

    @FXML
    private TableColumn<ChurchDto, String> nameColumn;

    @FXML
    private TableColumn<ChurchDto, String> regionColumn;

    @FXML
    private TableColumn<ChurchDto, String> authorizedPersonColumn;

    @FXML
    private TableColumn<ChurchDto, String> smsMobileNumberColumn;

    @FXML
    private TableColumn<ChurchDto, String> receiptLanguageColumn;

    @FXML
    private TableColumn<ChurchDto, String> statusColumn;

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

        configureButtonIcons();
        configureTable();
        applyPermissions();
        refreshRegions();
        refreshChurches();
    }

    @FXML
    private void handleSave() {
        showChurchDialog(null);
    }

    @FXML
    private void handleRefresh() {
        searchField.clear();
        regionFilterComboBox.getSelectionModel().selectFirst();
        refreshRegions();
        refreshChurches();
    }

    @FXML
    private void handleSearch() {
        applyChurchFilters();
    }

    private void configureTable() {
        codeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getChurchCode()));
        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getChurchName()));
        regionColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getRegionCode() + " - " + cellData.getValue().getRegionName()
        ));
        authorizedPersonColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToBlank(cellData.getValue().getAuthorizedPersonName())
        ));
        smsMobileNumberColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                nullToBlank(cellData.getValue().getSmsMobileNumber())
        ));
        receiptLanguageColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getReceiptLanguageLabel()
        ));
        statusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus()));
        statusColumn.setCellFactory(column -> new StatusBadgeCell());
        actionColumn.setCellFactory(column -> new ActionButtonCell());
        regionFilterComboBox.setItems(regionFilterOptions);
        regionFilterComboBox.setCellFactory(listView -> new RegionListCell());
        regionFilterComboBox.setButtonCell(new RegionListCell());
        regionFilterComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applyChurchFilters());

        TablePaginationUtil.configure(churchTable, churches, churchPagination, churchItemsPerPageComboBox,
                churchPaginationSummaryLabel, "churches");
        churchTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                loadSelectedChurch(newValue);
            }
        });
    }

    private void applyPermissions() {
        saveButton.setVisible(permissionGuard.can("church.create"));
        saveButton.setManaged(saveButton.isVisible());
        actionColumn.setVisible(permissionGuard.can("church.update") || permissionGuard.can("church.delete"));
    }

    private void configureButtonIcons() {
        ButtonIconUtil.applyIcon(saveButton, "fas-plus");
        ButtonIconUtil.applyIcon(searchButton, "fas-search");
        ButtonIconUtil.applyIcon(refreshButton, "fas-sync-alt");
    }

    private void refreshRegions() {
        try {
            activeRegions.setAll(churchService.findActiveRegions());
            Region allRegionsOption = new Region(null, "", "All regions", Region.Status.ACTIVE, null, null);
            regionFilterOptions.setAll(allRegionsOption);
            regionFilterOptions.addAll(activeRegions);
            if (regionFilterComboBox.getSelectionModel().isEmpty()) {
                regionFilterComboBox.getSelectionModel().selectFirst();
            }
        } catch (ChurchService.ChurchException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void refreshChurches() {
        try {
            allChurches.setAll(churchService.findAll().stream()
                    .map(ChurchDto::fromChurch)
                    .toList());
            applyChurchFilters();
        } catch (ChurchService.ChurchException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void applyChurchFilters() {
        String query = searchField == null || searchField.getText() == null
                ? ""
                : searchField.getText().strip().toLowerCase(Locale.ROOT);
        Region selectedRegion = regionFilterComboBox == null ? null : regionFilterComboBox.getValue();
        Long selectedRegionId = selectedRegion == null ? null : selectedRegion.getId();

        churches.setAll(allChurches.stream()
                .filter(church -> selectedRegionId == null || selectedRegionId.equals(church.getRegionId()))
                .filter(church -> query.isBlank() || matchesSearch(church, query))
                .toList());
        setMessage("Showing " + churches.size() + " church(es).");
    }

    private boolean matchesSearch(ChurchDto church, String query) {
        return contains(church.getChurchCode(), query)
                || contains(church.getChurchName(), query)
                || contains(church.getRegionCode(), query)
                || contains(church.getRegionName(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void loadSelectedChurch(ChurchDto church) {
        selectedChurch = church;
        setMessage("Selected church " + church.getChurchCode() + ".");
    }

    private Region findRegion(Long regionId) {
        return activeRegions.stream()
                .filter(region -> region.getId().equals(regionId))
                .findFirst()
                .orElse(null);
    }

    private void toggleChurchStatus(ChurchDto church) {
        if (!permissionGuard.can("church.delete")) {
            showFriendlyError("You do not have permission to activate or deactivate churches.");
            return;
        }

        if (church.isActive() && !confirmDeactivate(church)) {
            return;
        }

        ProcessingDialog.run(church.isActive() ? "Deactivate Church" : "Activate Church",
                church.isActive() ? "Deactivating church..." : "Activating church...",
                () -> {
            if (church.isActive()) {
                churchService.deactivate(church.getId(), currentUser.getUserId());
            } else {
                churchService.activate(church.getId(), currentUser.getUserId());
            }
                },
                () -> {
            setMessage(church.isActive() ? "Church deactivated successfully." : "Church activated successfully.");
            clearForm();
            refreshChurches();
                },
                this::showProcessingError);
    }

    private boolean confirmDeactivate(ChurchDto church) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.CONFIRMATION));
        alert.setTitle("Deactivate Church");
        alert.setHeaderText("Deactivate " + church.getChurchCode() + "?");
        alert.setContentText("This church will be hidden from active workflows, but it will not be deleted.");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void clearForm() {
        selectedChurch = null;
        churchTable.getSelectionModel().clearSelection();
        setMessage("");
    }

    private void setFormDisabled(boolean disabled) {
        saveButton.setDisable(disabled);
        searchField.setDisable(disabled);
        regionFilterComboBox.setDisable(disabled);
        churchTable.setDisable(disabled);
        churchPagination.setDisable(disabled);
        churchItemsPerPageComboBox.setDisable(disabled);
    }

    private void setMessage(String message) {
        if (messageLabel != null) {
            messageLabel.setText(message);
        }
    }

    private void showFriendlyError(String message) {
        setMessage(message);
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Church Management");
        alert.setHeaderText("Unable to complete action");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showProcessingError(Throwable throwable) {
        String message = throwable.getMessage() == null ? "Action failed. Please try again." : throwable.getMessage();
        showFriendlyError(message);
    }

    private void showChurchDialog(ChurchDto church) {
        boolean editing = church != null;
        Dialog<ButtonType> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle(editing ? "Update Church" : "Create Church");
        dialog.setHeaderText(editing ? "Update " + church.getChurchCode() : "Create a new church");

        ButtonType saveButtonType = new ButtonType(editing ? "Update" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(churchDialogContent(church));
        dialog.getDialogPane().setPrefWidth(620);

        Button submitButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        submitButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            submitButton.setDisable(true);
            ChurchDialogFields fields = (ChurchDialogFields) dialog.getDialogPane().getContent().getUserData();
            Region selectedRegion = fields.regionComboBox().getSelectionModel().getSelectedItem();
            Long regionId = selectedRegion == null ? null : selectedRegion.getId();
            ProcessingDialog.run(editing ? "Update Church" : "Create Church",
                    editing ? "Updating church..." : "Creating church...",
                    () -> {
                if (editing) {
                    churchService.update(church.getId(), fields.churchCodeField().getText(),
                            fields.churchNameField().getText(), regionId, fields.statusComboBox().getValue(),
                            fields.authorizedPersonNameField().getText(),
                            fields.authorizedPersonPositionComboBox().getValue(),
                            fields.authorizedPersonPositionOtherField().getText(),
                            fields.smsMobileNumberField().getText(), fields.receiptLanguageComboBox().getValue(),
                            currentUser.getUserId());
                } else {
                    churchService.create(fields.churchCodeField().getText(), fields.churchNameField().getText(),
                            regionId, fields.statusComboBox().getValue(),
                            fields.authorizedPersonNameField().getText(),
                            fields.authorizedPersonPositionComboBox().getValue(),
                            fields.authorizedPersonPositionOtherField().getText(),
                            fields.smsMobileNumberField().getText(), fields.receiptLanguageComboBox().getValue(),
                            currentUser.getUserId());
                }
                    },
                    () -> {
                clearForm();
                refreshChurches();
                setMessage(editing ? "Church updated successfully." : "Church created successfully.");
                dialog.close();
                    },
                    throwable -> {
                submitButton.setDisable(false);
                showProcessingError(throwable);
                    });
        });

        dialog.showAndWait();
    }

    private GridPane churchDialogContent(ChurchDto church) {
        TextField churchCodeField = new TextField();
        churchCodeField.setPromptText("CH001");
        TextField churchNameField = new TextField();
        churchNameField.setPromptText("Main Church");
        ComboBox<Region> regionComboBox = new ComboBox<>(activeRegions);
        regionComboBox.setCellFactory(listView -> new RegionListCell());
        regionComboBox.setButtonCell(new RegionListCell());
        regionComboBox.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Church.Status> statusComboBox = new ComboBox<>(
                FXCollections.observableArrayList(Church.Status.ACTIVE, Church.Status.INACTIVE));
        statusComboBox.setMaxWidth(Double.MAX_VALUE);
        TextField authorizedPersonNameField = new TextField();
        authorizedPersonNameField.setPromptText("Name");
        ComboBox<AuthorizedPersonPosition> authorizedPersonPositionComboBox = new ComboBox<>(
                FXCollections.observableArrayList(AuthorizedPersonPosition.values()));
        authorizedPersonPositionComboBox.setMaxWidth(Double.MAX_VALUE);
        Label otherPositionLabel = DialogStyler.fieldLabel("Other Position");
        TextField authorizedPersonPositionOtherField = new TextField();
        authorizedPersonPositionOtherField.setPromptText("Position");
        TextField smsMobileNumberField = new TextField();
        smsMobileNumberField.setPromptText("0771234567 or +94771234567");
        ComboBox<ReceiptLanguage> receiptLanguageComboBox = new ComboBox<>(
                FXCollections.observableArrayList(ReceiptLanguage.values()));
        receiptLanguageComboBox.setMaxWidth(Double.MAX_VALUE);
        receiptLanguageComboBox.setCellFactory(listView -> new ReceiptLanguageListCell());
        receiptLanguageComboBox.setButtonCell(new ReceiptLanguageListCell());

        Runnable updateOtherPositionVisibility = () -> {
            boolean otherSelected = authorizedPersonPositionComboBox.getValue() == AuthorizedPersonPosition.OTHER;
            otherPositionLabel.setVisible(otherSelected);
            otherPositionLabel.setManaged(otherSelected);
            authorizedPersonPositionOtherField.setVisible(otherSelected);
            authorizedPersonPositionOtherField.setManaged(otherSelected);
            authorizedPersonPositionOtherField.setDisable(!otherSelected);
            if (!otherSelected) {
                authorizedPersonPositionOtherField.clear();
            }
        };
        authorizedPersonPositionComboBox.valueProperty()
                .addListener((observable, oldValue, newValue) -> updateOtherPositionVisibility.run());

        if (church == null) {
            statusComboBox.setValue(Church.Status.ACTIVE);
            receiptLanguageComboBox.setValue(ReceiptLanguage.ENGLISH);
        } else {
            churchCodeField.setText(church.getChurchCode());
            churchNameField.setText(church.getChurchName());
            regionComboBox.getSelectionModel().select(findRegion(church.getRegionId()));
            statusComboBox.setValue(Church.Status.valueOf(church.getStatus()));
            authorizedPersonNameField.setText(church.getAuthorizedPersonName());
            authorizedPersonPositionComboBox.setValue(church.getAuthorizedPersonPosition());
            authorizedPersonPositionOtherField.setText(church.getAuthorizedPersonPositionOther());
            smsMobileNumberField.setText(church.getSmsMobileNumber());
            receiptLanguageComboBox.setValue(church.getReceiptLanguage());
        }
        updateOtherPositionVisibility.run();

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(170);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setMinWidth(360);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);

        grid.add(DialogStyler.fieldLabel("Church Code"), 0, 0);
        grid.add(churchCodeField, 1, 0);
        grid.add(DialogStyler.fieldLabel("Church Name"), 0, 1);
        grid.add(churchNameField, 1, 1);
        grid.add(DialogStyler.fieldLabel("Region"), 0, 2);
        grid.add(regionComboBox, 1, 2);
        grid.add(DialogStyler.fieldLabel("Status"), 0, 3);
        grid.add(statusComboBox, 1, 3);
        grid.add(DialogStyler.fieldLabel("Authorized Person Name"), 0, 4);
        grid.add(authorizedPersonNameField, 1, 4);
        grid.add(DialogStyler.fieldLabel("Position"), 0, 5);
        grid.add(authorizedPersonPositionComboBox, 1, 5);
        grid.add(otherPositionLabel, 0, 6);
        grid.add(authorizedPersonPositionOtherField, 1, 6);
        grid.add(DialogStyler.fieldLabel("SMS Mobile Number"), 0, 7);
        grid.add(smsMobileNumberField, 1, 7);
        grid.add(DialogStyler.fieldLabel("Receipt Language"), 0, 8);
        grid.add(receiptLanguageComboBox, 1, 8);

        grid.setUserData(new ChurchDialogFields(churchCodeField, churchNameField, regionComboBox, statusComboBox,
                authorizedPersonNameField, authorizedPersonPositionComboBox, authorizedPersonPositionOtherField,
                smsMobileNumberField, receiptLanguageComboBox));
        return grid;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private record ChurchDialogFields(TextField churchCodeField, TextField churchNameField,
                                      ComboBox<Region> regionComboBox,
                                      ComboBox<Church.Status> statusComboBox,
                                      TextField authorizedPersonNameField,
                                      ComboBox<AuthorizedPersonPosition> authorizedPersonPositionComboBox,
                                      TextField authorizedPersonPositionOtherField,
                                      TextField smsMobileNumberField,
                                      ComboBox<ReceiptLanguage> receiptLanguageComboBox) {
    }

    private static class ReceiptLanguageListCell extends ListCell<ReceiptLanguage> {
        @Override
        protected void updateItem(ReceiptLanguage language, boolean empty) {
            super.updateItem(language, empty);
            setText(empty || language == null ? null : language.getDisplayLabel());
        }
    }

    private static class RegionListCell extends ListCell<Region> {
        @Override
        protected void updateItem(Region region, boolean empty) {
            super.updateItem(region, empty);
            if (empty || region == null) {
                setText(null);
            } else if (region.getId() == null) {
                setText(region.getRegionName());
            } else {
                setText(region.getRegionCode() + " - " + region.getRegionName());
            }
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
        private final Button editButton = new Button();
        private final Button statusButton = new Button();
        private final HBox actionBox = new HBox(6, editButton, statusButton);

        private ActionButtonCell() {
            getStyleClass().add("centered-table-cell");
            setAlignment(Pos.CENTER);
            actionBox.setAlignment(Pos.CENTER);
            editButton.getStyleClass().add("table-action-button");
            statusButton.getStyleClass().add("table-action-button");
            ButtonIconUtil.applyTableActionIcon(editButton, "fas-edit", "Edit church");
            editButton.setOnAction(event -> showChurchDialog(getTableView().getItems().get(getIndex())));
            statusButton.setOnAction(event -> toggleChurchStatus(getTableView().getItems().get(getIndex())));
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getIndex() >= getTableView().getItems().size()) {
                setGraphic(null);
                return;
            }

            ChurchDto church = getTableView().getItems().get(getIndex());
            editButton.setVisible(permissionGuard.can("church.update"));
            editButton.setManaged(editButton.isVisible());
            statusButton.setVisible(permissionGuard.can("church.delete"));
            statusButton.setManaged(statusButton.isVisible());
            ButtonIconUtil.applyTableActionIcon(statusButton,
                    church.isActive() ? "fas-toggle-off" : "fas-toggle-on",
                    church.isActive() ? "Deactivate church" : "Activate church");
            statusButton.setDisable(!permissionGuard.can("church.delete"));
            setGraphic(actionBox);
        }
    }
}
