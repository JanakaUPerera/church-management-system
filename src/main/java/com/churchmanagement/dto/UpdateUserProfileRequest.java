package com.churchmanagement.dto;

import java.nio.file.Path;

public class UpdateUserProfileRequest {
    private final String fullName;
    private final String email;
    private final String mobileNumber;
    private final Path selectedProfilePicture;

    public UpdateUserProfileRequest(String fullName, String email, String mobileNumber, Path selectedProfilePicture) {
        this.fullName = fullName;
        this.email = email;
        this.mobileNumber = mobileNumber;
        this.selectedProfilePicture = selectedProfilePicture;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public Path getSelectedProfilePicture() {
        return selectedProfilePicture;
    }
}
