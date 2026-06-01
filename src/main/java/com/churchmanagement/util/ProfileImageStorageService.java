package com.churchmanagement.util;

import com.churchmanagement.validation.UserProfileValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ProfileImageStorageService {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final Path storageDirectory;
    private final Clock clock;

    public ProfileImageStorageService() {
        this(Path.of("user_uploads", "profile_pictures"), Clock.systemDefaultZone());
    }

    public ProfileImageStorageService(Path storageDirectory, Clock clock) {
        this.storageDirectory = storageDirectory;
        this.clock = clock;
    }

    public StoredProfileImage store(long userId, Path selectedImage) {
        if (selectedImage == null) {
            return null;
        }

        try {
            long fileSize = Files.size(selectedImage);
            String mimeType = Files.probeContentType(selectedImage);
            List<String> errors = UserProfileValidator.validateImage(selectedImage, fileSize, mimeType);
            if (!errors.isEmpty()) {
                throw new ProfileImageStorageException(String.join("\n", errors));
            }

            Files.createDirectories(storageDirectory);
            Path destination = storageDirectory.resolve("user_" + userId + "_"
                    + FILE_TIMESTAMP.format(LocalDateTime.now(clock)) + ".png");
            Files.copy(selectedImage, destination, StandardCopyOption.REPLACE_EXISTING);
            return new StoredProfileImage(destination.toString(), fileSize);
        } catch (ProfileImageStorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ProfileImageStorageException("Unable to save profile picture. Please try again.", exception);
        }
    }

    public record StoredProfileImage(String path, long fileSizeBytes) {
    }

    public static class ProfileImageStorageException extends RuntimeException {
        public ProfileImageStorageException(String message) {
            super(message);
        }

        public ProfileImageStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
