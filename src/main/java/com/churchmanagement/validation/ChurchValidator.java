package com.churchmanagement.validation;

import com.churchmanagement.entity.Church;

import java.util.ArrayList;
import java.util.List;

public final class ChurchValidator {
    private ChurchValidator() {
    }

    public static List<String> validateForCreateOrUpdate(String churchCode, String churchName, Long regionId,
                                                         Church.Status status) {
        List<String> errors = new ArrayList<>();

        if (churchCode == null || churchCode.isBlank()) {
            errors.add("Church code is required.");
        }

        if (churchName == null || churchName.isBlank()) {
            errors.add("Church name is required.");
        }

        if (regionId == null) {
            errors.add("Region is required.");
        }

        if (status == null) {
            errors.add("Status must be ACTIVE or INACTIVE.");
        }

        return errors;
    }
}
