package com.churchmanagement.service;

import com.churchmanagement.repository.UserRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordChangeServiceTest {
    private FakeUserRepository userRepository;
    private FakeActivityLogService activityLogService;
    private PasswordChangeService passwordChangeService;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        activityLogService = new FakeActivityLogService();
        passwordChangeService = new PasswordChangeService(userRepository, activityLogService,
                Clock.fixed(Instant.parse("2026-06-01T08:00:00Z"), ZoneId.of("UTC")));
        AuthContext.setCurrentUser(new AuthenticatedUser(5L, "forced", "Forced User", 2L,
                "User", List.of("REPORT_VIEW"), true));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void wrongCurrentPasswordRejected() {
        PasswordChangeService.PasswordChangeException exception = assertThrows(
                PasswordChangeService.PasswordChangeException.class,
                () -> passwordChangeService.changeForcedPassword("wrongpass", "newSecret1", "newSecret1"));

        assertEquals("Current password is incorrect.", exception.getMessage());
        assertEquals(ActivityLogService.PASSWORD_CHANGE_FAILED, activityLogService.lastAction);
    }

    @Test
    void newPasswordSameAsOldRejected() {
        PasswordChangeService.PasswordChangeException exception = assertThrows(
                PasswordChangeService.PasswordChangeException.class,
                () -> passwordChangeService.changeForcedPassword("oldSecret", "oldSecret", "oldSecret"));

        assertTrue(exception.getMessage().contains("New password cannot be the same as current password."));
        assertEquals(ActivityLogService.PASSWORD_CHANGE_FAILED, activityLogService.lastAction);
    }

    @Test
    void passwordMismatchRejected() {
        PasswordChangeService.PasswordChangeException exception = assertThrows(
                PasswordChangeService.PasswordChangeException.class,
                () -> passwordChangeService.changeForcedPassword("oldSecret", "newSecret1", "newSecret2"));

        assertTrue(exception.getMessage().contains("New password and confirm password must match."));
        assertEquals(ActivityLogService.PASSWORD_CHANGE_FAILED, activityLogService.lastAction);
    }

    @Test
    void validPasswordChangeClearsForcePasswordChange() {
        passwordChangeService.changeForcedPassword("oldSecret", "newSecret1", "newSecret1");

        assertTrue(BCrypt.checkpw("newSecret1", userRepository.passwordHash));
        assertFalse(userRepository.forcePasswordChange);
        assertEquals(LocalDateTime.of(2026, 6, 1, 8, 0), userRepository.updatedAt);
        assertEquals(ActivityLogService.PASSWORD_CHANGED_FORCE, activityLogService.lastAction);
    }

    @Test
    void authContextUpdatedAfterPasswordChange() {
        passwordChangeService.changeForcedPassword("oldSecret", "newSecret1", "newSecret1");

        assertFalse(AuthContext.getCurrentUser().orElseThrow().isForcePasswordChange());
    }

    private static class FakeUserRepository extends UserRepository {
        private String passwordHash = BCrypt.hashpw("oldSecret", BCrypt.gensalt());
        private boolean forcePasswordChange = true;
        private LocalDateTime updatedAt;

        private FakeUserRepository() {
            super((DataSource) null);
        }

        @Override
        public Optional<String> findPasswordHashByUserId(long userId) {
            return userId == 5L ? Optional.of(passwordHash) : Optional.empty();
        }

        @Override
        public void updatePasswordAndClearForceChange(long userId, String newPasswordHash,
                                                      LocalDateTime updatedAt) {
            this.passwordHash = newPasswordHash;
            this.forcePasswordChange = false;
            this.updatedAt = updatedAt;
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String lastAction;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logForcedPasswordChanged(long userId, String username) {
            lastAction = PASSWORD_CHANGED_FORCE;
        }

        @Override
        public void logPasswordChangeFailed(long userId, String username, String reason) {
            lastAction = PASSWORD_CHANGE_FAILED;
        }
    }
}
