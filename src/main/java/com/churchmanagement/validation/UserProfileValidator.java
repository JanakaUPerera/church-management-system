package com.churchmanagement.validation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class UserProfileValidator {
    public static final long MAX_PROFILE_PICTURE_BYTES = 2L * 1024L * 1024L;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOCAL_MOBILE_PATTERN = Pattern.compile("^07\\d{8}$");
    private static final Pattern PLUS_MOBILE_PATTERN = Pattern.compile("^\\+947\\d{8}$");
    private static final Pattern INTERNATIONAL_MOBILE_PATTERN = Pattern.compile("^947\\d{8}$");

    private UserProfileValidator() {
    }

    public static List<String> validateProfile(String fullName, String email, String mobileNumber) {
        List<String> errors = new ArrayList<>();
        if (fullName == null || fullName.isBlank()) {
            errors.add("Full name is required.");
        }
        if (email != null && !email.isBlank() && !EMAIL_PATTERN.matcher(email.strip()).matches()) {
            errors.add("Invalid email address.");
        }
        if (mobileNumber != null && !mobileNumber.isBlank() && normalizeMobileNumber(mobileNumber).isEmpty()) {
            errors.add("Invalid mobile number.");
        }
        return errors;
    }

    public static List<String> validatePasswordChange(String currentPassword, String newPassword,
                                                       String confirmNewPassword) {
        List<String> errors = new ArrayList<>();
        if (currentPassword == null || currentPassword.isBlank()) {
            errors.add("Current password is required.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            errors.add("New password must be at least 8 characters.");
        }
        if (newPassword != null && currentPassword != null && newPassword.equals(currentPassword)) {
            errors.add("New password cannot be same as current password.");
        }
        if (newPassword == null || confirmNewPassword == null || !newPassword.equals(confirmNewPassword)) {
            errors.add("New password and confirm password do not match.");
        }
        return errors;
    }

    public static List<String> validateImage(Path imagePath, long fileSizeBytes, String mimeType) {
        List<String> errors = new ArrayList<>();
        if (imagePath == null) {
            return errors;
        }
        if (!hasAllowedImageExtension(imagePath) || !hasAllowedMimeType(mimeType)) {
            errors.add("Profile picture must be JPG or PNG.");
        }
        if (fileSizeBytes > MAX_PROFILE_PICTURE_BYTES) {
            errors.add("Profile picture must be less than 2 MB.");
        }
        return errors;
    }

    public static java.util.Optional<String> normalizeMobileNumber(String mobileNumber) {
        if (mobileNumber == null || mobileNumber.isBlank()) {
            return java.util.Optional.empty();
        }

        String value = mobileNumber.strip().replace(" ", "");
        if (LOCAL_MOBILE_PATTERN.matcher(value).matches()) {
            return java.util.Optional.of("+94" + value.substring(1));
        }
        if (PLUS_MOBILE_PATTERN.matcher(value).matches()) {
            return java.util.Optional.of(value);
        }
        if (INTERNATIONAL_MOBILE_PATTERN.matcher(value).matches()) {
            return java.util.Optional.of("+" + value);
        }
        return java.util.Optional.empty();
    }

    public static boolean hasAllowedImageExtension(Path imagePath) {
        if (imagePath == null || imagePath.getFileName() == null) {
            return false;
        }
        String fileName = imagePath.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png");
    }

    public static boolean hasAllowedMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return true;
        }
        String normalized = mimeType.toLowerCase(Locale.ROOT);
        return "image/jpeg".equals(normalized) || "image/png".equals(normalized);
    }
}
