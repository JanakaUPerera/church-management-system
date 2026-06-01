package com.churchmanagement.controller;

import com.churchmanagement.dto.ChangeOwnPasswordRequest;
import com.churchmanagement.dto.UpdateUserProfileRequest;
import com.churchmanagement.dto.UserProfileDto;
import com.churchmanagement.service.UserProfileService;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.geometry.Rectangle2D;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class UserProfileController {
    private final UserProfileService userProfileService = new UserProfileService();

    private Path selectedProfilePicture;
    private UserProfileDto profile;

    @FXML private Label messageLabel;
    @FXML private Label avatarInitialsLabel;
    @FXML private ImageView profilePicturePreview;
    @FXML private Button uploadPictureButton;
    @FXML private TextField usernameField;
    @FXML private TextField roleField;
    @FXML private TextField statusField;
    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private TextField mobileNumberField;
    @FXML private Button saveProfileButton;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmNewPasswordField;
    @FXML private Button changePasswordButton;

    @FXML
    private void initialize() {
        configureControls();
        loadProfile();
    }

    @FXML
    private void handleUploadPicture() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Picture");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"));
        Window window = uploadPictureButton.getScene() == null ? null : uploadPictureButton.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(window);
        if (selectedFile == null) {
            return;
        }

        selectedProfilePicture = selectedFile.toPath();
        showPreview(selectedProfilePicture);
    }

    @FXML
    private void handleSaveProfile() {
        try {
            profile = userProfileService.updateOwnProfile(new UpdateUserProfileRequest(
                    fullNameField.getText(), emailField.getText(), mobileNumberField.getText(),
                    selectedProfilePicture));
            selectedProfilePicture = null;
            populateProfile(profile);
            DashboardController.refreshActiveHeader();
            showInfo("Profile updated successfully.");
        } catch (UserProfileService.UserProfileException exception) {
            showError(exception.getMessage());
        }
    }

    @FXML
    private void handleChangePassword() {
        try {
            userProfileService.changeOwnPassword(new ChangeOwnPasswordRequest(
                    currentPasswordField.getText(), newPasswordField.getText(), confirmNewPasswordField.getText()));
            currentPasswordField.clear();
            newPasswordField.clear();
            confirmNewPasswordField.clear();
            showInfo("Password changed successfully.");
        } catch (UserProfileService.UserProfileException exception) {
            showError(exception.getMessage());
        }
    }

    private void configureControls() {
        usernameField.setEditable(false);
        roleField.setEditable(false);
        statusField.setEditable(false);
        profilePicturePreview.setFitWidth(118);
        profilePicturePreview.setFitHeight(118);
        profilePicturePreview.setPreserveRatio(false);
        profilePicturePreview.setClip(new Circle(59, 59, 59));
        ButtonIconUtil.applyIcon(uploadPictureButton, "fas-upload");
        ButtonIconUtil.applyIcon(saveProfileButton, "fas-save");
        ButtonIconUtil.applyIcon(changePasswordButton, "fas-key");
    }

    private void loadProfile() {
        try {
            profile = userProfileService.loadOwnProfile();
            populateProfile(profile);
        } catch (UserProfileService.UserProfileException exception) {
            showError(exception.getMessage());
            setFormDisabled(true);
        }
    }

    private void populateProfile(UserProfileDto profile) {
        usernameField.setText(profile.getUsername());
        roleField.setText(profile.getRoleName());
        statusField.setText(profile.getStatus());
        fullNameField.setText(profile.getFullName());
        emailField.setText(profile.getEmail());
        mobileNumberField.setText(profile.getMobileNumber());
        showPreview(profile.getProfilePicturePath() == null ? null : Path.of(profile.getProfilePicturePath()));
    }

    private void showPreview(Path imagePath) {
        if (imagePath != null && Files.exists(imagePath)) {
            setCircularImage(profilePicturePreview, imagePath, 118);
            avatarInitialsLabel.setVisible(false);
        } else {
            profilePicturePreview.setImage(null);
            profilePicturePreview.setViewport(null);
            avatarInitialsLabel.setText(initials(fullNameField.getText()));
            avatarInitialsLabel.setVisible(true);
        }
    }

    private void setCircularImage(ImageView imageView, Path imagePath, double size) {
        Image image = new Image(imagePath.toUri().toString());
        double width = image.getWidth();
        double height = image.getHeight();
        double squareSize = Math.min(width, height);
        imageView.setViewport(new Rectangle2D(
                (width - squareSize) / 2,
                (height - squareSize) / 2,
                squareSize,
                squareSize));
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(false);
        imageView.setImage(image);
    }

    private void setFormDisabled(boolean disabled) {
        uploadPictureButton.setDisable(disabled);
        fullNameField.setDisable(disabled);
        emailField.setDisable(disabled);
        mobileNumberField.setDisable(disabled);
        saveProfileButton.setDisable(disabled);
        currentPasswordField.setDisable(disabled);
        newPasswordField.setDisable(disabled);
        confirmNewPasswordField.setDisable(disabled);
        changePasswordButton.setDisable(disabled);
    }

    private void showInfo(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.INFORMATION));
        alert.setTitle("My Profile");
        alert.setHeaderText("Success");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("My Profile");
        alert.setHeaderText("Unable to complete action");
        alert.setContentText(message == null || message.isBlank() ? "Action failed. Please try again." : message);
        alert.showAndWait();
    }

    private String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.strip().split("\\s+");
        String first = parts[0].substring(0, 1);
        String second = parts.length > 1 ? parts[1].substring(0, 1) : "";
        return (first + second).toUpperCase();
    }
}
