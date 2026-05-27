package com.churchmanagement.service;

import com.churchmanagement.repository.PermissionRepository;
import com.churchmanagement.repository.UserRepository;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    private final FakeUserRepository userRepository = new FakeUserRepository();
    private final FakePermissionRepository permissionRepository = new FakePermissionRepository();
    private final FakeActivityLogService activityLogService = new FakeActivityLogService();
    private final AuthService authService = new AuthService(
            userRepository,
            permissionRepository,
            activityLogService,
            Clock.fixed(Instant.parse("2026-05-27T12:00:00Z"), ZoneId.of("UTC"))
    );

    @Test
    void successfulLoginReturnsAuthenticatedUser() {
        AuthenticatedUser user = authService.login("admin", "admin123");

        assertEquals(1L, user.getUserId());
        assertEquals("admin", user.getUsername());
        assertEquals("System Administrator", user.getFullName());
        assertEquals(1L, user.getRoleId());
        assertEquals("Admin", user.getRoleName());
        assertEquals(1L, userRepository.lastLoginUserId);
        assertEquals(1, activityLogService.successCount);
    }

    @Test
    void wrongPasswordFailsLogin() {
        AuthService.AuthException exception = assertThrows(
                AuthService.AuthException.class,
                () -> authService.login("admin", "wrong")
        );

        assertEquals(AuthService.AuthFailureReason.INVALID_CREDENTIALS, exception.getReason());
        assertEquals(1, activityLogService.failedCount);
        assertFalse(userRepository.lastLoginUpdated);
    }

    @Test
    void inactiveUserCannotLogin() {
        userRepository.active = false;

        AuthService.AuthException exception = assertThrows(
                AuthService.AuthException.class,
                () -> authService.login("admin", "admin123")
        );

        assertEquals(AuthService.AuthFailureReason.INACTIVE_USER, exception.getReason());
        assertEquals(1, activityLogService.failedCount);
        assertFalse(userRepository.lastLoginUpdated);
    }

    @Test
    void loadsPermissionsForAuthenticatedUser() {
        AuthenticatedUser user = authService.login("admin", "admin123");

        assertTrue(user.hasPermission("USER_MANAGE"));
        assertTrue(user.hasAnyPermission("REPORT_VIEW", "MISSING_PERMISSION"));
        assertFalse(user.hasPermission("MISSING_PERMISSION"));
        assertTrue(user.hasRole("admin"));
    }

    private static class FakeUserRepository extends UserRepository {
        private boolean active = true;
        private boolean lastLoginUpdated;
        private long lastLoginUserId;

        private FakeUserRepository() {
            super((DataSource) null);
        }

        @Override
        public Optional<UserCredentials> findByUsername(String username) {
            if (!"admin".equals(username)) {
                return Optional.empty();
            }

            return Optional.of(new UserCredentials(
                    1L,
                    "admin",
                    BCrypt.hashpw("admin123", BCrypt.gensalt()),
                    "System Administrator",
                    1L,
                    "Admin",
                    active
            ));
        }

        @Override
        public void updateLastLoginAt(long userId, java.time.LocalDateTime lastLoginAt) {
            lastLoginUpdated = true;
            lastLoginUserId = userId;
        }
    }

    private static class FakePermissionRepository extends PermissionRepository {
        private FakePermissionRepository() {
            super((DataSource) null);
        }

        @Override
        public List<String> findPermissionCodesByRoleId(long roleId) {
            return List.of("REPORT_VIEW", "USER_MANAGE");
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private int successCount;
        private int failedCount;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logLoginSuccess(long userId, String username) {
            successCount++;
        }

        @Override
        public void logLoginFailed(String username, String reason) {
            failedCount++;
        }
    }
}
