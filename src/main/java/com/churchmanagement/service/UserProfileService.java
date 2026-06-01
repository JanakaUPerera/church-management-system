package com.churchmanagement.service;

import com.churchmanagement.dto.ChangeOwnPasswordRequest;
import com.churchmanagement.dto.UpdateUserProfileRequest;
import com.churchmanagement.dto.UserProfileDto;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.UserProfileRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.util.ProfileImageStorageService;
import com.churchmanagement.validation.UserProfileValidator;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class UserProfileService {
    private final UserProfileRepository userProfileRepository;
    private final ProfileImageStorageService profileImageStorageService;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public UserProfileService() {
        this(new UserProfileRepository(), new ProfileImageStorageService(), new ActivityLogService(),
                Clock.systemDefaultZone());
    }

    public UserProfileService(UserProfileRepository userProfileRepository,
                              ProfileImageStorageService profileImageStorageService,
                              ActivityLogService activityLogService,
                              Clock clock) {
        this.userProfileRepository = userProfileRepository;
        this.profileImageStorageService = profileImageStorageService;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public UserProfileDto loadOwnProfile() {
        AuthenticatedUser currentUser = currentUser();
        try {
            return userProfileRepository.findById(currentUser.getUserId())
                    .orElseThrow(() -> new UserProfileException("User profile could not be found."));
        } catch (DatabaseException exception) {
            throw new UserProfileException("Unable to load profile right now. Please try again later.", exception);
        }
    }

    public UserProfileDto updateOwnProfile(UpdateUserProfileRequest request) {
        AuthenticatedUser currentUser = currentUser();
        UpdateUserProfileRequest safeRequest = request == null
                ? new UpdateUserProfileRequest("", "", "", null)
                : request;
        String fullName = nullToBlank(safeRequest.getFullName()).strip();
        String email = nullToBlank(safeRequest.getEmail()).strip();
        String mobile = nullToBlank(safeRequest.getMobileNumber()).strip();

        List<String> errors = UserProfileValidator.validateProfile(fullName, email, mobile);
        if (!errors.isEmpty()) {
            throw new UserProfileException(String.join("\n", errors));
        }

        try {
            UserProfileDto existing = userProfileRepository.findById(currentUser.getUserId())
                    .orElseThrow(() -> new UserProfileException("User profile could not be found."));
            String normalizedMobile = mobile.isBlank()
                    ? null
                    : UserProfileValidator.normalizeMobileNumber(mobile)
                    .orElseThrow(() -> new UserProfileException("Invalid mobile number."));
            String picturePath = existing.getProfilePicturePath();
            boolean pictureUpdated = safeRequest.getSelectedProfilePicture() != null;
            if (pictureUpdated) {
                ProfileImageStorageService.StoredProfileImage storedImage =
                        profileImageStorageService.store(currentUser.getUserId(),
                                safeRequest.getSelectedProfilePicture());
                picturePath = storedImage.path();
            }

            userProfileRepository.updateProfile(currentUser.getUserId(), fullName,
                    email.isBlank() ? null : email, normalizedMobile, picturePath, LocalDateTime.now(clock));
            UserProfileDto updated = userProfileRepository.findById(currentUser.getUserId())
                    .orElseThrow(() -> new UserProfileException("User profile could not be found."));
            AuthContext.updateCurrentUserProfile(updated.getFullName(), updated.getProfilePicturePath());
            activityLogService.logProfileUpdated(currentUser.getUserId(), currentUser.getUsername());
            if (pictureUpdated) {
                activityLogService.logProfilePictureUpdated(currentUser.getUserId(), currentUser.getUsername(),
                        picturePath);
            }
            return updated;
        } catch (ProfileImageStorageService.ProfileImageStorageException exception) {
            throw new UserProfileException(exception.getMessage(), exception);
        } catch (DatabaseException exception) {
            throw new UserProfileException("Unable to update profile right now. Please try again later.", exception);
        }
    }

    public void changeOwnPassword(ChangeOwnPasswordRequest request) {
        AuthenticatedUser currentUser = currentUser();
        ChangeOwnPasswordRequest safeRequest = request == null
                ? new ChangeOwnPasswordRequest("", "", "")
                : request;
        try {
            validatePasswordChange(safeRequest, currentUser);
            String passwordHash = BCrypt.hashpw(safeRequest.getNewPassword(), BCrypt.gensalt());
            userProfileRepository.updatePassword(currentUser.getUserId(), passwordHash, LocalDateTime.now(clock));
            activityLogService.logOwnPasswordChanged(currentUser.getUserId(), currentUser.getUsername());
        } catch (UserProfileException exception) {
            activityLogService.logOwnPasswordChangeFailed(currentUser.getUserId(), currentUser.getUsername(),
                    exception.getMessage());
            throw exception;
        } catch (DatabaseException exception) {
            activityLogService.logOwnPasswordChangeFailed(currentUser.getUserId(), currentUser.getUsername(),
                    "Database error");
            throw new UserProfileException("Unable to change password right now. Please try again later.", exception);
        }
    }

    private void validatePasswordChange(ChangeOwnPasswordRequest request, AuthenticatedUser currentUser) {
        List<String> errors = UserProfileValidator.validatePasswordChange(request.getCurrentPassword(),
                request.getNewPassword(), request.getConfirmNewPassword());
        if (!errors.isEmpty()) {
            throw new UserProfileException(String.join("\n", errors));
        }

        String currentPasswordHash = userProfileRepository.findPasswordHashByUserId(currentUser.getUserId())
                .orElseThrow(() -> new UserProfileException("User account could not be found."));
        if (!BCrypt.checkpw(request.getCurrentPassword(), currentPasswordHash)) {
            throw new UserProfileException("Current password is incorrect.");
        }
    }

    private AuthenticatedUser currentUser() {
        return AuthContext.getCurrentUser()
                .orElseThrow(() -> new UserProfileException("Please sign in to manage your profile."));
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    public static class UserProfileException extends RuntimeException {
        public UserProfileException(String message) {
            super(message);
        }

        public UserProfileException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
