package com.churchmanagement.service;

import com.churchmanagement.dto.ChangeOwnPasswordRequest;
import com.churchmanagement.dto.UpdateUserProfileRequest;
import com.churchmanagement.dto.UserProfileDto;
import com.churchmanagement.repository.UserProfileRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.util.ProfileImageStorageService;
import com.churchmanagement.validation.UserProfileValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserProfileServiceTest {
    private FakeUserProfileRepository userProfileRepository;
    private FakeProfileImageStorageService profileImageStorageService;
    private FakeActivityLogService activityLogService;
    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileRepository = new FakeUserProfileRepository();
        profileImageStorageService = new FakeProfileImageStorageService();
        activityLogService = new FakeActivityLogService();
        userProfileService = new UserProfileService(userProfileRepository, profileImageStorageService,
                activityLogService, Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("UTC")));
        AuthContext.setCurrentUser(new AuthenticatedUser(1L, "admin", "Admin", 1L, "Admin", List.of()));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void loadOwnProfile() {
        UserProfileDto profile = userProfileService.loadOwnProfile();

        assertEquals(1L, profile.getId());
        assertEquals("admin", profile.getUsername());
    }

    @Test
    void updateOwnFullName() {
        UserProfileDto profile = userProfileService.updateOwnProfile(
                new UpdateUserProfileRequest("System Admin", "", "", null));

        assertEquals("System Admin", profile.getFullName());
        assertEquals("System Admin", AuthContext.getCurrentUser().orElseThrow().getFullName());
        assertTrue(activityLogService.loggedActions.contains(ActivityLogService.PROFILE_UPDATED));
    }

    @Test
    void updateProfilePicture() {
        UserProfileDto profile = userProfileService.updateOwnProfile(
                new UpdateUserProfileRequest("Admin", "", "", Path.of("profile.png")));

        assertEquals("user_uploads/profile_pictures/user_1_20260601100000.png",
                profile.getProfilePicturePath());
        assertTrue(activityLogService.loggedActions.contains(ActivityLogService.PROFILE_PICTURE_UPDATED));
    }

    @Test
    void rejectEmptyFullName() {
        UserProfileService.UserProfileException exception = assertThrows(
                UserProfileService.UserProfileException.class,
                () -> userProfileService.updateOwnProfile(new UpdateUserProfileRequest(" ", "", "", null)));

        assertEquals("Full name is required.", exception.getMessage());
    }

    @Test
    void validateEmail() {
        UserProfileService.UserProfileException exception = assertThrows(
                UserProfileService.UserProfileException.class,
                () -> userProfileService.updateOwnProfile(
                        new UpdateUserProfileRequest("Admin", "not-an-email", "", null)));

        assertEquals("Invalid email address.", exception.getMessage());
    }

    @Test
    void normalizeMobileNumber() {
        UserProfileDto profile = userProfileService.updateOwnProfile(
                new UpdateUserProfileRequest("Admin", "", "0712345678", null));

        assertEquals("+94712345678", profile.getMobileNumber());
    }

    @Test
    void rejectInvalidMobileNumber() {
        UserProfileService.UserProfileException exception = assertThrows(
                UserProfileService.UserProfileException.class,
                () -> userProfileService.updateOwnProfile(
                        new UpdateUserProfileRequest("Admin", "", "12345", null)));

        assertEquals("Invalid mobile number.", exception.getMessage());
    }

    @Test
    void rejectInvalidImageExtension() {
        List<String> errors = UserProfileValidator.validateImage(Path.of("profile.exe"), 100, "application/x-msdownload");

        assertEquals(List.of("Profile picture must be JPG or PNG."), errors);
    }

    @Test
    void rejectImageOverTwoMb() {
        List<String> errors = UserProfileValidator.validateImage(Path.of("profile.png"),
                UserProfileValidator.MAX_PROFILE_PICTURE_BYTES + 1, "image/png");

        assertEquals(List.of("Profile picture must be less than 2 MB."), errors);
    }

    @Test
    void changeOwnPasswordSuccessfully() {
        userProfileService.changeOwnPassword(new ChangeOwnPasswordRequest("admin123", "newSecret1", "newSecret1"));

        assertTrue(BCrypt.checkpw("newSecret1", userProfileRepository.passwordHash(1L)));
        assertTrue(activityLogService.loggedActions.contains(ActivityLogService.OWN_PASSWORD_CHANGED));
    }

    @Test
    void rejectWrongCurrentPassword() {
        UserProfileService.UserProfileException exception = assertThrows(
                UserProfileService.UserProfileException.class,
                () -> userProfileService.changeOwnPassword(
                        new ChangeOwnPasswordRequest("wrongpass", "newSecret1", "newSecret1")));

        assertEquals("Current password is incorrect.", exception.getMessage());
        assertTrue(activityLogService.loggedActions.contains(ActivityLogService.OWN_PASSWORD_CHANGE_FAILED));
    }

    @Test
    void rejectPasswordMismatch() {
        UserProfileService.UserProfileException exception = assertThrows(
                UserProfileService.UserProfileException.class,
                () -> userProfileService.changeOwnPassword(
                        new ChangeOwnPasswordRequest("admin123", "newSecret1", "newSecret2")));

        assertEquals("New password and confirm password do not match.", exception.getMessage());
    }

    @Test
    void rejectSameNewPassword() {
        UserProfileService.UserProfileException exception = assertThrows(
                UserProfileService.UserProfileException.class,
                () -> userProfileService.changeOwnPassword(
                        new ChangeOwnPasswordRequest("admin123", "admin123", "admin123")));

        assertEquals("New password cannot be same as current password.", exception.getMessage());
    }

    @Test
    void ensureUserCannotUpdateAnotherUsersProfile() {
        userProfileService.updateOwnProfile(new UpdateUserProfileRequest("Admin Updated", "", "", null));

        assertEquals("Admin Updated", userProfileRepository.findById(1L).orElseThrow().getFullName());
        assertEquals("Other User", userProfileRepository.findById(2L).orElseThrow().getFullName());
    }

    private static class FakeUserProfileRepository extends UserProfileRepository {
        private final List<UserProfileDto> profiles = new ArrayList<>();
        private final java.util.Map<Long, String> passwordHashes = new java.util.HashMap<>();

        private FakeUserProfileRepository() {
            super((DataSource) null);
            profiles.add(profile(1L, "admin", "Admin", "Admin", "ACTIVE"));
            profiles.add(profile(2L, "other", "Other User", "User", "ACTIVE"));
            passwordHashes.put(1L, BCrypt.hashpw("admin123", BCrypt.gensalt()));
            passwordHashes.put(2L, BCrypt.hashpw("other123", BCrypt.gensalt()));
        }

        @Override
        public Optional<UserProfileDto> findById(long userId) {
            return profiles.stream().filter(profile -> profile.getId() == userId).findFirst();
        }

        @Override
        public void updateProfile(long userId, String fullName, String email, String mobileNumber,
                                  String profilePicturePath, LocalDateTime updatedAt) {
            UserProfileDto profile = findById(userId).orElseThrow();
            profile.setFullName(fullName);
            profile.setEmail(email);
            profile.setMobileNumber(mobileNumber);
            profile.setProfilePicturePath(profilePicturePath);
        }

        @Override
        public Optional<String> findPasswordHashByUserId(long userId) {
            return Optional.ofNullable(passwordHashes.get(userId));
        }

        @Override
        public void updatePassword(long userId, String passwordHash, LocalDateTime updatedAt) {
            passwordHashes.put(userId, passwordHash);
        }

        private String passwordHash(long userId) {
            return passwordHashes.get(userId);
        }

        private static UserProfileDto profile(long id, String username, String fullName, String roleName,
                                              String status) {
            UserProfileDto profile = new UserProfileDto();
            profile.setId(id);
            profile.setUsername(username);
            profile.setFullName(fullName);
            profile.setRoleId(id);
            profile.setRoleName(roleName);
            profile.setStatus(status);
            return profile;
        }
    }

    private static class FakeProfileImageStorageService extends ProfileImageStorageService {
        private FakeProfileImageStorageService() {
            super(Path.of("unused"), Clock.systemUTC());
        }

        @Override
        public StoredProfileImage store(long userId, Path selectedImage) {
            return new StoredProfileImage("user_uploads/profile_pictures/user_" + userId + "_20260601100000.png",
                    100);
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private final List<String> loggedActions = new ArrayList<>();

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logProfileUpdated(long userId, String username) {
            loggedActions.add(PROFILE_UPDATED);
        }

        @Override
        public void logProfilePictureUpdated(long userId, String username, String profilePicturePath) {
            loggedActions.add(PROFILE_PICTURE_UPDATED);
        }

        @Override
        public void logOwnPasswordChanged(long userId, String username) {
            loggedActions.add(OWN_PASSWORD_CHANGED);
        }

        @Override
        public void logOwnPasswordChangeFailed(long userId, String username, String reason) {
            loggedActions.add(OWN_PASSWORD_CHANGE_FAILED);
        }
    }
}
