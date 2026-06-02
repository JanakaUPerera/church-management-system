package com.churchmanagement.validation;

import java.util.ArrayList;
import java.util.List;

public final class RoleValidator {
    private RoleValidator() {
    }

    public static List<String> validateName(String roleName) {
        List<String> errors = new ArrayList<>();
        if (roleName == null || roleName.isBlank()) {
            errors.add("Role name is required.");
        } else if (roleName.strip().length() > 50) {
            errors.add("Role name must be 50 characters or fewer.");
        }
        return errors;
    }
}
