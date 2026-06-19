package com.churchmanagement.controller;

import com.churchmanagement.dto.PermissionDto;
import com.churchmanagement.dto.RoleDto;
import com.churchmanagement.dto.RolePermissionUpdateRequest;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.RolePermissionService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ProcessingDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RolePermissionController {
    private static final List<String> MODULE_DISPLAY_ORDER = List.of(
            "Regions",
            "Churches",
            "Receipts",
            "Reports",
            "User Management",
            "Roles & Permissions",
            "Backup",
            "Activity Logs",
            "SMS Logs",
            "Settings"
    );
    private static final Map<String, Integer> MODULE_ORDER = buildOrderMap(MODULE_DISPLAY_ORDER);
    private static final Map<String, Integer> ACTION_ORDER = buildOrderMap(List.of(
            "menu",
            "create",
            "view",
            "edit",
            "delete"
    ));

    private final RolePermissionService rolePermissionService = new RolePermissionService();
    private final ObservableList<RoleDto> roles = FXCollections.observableArrayList();
    private final List<PermissionDto> permissions = new ArrayList<>();
    private final Map<String, CheckBox> permissionCheckBoxes = new LinkedHashMap<>();

    private RoleDto selectedRole;

    @FXML private TableView<RoleDto> roleTable;
    @FXML private TableColumn<RoleDto, String> roleNameColumn;
    @FXML private TableColumn<RoleDto, String> statusColumn;
    @FXML private TableColumn<RoleDto, String> createdAtColumn;
    @FXML private TextField roleNameField;
    @FXML private Button saveRoleButton;
    @FXML private Button updateRoleButton;
    @FXML private Button statusButton;
    @FXML private Button savePermissionsButton;
    @FXML private VBox permissionGroupsBox;

    @FXML
    private void initialize() {
        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty()) {
            setFormDisabled(true);
            return;
        }

        if (!new PermissionGuard(user.get()).can(RolePermissionService.ROLE_MENU_VIEW_PERMISSION)) {
            setFormDisabled(true);
            return;
        }

        configureButtonIcons();
        configureTable();
        refreshData();
        clearSelection();
    }

    @FXML
    private void handleSaveRole() {
        ProcessingDialog.run("Create Role", "Creating role...",
                () -> rolePermissionService.createRole(roleNameField.getText()),
                () -> {
                    refreshData();
                    clearSelection();
                },
                this::showProcessingError);
    }

    @FXML
    private void handleUpdateRole() {
        if (selectedRole == null) {
            return;
        }

        ProcessingDialog.run("Update Role", "Updating role...",
                () -> rolePermissionService.updateRoleName(selectedRole.getId(), roleNameField.getText()),
                () -> {
                    refreshData();
                    selectRoleById(selectedRole.getId());
                },
                this::showProcessingError);
    }

    @FXML
    private void handleToggleStatus() {
        if (selectedRole == null) {
            return;
        }
        if (selectedRole.isActive() && !confirmDeactivate(selectedRole)) {
            return;
        }

        boolean wasActive = selectedRole.isActive();
        ProcessingDialog.run(wasActive ? "Deactivate Role" : "Activate Role",
                wasActive ? "Deactivating role..." : "Activating role...",
                () -> {
                    if (wasActive) {
                        rolePermissionService.deactivateRole(selectedRole.getId());
                    } else {
                        rolePermissionService.activateRole(selectedRole.getId());
                    }
                },
                () -> {
                    Long roleId = selectedRole.getId();
                    refreshData();
                    selectRoleById(roleId);
                },
                this::showProcessingError);
    }

    @FXML
    private void handleSavePermissions() {
        if (selectedRole == null) {
            return;
        }

        List<String> selectedPermissionCodes = permissionCheckBoxes.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .toList();
        ProcessingDialog.run("Save Permissions", "Saving permissions...",
                () -> rolePermissionService.updateRolePermissions(
                        new RolePermissionUpdateRequest(selectedRole.getId(), selectedPermissionCodes)),
                () -> {
                    loadRolePermissions(selectedRole.getId());
                },
                this::showProcessingError);
    }

    private void configureTable() {
        roleNameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRoleName()));
        statusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus()));
        statusColumn.setCellFactory(column -> new StatusBadgeCell());
        createdAtColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getCreatedAt()));
        roleTable.setItems(roles);
        roleTable.getSelectionModel().selectedItemProperty().addListener((observable, oldRole, newRole) -> {
            if (newRole != null) {
                loadSelectedRole(newRole);
            } else {
                resetForCreateMode();
            }
        });
    }

    private void configureButtonIcons() {
        ButtonIconUtil.applyIcon(saveRoleButton, "fas-plus");
        ButtonIconUtil.applyIcon(updateRoleButton, "fas-save");
        ButtonIconUtil.applyIcon(statusButton, "fas-toggle-on");
        ButtonIconUtil.applyIcon(savePermissionsButton, "fas-user-lock");
    }

    private void refreshData() {
        try {
            roles.setAll(rolePermissionService.findRoles().stream().map(RoleDto::fromRole).toList());
            permissions.clear();
            permissions.addAll(rolePermissionService.findPermissions());
            renderPermissionGroups();
        } catch (RolePermissionService.RolePermissionException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void renderPermissionGroups() {
        permissionGroupsBox.getChildren().clear();
        permissionCheckBoxes.clear();

        Map<String, List<PermissionDto>> permissionsByModule = new LinkedHashMap<>();
        for (PermissionDto permission : sortedPermissions()) {
            permissionsByModule.computeIfAbsent(permission.getModule(), key -> new ArrayList<>()).add(permission);
        }

        for (Map.Entry<String, List<PermissionDto>> group : permissionsByModule.entrySet()) {
            VBox groupBox = new VBox(8);
            groupBox.getStyleClass().add("permission-checklist");
            for (PermissionDto permission : group.getValue()) {
                CheckBox checkBox = new CheckBox(permission.getDescription());
                checkBox.setWrapText(true);
                permissionCheckBoxes.put(permission.getPermissionCode(), checkBox);
                groupBox.getChildren().add(checkBox);
            }
            TitledPane titledPane = new TitledPane(group.getKey(), groupBox);
            titledPane.getStyleClass().add("permission-module-pane");
            titledPane.setExpanded(true);
            permissionGroupsBox.getChildren().add(titledPane);
        }
    }

    private List<PermissionDto> sortedPermissions() {
        return permissions.stream()
                .sorted(Comparator
                        .comparingInt((PermissionDto permission) -> moduleOrder(permission.getModule()))
                        .thenComparingInt(permission -> ACTION_ORDER.getOrDefault(actionKey(permission),
                                ACTION_ORDER.size()))
                        .thenComparing(PermissionDto::getDescription, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static int moduleOrder(String module) {
        if (module != null && module.toLowerCase().contains("settings")) {
            return MODULE_ORDER.size() + 1;
        }
        return MODULE_ORDER.getOrDefault(module, MODULE_ORDER.size());
    }

    private static String actionKey(PermissionDto permission) {
        String permissionCode = permission.getPermissionCode();
        if (permissionCode == null || permissionCode.isBlank()) {
            return "";
        }
        if ("menu.view".equals(permissionCode) || permissionCode.endsWith(".menu.view")) {
            return "menu";
        }
        String action = permissionCode.substring(permissionCode.lastIndexOf('.') + 1);
        if ("update".equals(action)) {
            return "edit";
        }
        return action;
    }

    private static Map<String, Integer> buildOrderMap(List<String> values) {
        Map<String, Integer> order = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index++) {
            order.put(values.get(index), index);
        }
        return order;
    }

    private void loadSelectedRole(RoleDto role) {
        selectedRole = role;
        roleNameField.setText(role.getRoleName());
        setEditMode(true);
        updateStatusButton();
        loadRolePermissions(role.getId());
    }

    private void loadRolePermissions(long roleId) {
        try {
            List<String> rolePermissionCodes = new ArrayList<>(rolePermissionService.findPermissionCodesByRoleId(roleId));
            permissionCheckBoxes.forEach((permissionCode, checkBox) ->
                    checkBox.setSelected(rolePermissionCodes.contains(permissionCode)));
        } catch (RolePermissionService.RolePermissionException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void clearSelection() {
        selectedRole = null;
        roleNameField.clear();
        roleTable.getSelectionModel().clearSelection();
        resetForCreateMode();
    }

    private void resetForCreateMode() {
        selectedRole = null;
        roleNameField.clear();
        permissionCheckBoxes.values().forEach(checkBox -> checkBox.setSelected(false));
        setEditMode(false);
        updateStatusButton();
    }

    private void setEditMode(boolean editing) {
        saveRoleButton.setDisable(editing);
        updateRoleButton.setDisable(!editing);
        statusButton.setDisable(!editing);
        savePermissionsButton.setDisable(!editing);
    }

    private void selectRoleById(Long roleId) {
        if (roleId == null) {
            clearSelection();
            return;
        }
        roles.stream()
                .filter(role -> roleId.equals(role.getId()))
                .findFirst()
                .ifPresent(role -> roleTable.getSelectionModel().select(role));
    }

    private boolean confirmDeactivate(RoleDto role) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.CONFIRMATION));
        alert.setTitle("Deactivate Role");
        alert.setHeaderText("Deactivate " + role.getRoleName() + "?");
        alert.setContentText("Users with this role may lose access until the role is reactivated.");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void updateStatusButton() {
        if (selectedRole == null || selectedRole.isActive()) {
            statusButton.setText("Deactivate");
            ButtonIconUtil.applyIcon(statusButton, "fas-toggle-off");
        } else {
            statusButton.setText("Activate");
            ButtonIconUtil.applyIcon(statusButton, "fas-toggle-on");
        }
    }

    private void setFormDisabled(boolean disabled) {
        roleTable.setDisable(disabled);
        roleNameField.setDisable(disabled);
        saveRoleButton.setDisable(disabled);
        updateRoleButton.setDisable(true);
        statusButton.setDisable(true);
        savePermissionsButton.setDisable(true);
        permissionGroupsBox.setDisable(disabled);
    }

    private void showFriendlyError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Roles & Permissions");
        alert.setHeaderText("Unable to complete action");
        alert.setContentText(message == null || message.isBlank() ? "Action failed. Please try again." : message);
        alert.showAndWait();
    }

    private void showProcessingError(Throwable throwable) {
        String message = throwable.getMessage() == null ? "Action failed. Please try again." : throwable.getMessage();
        showFriendlyError(message);
    }

    private static class StatusBadgeCell extends TableCell<RoleDto, String> {
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
}
