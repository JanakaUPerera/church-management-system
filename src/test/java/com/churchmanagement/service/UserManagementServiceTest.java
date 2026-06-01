package com.churchmanagement.service;

import com.churchmanagement.dto.CreateUserRequest;
import com.churchmanagement.dto.ResetPasswordRequest;
import com.churchmanagement.dto.UpdateUserRequest;
import com.churchmanagement.entity.User;
import com.churchmanagement.repository.UserManagementRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserManagementServiceTest {
    private FakeUserManagementRepository userRepository;
    private FakeActivityLogService activityLogService;
    private UserManagementService userManagementService;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserManagementRepository();
        activityLogService = new FakeActivityLogService();
        userManagementService = new UserManagementService(userRepository, activityLogService,
                Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("UTC")));
        AuthContext.setCurrentUser(new AuthenticatedUser(1L, "admin", "Admin", 1L, "Admin",
                List.of("user.manage")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createUser() {
        User user = userManagementService.create(new CreateUserRequest(
                " jane ", " Jane Doe ", 2L, User.Status.ACTIVE, "secret123", true));

        assertEquals("jane", user.getUsername());
        assertEquals("Jane Doe", user.getFullName());
        assertEquals(2L, user.getRoleId());
        assertEquals(User.Status.ACTIVE, user.getStatus());
        assertTrue(user.isForcePasswordChange());
        assertEquals(ActivityLogService.USER_CREATED, activityLogService.loggedActions.getFirst());
    }

    @Test
    void duplicateUsernameRejected() {
        userManagementService.create(new CreateUserRequest(
                "jane", "Jane Doe", 2L, User.Status.ACTIVE, "secret123", true));

        UserManagementService.UserManagementException exception = assertThrows(
                UserManagementService.UserManagementException.class,
                () -> userManagementService.create(new CreateUserRequest(
                        "JANE", "Jane Other", 2L, User.Status.ACTIVE, "secret123", true)));

        assertEquals("A user with this username already exists.", exception.getMessage());
    }

    @Test
    void passwordHashed() {
        User user = userManagementService.create(new CreateUserRequest(
                "jane", "Jane Doe", 2L, User.Status.ACTIVE, "secret123", true));

        assertNotEquals("secret123", user.getPasswordHash());
        assertTrue(BCrypt.checkpw("secret123", user.getPasswordHash()));
    }

    @Test
    void updateUser() {
        User user = userManagementService.create(new CreateUserRequest(
                "jane", "Jane Doe", 2L, User.Status.ACTIVE, "secret123", true));

        User updated = userManagementService.update(user.getId(), new UpdateUserRequest(
                "janed", "Jane D", 1L, User.Status.ACTIVE, false));

        assertEquals("janed", updated.getUsername());
        assertEquals("Jane D", updated.getFullName());
        assertEquals(1L, updated.getRoleId());
        assertTrue(activityLogService.loggedActions.contains(ActivityLogService.USER_UPDATED));
    }

    @Test
    void deactivateUser() {
        User user = userManagementService.create(new CreateUserRequest(
                "jane", "Jane Doe", 2L, User.Status.ACTIVE, "secret123", true));

        userManagementService.deactivate(user.getId());

        assertEquals(User.Status.INACTIVE, userRepository.findById(user.getId()).orElseThrow().getStatus());
        assertTrue(activityLogService.loggedActions.contains(ActivityLogService.USER_DEACTIVATED));
    }

    @Test
    void blockSelfDeactivation() {
        UserManagementService.UserManagementException exception = assertThrows(
                UserManagementService.UserManagementException.class,
                () -> userManagementService.deactivate(1L));

        assertEquals("You cannot deactivate the currently signed-in user.", exception.getMessage());
    }

    @Test
    void resetPassword() {
        User user = userManagementService.create(new CreateUserRequest(
                "jane", "Jane Doe", 2L, User.Status.ACTIVE, "secret123", false));

        userManagementService.resetPassword(user.getId(), new ResetPasswordRequest("newSecret"));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(updated.isForcePasswordChange());
        assertTrue(BCrypt.checkpw("newSecret", updated.getPasswordHash()));
        assertTrue(activityLogService.loggedActions.contains(ActivityLogService.USER_PASSWORD_RESET));
    }

    @Test
    void userWithoutUserManageRejected() {
        AuthContext.setCurrentUser(new AuthenticatedUser(3L, "user", "User", 2L, "User", List.of("REPORT_VIEW")));

        UserManagementService.UserManagementException exception = assertThrows(
                UserManagementService.UserManagementException.class,
                () -> userManagementService.findAll());

        assertEquals("You do not have permission to manage users.", exception.getMessage());
    }

    private static class FakeUserManagementRepository extends UserManagementRepository {
        private final List<User> users = new ArrayList<>();
        private final List<RoleOption> roles = List.of(new RoleOption(1L, "Admin"), new RoleOption(2L, "User"));
        private long nextId = 2L;

        private FakeUserManagementRepository() {
            super((DataSource) null);
            User admin = new User(1L, "admin", "Admin", 1L, "Admin", User.Status.ACTIVE,
                    BCrypt.hashpw("admin123", BCrypt.gensalt()), false, LocalDateTime.now(), null);
            users.add(admin);
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(users);
        }

        @Override
        public Optional<User> findById(long id) {
            return users.stream().filter(user -> user.getId() == id).findFirst();
        }

        @Override
        public boolean existsByUsername(String username) {
            return users.stream().anyMatch(user -> user.getUsername().equals(username));
        }

        @Override
        public boolean existsByUsernameAndIdNot(String username, long id) {
            return users.stream().anyMatch(user -> user.getUsername().equals(username) && user.getId() != id);
        }

        @Override
        public boolean roleExists(long roleId) {
            return roles.stream().anyMatch(role -> role.id() == roleId);
        }

        @Override
        public List<RoleOption> findRoles() {
            return roles;
        }

        @Override
        public User save(User user) {
            user.setId(nextId++);
            user.setRoleName(roleName(user.getRoleId()));
            users.add(user);
            return user;
        }

        @Override
        public User update(User user) {
            User existing = findById(user.getId()).orElseThrow();
            existing.setUsername(user.getUsername());
            existing.setFullName(user.getFullName());
            existing.setRoleId(user.getRoleId());
            existing.setRoleName(roleName(user.getRoleId()));
            existing.setStatus(user.getStatus());
            existing.setForcePasswordChange(user.isForcePasswordChange());
            existing.setUpdatedAt(user.getUpdatedAt());
            return existing;
        }

        @Override
        public void updateStatus(long id, User.Status status, LocalDateTime updatedAt) {
            User existing = findById(id).orElseThrow();
            existing.setStatus(status);
            existing.setUpdatedAt(updatedAt);
        }

        @Override
        public void updatePassword(long id, String passwordHash, boolean forcePasswordChange,
                                   LocalDateTime updatedAt) {
            User existing = findById(id).orElseThrow();
            existing.setPasswordHash(passwordHash);
            existing.setForcePasswordChange(forcePasswordChange);
            existing.setUpdatedAt(updatedAt);
        }

        private String roleName(long roleId) {
            return roles.stream().filter(role -> role.id() == roleId).findFirst().orElseThrow().name();
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private final List<String> loggedActions = new ArrayList<>();

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logUserAction(long userId, String action, String username) {
            loggedActions.add(action);
        }
    }
}
