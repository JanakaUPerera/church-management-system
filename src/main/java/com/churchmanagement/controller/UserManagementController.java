package com.churchmanagement.controller;

import com.churchmanagement.dto.CreateUserRequest;
import com.churchmanagement.dto.ResetPasswordRequest;
import com.churchmanagement.dto.UpdateUserRequest;
import com.churchmanagement.dto.UserDto;
import com.churchmanagement.entity.User;
import com.churchmanagement.repository.UserManagementRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.service.UserManagementService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ProcessingDialog;
import com.churchmanagement.util.TablePaginationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Pagination;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.util.Locale;
import java.util.Optional;

public class UserManagementController {
    private final UserManagementService userManagementService = new UserManagementService();
    private final ObservableList<UserDto> allUsers = FXCollections.observableArrayList();
    private final ObservableList<UserDto> users = FXCollections.observableArrayList();
    private final ObservableList<UserManagementRepository.RoleOption> roles = FXCollections.observableArrayList();

    private AuthenticatedUser currentUser;
    private PermissionGuard permissionGuard;
    private UserDto selectedUser;

    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private ComboBox<UserManagementRepository.RoleOption> roleComboBox;
    @FXML private ComboBox<User.Status> statusComboBox;
    @FXML private PasswordField temporaryPasswordField;
    @FXML private CheckBox forcePasswordChangeCheckBox;
    @FXML private Button saveButton;
    @FXML private Button updateButton;
    @FXML private Button clearButton;
    @FXML private Button resetPasswordButton;
    @FXML private Button toggleStatusButton;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Button refreshButton;
    @FXML private TableView<UserDto> userTable;
    @FXML private Pagination userPagination;
    @FXML private ComboBox<Integer> userItemsPerPageComboBox;
    @FXML private Label userPaginationSummaryLabel;
    @FXML private TableColumn<UserDto, String> usernameColumn;
    @FXML private TableColumn<UserDto, String> fullNameColumn;
    @FXML private TableColumn<UserDto, String> roleColumn;
    @FXML private TableColumn<UserDto, String> statusColumn;
    @FXML private TableColumn<UserDto, String> forcePasswordChangeColumn;
    @FXML private TableColumn<UserDto, String> createdAtColumn;
    @FXML private TableColumn<UserDto, Void> actionColumn;
    @FXML private Label messageLabel;

    @FXML
    private void initialize() {
        Optional<AuthenticatedUser> user = AuthContext.getCurrentUser();
        if (user.isEmpty()) {
            setMessage("Please sign in to manage users.");
            setFormDisabled(true);
            return;
        }

        currentUser = user.get();
        permissionGuard = new PermissionGuard(currentUser);
        if (!permissionGuard.can("user.manage")) {
            setMessage("You do not have permission to manage users.");
            setFormDisabled(true);
            return;
        }

        configureButtonIcons();
        configureForm();
        configureTable();
        refreshRoles();
        refreshUsers();
        clearForm();
    }

    @FXML
    private void handleSave() {
        UserManagementRepository.RoleOption role = roleComboBox.getValue();
        CreateUserRequest request = new CreateUserRequest(usernameField.getText(), fullNameField.getText(),
                role == null ? null : role.id(), statusComboBox.getValue(), temporaryPasswordField.getText(),
                forcePasswordChangeCheckBox.isSelected());

        ProcessingDialog.run("Create User", "Creating user...",
                () -> userManagementService.create(request),
                () -> {
                    setMessage("User created successfully.");
                    clearForm();
                    refreshUsers();
                },
                this::showProcessingError);
    }

    @FXML
    private void handleUpdate() {
        if (selectedUser == null) {
            showFriendlyError("Select a user to update.");
            return;
        }

        UserManagementRepository.RoleOption role = roleComboBox.getValue();
        UpdateUserRequest request = new UpdateUserRequest(usernameField.getText(), fullNameField.getText(),
                role == null ? null : role.id(), statusComboBox.getValue(),
                forcePasswordChangeCheckBox.isSelected());

        ProcessingDialog.run("Update User", "Updating user...",
                () -> userManagementService.update(selectedUser.getId(), request),
                () -> {
                    setMessage("User updated successfully.");
                    clearForm();
                    refreshUsers();
                },
                this::showProcessingError);
    }

    @FXML
    private void handleClear() {
        clearForm();
    }

    @FXML
    private void handleResetPassword() {
        if (selectedUser == null) {
            showFriendlyError("Select a user before resetting the password.");
            return;
        }
        if (!confirmResetPassword(selectedUser)) {
            return;
        }

        ResetPasswordRequest request = new ResetPasswordRequest(temporaryPasswordField.getText());
        ProcessingDialog.run("Reset Password", "Resetting password...",
                () -> userManagementService.resetPassword(selectedUser.getId(), request),
                () -> {
                    setMessage("Password reset successfully. The user must change it at next sign-in.");
                    temporaryPasswordField.clear();
                    refreshUsers();
                },
                this::showProcessingError);
    }

    @FXML
    private void handleToggleStatus() {
        if (selectedUser == null) {
            showFriendlyError("Select a user to activate or deactivate.");
            return;
        }
        toggleUserStatus(selectedUser);
    }

    @FXML
    private void handleSearch() {
        applyUserFilters();
    }

    @FXML
    private void handleRefresh() {
        searchField.clear();
        refreshRoles();
        refreshUsers();
    }

    private void configureForm() {
        statusComboBox.setItems(FXCollections.observableArrayList(User.Status.ACTIVE, User.Status.INACTIVE));
        roleComboBox.setItems(roles);
        roleComboBox.setCellFactory(listView -> new RoleListCell());
        roleComboBox.setButtonCell(new RoleListCell());
    }

    private void configureTable() {
        usernameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getUsername()));
        fullNameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFullName()));
        roleColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRoleName()));
        statusColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus()));
        statusColumn.setCellFactory(column -> new StatusBadgeCell());
        forcePasswordChangeColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getForcePasswordChangeLabel()));
        createdAtColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getCreatedAt()));
        actionColumn.setCellFactory(column -> new ActionButtonCell());

        TablePaginationUtil.configure(userTable, users, userPagination, userItemsPerPageComboBox,
                userPaginationSummaryLabel, "users");
        userTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                loadSelectedUser(newValue);
            }
        });
    }

    private void configureButtonIcons() {
        ButtonIconUtil.applyIcon(saveButton, "fas-save");
        ButtonIconUtil.applyIcon(updateButton, "fas-edit");
        ButtonIconUtil.applyIcon(clearButton, "fas-eraser");
        ButtonIconUtil.applyIcon(resetPasswordButton, "fas-key");
        ButtonIconUtil.applyIcon(toggleStatusButton, "fas-toggle-off");
        ButtonIconUtil.applyIcon(searchButton, "fas-search");
        ButtonIconUtil.applyIcon(refreshButton, "fas-sync-alt");
    }

    private void refreshRoles() {
        try {
            roles.setAll(userManagementService.findRoles());
        } catch (UserManagementService.UserManagementException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void refreshUsers() {
        try {
            allUsers.setAll(userManagementService.findAll().stream().map(UserDto::fromUser).toList());
            applyUserFilters();
        } catch (UserManagementService.UserManagementException exception) {
            showFriendlyError(exception.getMessage());
        }
    }

    private void applyUserFilters() {
        String query = searchField == null || searchField.getText() == null
                ? ""
                : searchField.getText().strip().toLowerCase(Locale.ROOT);
        users.setAll(allUsers.stream()
                .filter(user -> query.isBlank() || contains(user.getUsername(), query)
                        || contains(user.getFullName(), query) || contains(user.getRoleName(), query))
                .toList());
        setMessage("Showing " + users.size() + " user(s).");
    }

    private void loadSelectedUser(UserDto user) {
        selectedUser = user;
        usernameField.setText(user.getUsername());
        fullNameField.setText(user.getFullName());
        roleComboBox.getSelectionModel().select(findRole(user.getRoleId()));
        statusComboBox.setValue(User.Status.valueOf(user.getStatus()));
        forcePasswordChangeCheckBox.setSelected(user.isForcePasswordChange());
        temporaryPasswordField.clear();
        saveButton.setDisable(true);
        updateButton.setDisable(false);
        resetPasswordButton.setDisable(false);
        toggleStatusButton.setDisable(false);
        updateStatusButtonText(user);
        setMessage("Selected user " + user.getUsername() + ".");
    }

    private void toggleUserStatus(UserDto user) {
        if (user.isActive() && !confirmDeactivate(user)) {
            return;
        }

        ProcessingDialog.run(user.isActive() ? "Deactivate User" : "Activate User",
                user.isActive() ? "Deactivating user..." : "Activating user...",
                () -> {
                    if (user.isActive()) {
                        userManagementService.deactivate(user.getId());
                    } else {
                        userManagementService.activate(user.getId());
                    }
                },
                () -> {
                    setMessage(user.isActive() ? "User deactivated successfully." : "User activated successfully.");
                    clearForm();
                    refreshUsers();
                },
                this::showProcessingError);
    }

    private boolean confirmDeactivate(UserDto user) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.CONFIRMATION));
        alert.setTitle("Deactivate User");
        alert.setHeaderText("Deactivate " + user.getUsername() + "?");
        alert.setContentText("This user will not be able to sign in until reactivated.");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private boolean confirmResetPassword(UserDto user) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.CONFIRMATION));
        alert.setTitle("Reset Password");
        alert.setHeaderText("Reset password for " + user.getUsername() + "?");
        alert.setContentText("The temporary password will be saved securely and the user must change it next time.");
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private void clearForm() {
        selectedUser = null;
        userTable.getSelectionModel().clearSelection();
        usernameField.clear();
        fullNameField.clear();
        roleComboBox.getSelectionModel().clearSelection();
        statusComboBox.setValue(User.Status.ACTIVE);
        temporaryPasswordField.clear();
        forcePasswordChangeCheckBox.setSelected(true);
        saveButton.setDisable(false);
        updateButton.setDisable(true);
        resetPasswordButton.setDisable(true);
        toggleStatusButton.setDisable(true);
        toggleStatusButton.setText("Deactivate");
        ButtonIconUtil.applyIcon(toggleStatusButton, "fas-toggle-off");
    }

    private void updateStatusButtonText(UserDto user) {
        toggleStatusButton.setText(user.isActive() ? "Deactivate" : "Activate");
        ButtonIconUtil.applyIcon(toggleStatusButton, user.isActive() ? "fas-toggle-off" : "fas-toggle-on");
    }

    private UserManagementRepository.RoleOption findRole(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return roles.stream().filter(role -> role.id() == roleId).findFirst().orElse(null);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void setFormDisabled(boolean disabled) {
        usernameField.setDisable(disabled);
        fullNameField.setDisable(disabled);
        roleComboBox.setDisable(disabled);
        statusComboBox.setDisable(disabled);
        temporaryPasswordField.setDisable(disabled);
        forcePasswordChangeCheckBox.setDisable(disabled);
        saveButton.setDisable(disabled);
        updateButton.setDisable(disabled);
        clearButton.setDisable(disabled);
        resetPasswordButton.setDisable(disabled);
        toggleStatusButton.setDisable(disabled);
        searchField.setDisable(disabled);
        searchButton.setDisable(disabled);
        refreshButton.setDisable(disabled);
        userTable.setDisable(disabled);
        userPagination.setDisable(disabled);
        userItemsPerPageComboBox.setDisable(disabled);
    }

    private void setMessage(String message) {
        if (messageLabel != null) {
            messageLabel.setText(message);
        }
    }

    private void showFriendlyError(String message) {
        setMessage(message);
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("User Management");
        alert.setHeaderText("Unable to complete action");
        alert.setContentText(message == null || message.isBlank() ? "Action failed. Please try again." : message);
        alert.showAndWait();
    }

    private void showProcessingError(Throwable throwable) {
        String message = throwable.getMessage() == null ? "Action failed. Please try again." : throwable.getMessage();
        showFriendlyError(message);
    }

    private static class RoleListCell extends ListCell<UserManagementRepository.RoleOption> {
        @Override
        protected void updateItem(UserManagementRepository.RoleOption role, boolean empty) {
            super.updateItem(role, empty);
            setText(empty || role == null ? null : role.name());
        }
    }

    private static class StatusBadgeCell extends TableCell<UserDto, String> {
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

    private class ActionButtonCell extends TableCell<UserDto, Void> {
        private final Button editButton = new Button();
        private final Button statusButton = new Button();
        private final HBox actionBox = new HBox(6, editButton, statusButton);

        private ActionButtonCell() {
            getStyleClass().add("centered-table-cell");
            setAlignment(Pos.CENTER);
            actionBox.setAlignment(Pos.CENTER);
            editButton.getStyleClass().add("table-action-button");
            statusButton.getStyleClass().add("table-action-button");
            ButtonIconUtil.applyTableActionIcon(editButton, "fas-edit", "Edit user");
            editButton.setOnAction(event -> loadSelectedUser(getTableView().getItems().get(getIndex())));
            statusButton.setOnAction(event -> toggleUserStatus(getTableView().getItems().get(getIndex())));
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || getIndex() >= getTableView().getItems().size()) {
                setGraphic(null);
                return;
            }
            UserDto user = getTableView().getItems().get(getIndex());
            ButtonIconUtil.applyTableActionIcon(statusButton,
                    user.isActive() ? "fas-toggle-off" : "fas-toggle-on",
                    user.isActive() ? "Deactivate user" : "Activate user");
            setGraphic(actionBox);
        }
    }
}
