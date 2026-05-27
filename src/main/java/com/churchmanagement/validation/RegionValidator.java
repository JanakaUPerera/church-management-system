package com.churchmanagement.validation;

import com.churchmanagement.entity.Region;

import java.util.ArrayList;
import java.util.List;

public final class RegionValidator {
    private RegionValidator() {
    }

    public static List<String> validateForCreateOrUpdate(String regionCode, String regionName, Region.Status status) {
        List<String> errors = new ArrayList<>();

        if (regionCode == null || regionCode.isBlank()) {
            errors.add("Region code is required.");
        }

        if (regionName == null || regionName.isBlank()) {
            errors.add("Region name is required.");
        }

        if (status == null) {
            errors.add("Status must be ACTIVE or INACTIVE.");
        }

        return errors;
    }
}
