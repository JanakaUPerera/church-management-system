package com.churchmanagement.validation;

import com.churchmanagement.entity.CollectionCategory;

import java.util.ArrayList;
import java.util.List;

public final class CollectionCategoryValidator {
    private CollectionCategoryValidator() {
    }

    public static List<String> validateForCreateOrUpdate(String code, String name, String description,
                                                         CollectionCategory.Status status) {
        List<String> errors = new ArrayList<>();

        if (code == null || code.isBlank()) {
            errors.add("Category code is required.");
        } else if (!code.matches("[A-Z0-9]{1,10}")) {
            errors.add("Category code may only contain letters and digits (max 10).");
        }

        if (name == null || name.isBlank()) {
            errors.add("Category name is required.");
        } else if (name.length() > 100) {
            errors.add("Category name must be 100 characters or fewer.");
        }

        if (description != null && description.length() > 255) {
            errors.add("Description must be 255 characters or fewer.");
        }

        if (status == null) {
            errors.add("Status must be ACTIVE or INACTIVE.");
        }

        return errors;
    }
}
