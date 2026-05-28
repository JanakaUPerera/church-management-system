package com.churchmanagement.validation;

import com.churchmanagement.entity.Church;
import com.churchmanagement.enums.AuthorizedPersonPosition;

import java.util.ArrayList;
import java.util.List;

public final class ChurchValidator {
    private ChurchValidator() {
    }

    public static List<String> validateForCreateOrUpdate(String churchCode, String churchName, Long regionId,
                                                         Church.Status status, AuthorizedPersonPosition position,
                                                         String positionOther, String smsMobileNumber) {
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

        if (position == AuthorizedPersonPosition.OTHER && (positionOther == null || positionOther.isBlank())) {
            errors.add("Other position is required when position is Other.");
        }

        if (smsMobileNumber != null && !smsMobileNumber.isBlank() && !isValidSriLankanMobile(smsMobileNumber)) {
            errors.add("SMS mobile number must be 07XXXXXXXX, 947XXXXXXXX, or +947XXXXXXXX.");
        }

        return errors;
    }

    public static boolean isValidSriLankanMobile(String smsMobileNumber) {
        String value = smsMobileNumber.strip();
        return value.matches("07\\d{8}") || value.matches("947\\d{8}") || value.matches("\\+947\\d{8}");
    }
}
