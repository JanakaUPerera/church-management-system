package com.churchmanagement.validation;

import com.churchmanagement.entity.User;

import java.util.ArrayList;
import java.util.List;

public final class UserValidator {
    private UserValidator() {
    }

    public static List<String> validateForCreate(String username, String fullName, Long roleId,
                                                 User.Status status, String temporaryPassword) {
        List<String> errors = validateForUpdate(username, fullName, roleId, status);
        if (temporaryPassword == null || temporaryPassword.isBlank()) {
            errors.add("Temporary password is required when creating a user.");
        }
        return errors;
    }

    public static List<String> validateForUpdate(String username, String fullName, Long roleId, User.Status status) {
        List<String> errors = new ArrayList<>();
        if (username == null || username.isBlank()) {
            errors.add("Username is required.");
        }
        if (fullName == null || fullName.isBlank()) {
            errors.add("Full name is required.");
        }
        if (roleId == null) {
            errors.add("Role is required.");
        }
        if (status == null) {
            errors.add("Status must be ACTIVE or INACTIVE.");
        }
        return errors;
    }

    public static List<String> validateForPasswordReset(String temporaryPassword) {
        List<String> errors = new ArrayList<>();
        if (temporaryPassword == null || temporaryPassword.isBlank()) {
            errors.add("Temporary password is required.");
        }
        return errors;
    }
}
