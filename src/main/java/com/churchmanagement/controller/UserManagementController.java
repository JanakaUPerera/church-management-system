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
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Pagination;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

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

    @FXML private Button saveButton;
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
        configureTable();
        refreshRoles();
        refreshUsers();
        clearForm();
    }

    @FXML
    private void handleSave() {
        showUserDialog(null);
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
        ButtonIconUtil.applyIcon(saveButton, "fas-plus");
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

    private void clearForm() {
        selectedUser = null;
        userTable.getSelectionModel().clearSelection();
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
        saveButton.setDisable(disabled);
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

    private void showUserDialog(UserDto user) {
        boolean editing = user != null;
        Dialog<ButtonType> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle(editing ? "Update User" : "Create User");
        dialog.setHeaderText(editing ? "Update " + user.getUsername() : "Create a new user");

        ButtonType saveButtonType = new ButtonType(editing ? "Update" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(userDialogContent(user));
        dialog.getDialogPane().setPrefWidth(560);

        Button submitButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        submitButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            submitButton.setDisable(true);
            UserDialogFields fields = (UserDialogFields) dialog.getDialogPane().getContent().getUserData();
            UserManagementRepository.RoleOption role = fields.roleComboBox().getValue();
            Long roleId = role == null ? null : role.id();
            ProcessingDialog.run(editing ? "Update User" : "Create User",
                    editing ? "Updating user..." : "Creating user...",
                    () -> {
                        if (editing) {
                            userManagementService.update(user.getId(), new UpdateUserRequest(
                                    fields.usernameField().getText(), fields.fullNameField().getText(), roleId,
                                    fields.statusComboBox().getValue(),
                                    fields.forcePasswordChangeCheckBox().isSelected()));
                        } else {
                            userManagementService.create(new CreateUserRequest(
                                    fields.usernameField().getText(), fields.fullNameField().getText(), roleId,
                                    fields.statusComboBox().getValue(),
                                    fields.temporaryPasswordField().getText(),
                                    fields.forcePasswordChangeCheckBox().isSelected()));
                        }
                    },
                    () -> {
                        clearForm();
                        refreshUsers();
                        setMessage(editing ? "User updated successfully." : "User created successfully.");
                        dialog.close();
                    },
                    throwable -> {
                        submitButton.setDisable(false);
                        showProcessingError(throwable);
                    });
        });

        dialog.showAndWait();
    }

    private GridPane userDialogContent(UserDto user) {
        boolean editing = user != null;
        TextField usernameField = new TextField();
        usernameField.setPromptText("username");
        TextField fullNameField = new TextField();
        fullNameField.setPromptText("Full name");
        ComboBox<UserManagementRepository.RoleOption> roleComboBox = new ComboBox<>(roles);
        roleComboBox.setCellFactory(listView -> new RoleListCell());
        roleComboBox.setButtonCell(new RoleListCell());
        roleComboBox.setMaxWidth(Double.MAX_VALUE);
        ComboBox<User.Status> statusComboBox = new ComboBox<>(
                FXCollections.observableArrayList(User.Status.ACTIVE, User.Status.INACTIVE));
        statusComboBox.setMaxWidth(Double.MAX_VALUE);
        PasswordField temporaryPasswordField = new PasswordField();
        temporaryPasswordField.setPromptText("Temporary password");
        CheckBox forcePasswordChangeCheckBox = new CheckBox("Force Password Change");

        if (editing) {
            usernameField.setText(user.getUsername());
            fullNameField.setText(user.getFullName());
            roleComboBox.getSelectionModel().select(findRole(user.getRoleId()));
            statusComboBox.setValue(User.Status.valueOf(user.getStatus()));
            temporaryPasswordField.setVisible(false);
            temporaryPasswordField.setManaged(false);
            forcePasswordChangeCheckBox.setSelected(user.isForcePasswordChange());
        } else {
            statusComboBox.setValue(User.Status.ACTIVE);
            forcePasswordChangeCheckBox.setSelected(true);
        }

        GridPane grid = dialogGrid();
        grid.add(DialogStyler.fieldLabel("Username"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(DialogStyler.fieldLabel("Full Name"), 0, 1);
        grid.add(fullNameField, 1, 1);
        grid.add(DialogStyler.fieldLabel("Role"), 0, 2);
        grid.add(roleComboBox, 1, 2);
        grid.add(DialogStyler.fieldLabel("Status"), 0, 3);
        grid.add(statusComboBox, 1, 3);
        if (!editing) {
            grid.add(DialogStyler.fieldLabel("Temporary Password"), 0, 4);
            grid.add(temporaryPasswordField, 1, 4);
            grid.add(forcePasswordChangeCheckBox, 1, 5);
        } else {
            grid.add(forcePasswordChangeCheckBox, 1, 4);
        }

        grid.setUserData(new UserDialogFields(usernameField, fullNameField, roleComboBox, statusComboBox,
                temporaryPasswordField, forcePasswordChangeCheckBox));
        return grid;
    }

    private void showResetPasswordDialog(UserDto user) {
        Dialog<ButtonType> dialog = DialogStyler.apply(new Dialog<>());
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Reset password for " + user.getUsername());

        ButtonType resetButtonType = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(resetButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(resetPasswordDialogContent());
        dialog.getDialogPane().setPrefWidth(520);

        Button submitButton = (Button) dialog.getDialogPane().lookupButton(resetButtonType);
        submitButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            submitButton.setDisable(true);
            ResetPasswordDialogFields fields =
                    (ResetPasswordDialogFields) dialog.getDialogPane().getContent().getUserData();
            ResetPasswordRequest request = new ResetPasswordRequest(fields.temporaryPasswordField().getText(),
                    fields.forcePasswordChangeCheckBox().isSelected());
            ProcessingDialog.run("Reset Password", "Resetting password...",
                    () -> userManagementService.resetPassword(user.getId(), request),
                    () -> {
                        setMessage("Password reset successfully.");
                        clearForm();
                        refreshUsers();
                        dialog.close();
                    },
                    throwable -> {
                        submitButton.setDisable(false);
                        showProcessingError(throwable);
                    });
        });

        dialog.showAndWait();
    }

    private GridPane resetPasswordDialogContent() {
        PasswordField temporaryPasswordField = new PasswordField();
        temporaryPasswordField.setPromptText("Temporary password");
        CheckBox forcePasswordChangeCheckBox = new CheckBox("Force Password Change");
        forcePasswordChangeCheckBox.setSelected(true);

        GridPane grid = dialogGrid();
        grid.add(DialogStyler.fieldLabel("Temporary Password"), 0, 0);
        grid.add(temporaryPasswordField, 1, 0);
        grid.add(forcePasswordChangeCheckBox, 1, 1);
        grid.setUserData(new ResetPasswordDialogFields(temporaryPasswordField, forcePasswordChangeCheckBox));
        return grid;
    }

    private GridPane dialogGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(160);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);
        valueColumn.setMinWidth(320);
        grid.getColumnConstraints().addAll(labelColumn, valueColumn);
        return grid;
    }

    private record UserDialogFields(TextField usernameField, TextField fullNameField,
                                    ComboBox<UserManagementRepository.RoleOption> roleComboBox,
                                    ComboBox<User.Status> statusComboBox,
                                    PasswordField temporaryPasswordField,
                                    CheckBox forcePasswordChangeCheckBox) {
    }

    private record ResetPasswordDialogFields(PasswordField temporaryPasswordField,
                                             CheckBox forcePasswordChangeCheckBox) {
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
        private final Button resetPasswordButton = new Button();
        private final Button statusButton = new Button();
        private final HBox actionBox = new HBox(6, editButton, resetPasswordButton, statusButton);

        private ActionButtonCell() {
            getStyleClass().add("centered-table-cell");
            setAlignment(Pos.CENTER);
            actionBox.setAlignment(Pos.CENTER);
            editButton.getStyleClass().add("table-action-button");
            resetPasswordButton.getStyleClass().add("table-action-button");
            statusButton.getStyleClass().add("table-action-button");
            ButtonIconUtil.applyTableActionIcon(editButton, "fas-edit", "Edit user");
            ButtonIconUtil.applyTableActionIcon(resetPasswordButton, "fas-key", "Reset password");
            editButton.setOnAction(event -> showUserDialog(getTableView().getItems().get(getIndex())));
            resetPasswordButton.setOnAction(event -> showResetPasswordDialog(getTableView().getItems().get(getIndex())));
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
