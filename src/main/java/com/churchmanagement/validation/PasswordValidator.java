package com.churchmanagement.validation;

import java.util.ArrayList;
import java.util.List;

public final class PasswordValidator {
    private static final int MINIMUM_PASSWORD_LENGTH = 8;

    private PasswordValidator() {
    }

    public static List<String> validateForcedChange(String currentPassword, String newPassword,
                                                    String confirmPassword) {
        List<String> errors = new ArrayList<>();

        if (isBlank(currentPassword)) {
            errors.add("Current password is required.");
        }
        if (isBlank(newPassword)) {
            errors.add("New password is required.");
        }
        if (isBlank(confirmPassword)) {
            errors.add("Confirm password is required.");
        }
        if (!isBlank(newPassword) && newPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            errors.add("New password must be at least 8 characters.");
        }
        if (!isBlank(newPassword) && !isBlank(confirmPassword) && !newPassword.equals(confirmPassword)) {
            errors.add("New password and confirm password must match.");
        }
        if (!isBlank(currentPassword) && !isBlank(newPassword) && currentPassword.equals(newPassword)) {
            errors.add("New password cannot be the same as current password.");
        }

        return errors;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
