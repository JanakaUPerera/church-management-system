package com.churchmanagement.service;

import com.churchmanagement.dto.CreateUserRequest;
import com.churchmanagement.dto.ResetPasswordRequest;
import com.churchmanagement.dto.UpdateUserRequest;
import com.churchmanagement.entity.User;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.UserManagementRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.validation.UserValidator;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public class UserManagementService {
    private final UserManagementRepository userRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public UserManagementService() {
        this(new UserManagementRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public UserManagementService(UserManagementRepository userRepository, ActivityLogService activityLogService,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public List<User> findAll() {
        requirePermission("user.menu.view");
        try {
            return userRepository.findAll();
        } catch (DatabaseException exception) {
            throw new UserManagementException("Unable to load users right now. Please try again later.", exception);
        }
    }

    public List<User> search(String searchText) {
        requirePermission("user.menu.view");
        if (searchText == null || searchText.isBlank()) {
            return findAll();
        }
        try {
            return userRepository.search(searchText.strip());
        } catch (DatabaseException exception) {
            throw new UserManagementException("Unable to search users right now. Please try again later.", exception);
        }
    }

    public List<UserManagementRepository.RoleOption> findRoles() {
        requirePermission("user.menu.view");
        try {
            return userRepository.findRoles();
        } catch (DatabaseException exception) {
            throw new UserManagementException("Unable to load roles right now. Please try again later.", exception);
        }
    }

    public User create(CreateUserRequest request) {
        AuthenticatedUser currentUser = requirePermission("user.create");
        CreateUserRequest normalized = normalizeCreateRequest(request);
        validateCreate(normalized);

        try {
            ensureRoleExists(normalized.getRoleId());
            if (userRepository.existsByUsername(normalized.getUsername())) {
                throw new UserManagementException("A user with this username already exists.");
            }

            User user = new User();
            user.setUsername(normalized.getUsername());
            user.setFullName(normalized.getFullName());
            user.setRoleId(normalized.getRoleId());
            user.setStatus(normalized.getStatus());
            user.setPasswordHash(hashPassword(normalized.getTemporaryPassword()));
            user.setForcePasswordChange(normalized.isForcePasswordChange());
            user.setCreatedAt(LocalDateTime.now(clock));

            User savedUser = userRepository.save(user);
            activityLogService.logUserAction(currentUser.getUserId(), ActivityLogService.USER_CREATED,
                    savedUser.getUsername());
            return savedUser;
        } catch (UserManagementException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new UserManagementException("Unable to save user right now. Please try again later.", exception);
        }
    }

    public User update(long id, UpdateUserRequest request) {
        AuthenticatedUser currentUser = requirePermission("user.update");
        UpdateUserRequest normalized = normalizeUpdateRequest(request);
        validateUpdate(normalized);

        try {
            User existing = userRepository.findById(id)
                    .orElseThrow(() -> new UserManagementException("User could not be found."));
            ensureRoleExists(normalized.getRoleId());
            if (userRepository.existsByUsernameAndIdNot(normalized.getUsername(), id)) {
                throw new UserManagementException("A user with this username already exists.");
            }
            if (existing.getId().equals(currentUser.getUserId()) && normalized.getStatus() == User.Status.INACTIVE) {
                throw new UserManagementException("You cannot deactivate the currently signed-in user.");
            }

            User user = new User();
            user.setId(id);
            user.setUsername(normalized.getUsername());
            user.setFullName(normalized.getFullName());
            user.setRoleId(normalized.getRoleId());
            user.setStatus(normalized.getStatus());
            user.setForcePasswordChange(normalized.isForcePasswordChange());
            user.setUpdatedAt(LocalDateTime.now(clock));

            User updatedUser = userRepository.update(user);
            activityLogService.logUserAction(currentUser.getUserId(), ActivityLogService.USER_UPDATED,
                    updatedUser.getUsername());
            return updatedUser;
        } catch (UserManagementException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new UserManagementException("Unable to update user right now. Please try again later.", exception);
        }
    }

    public void activate(long id) {
        updateStatus(id, User.Status.ACTIVE, ActivityLogService.USER_ACTIVATED);
    }

    public void deactivate(long id) {
        updateStatus(id, User.Status.INACTIVE, ActivityLogService.USER_DEACTIVATED);
    }

    public void resetPassword(long id, ResetPasswordRequest request) {
        AuthenticatedUser currentUser = requirePermission("user.update");
        ResetPasswordRequest normalized = new ResetPasswordRequest(
                request == null ? "" : nullToBlank(request.getTemporaryPassword()),
                request != null && request.isForcePasswordChange());
        List<String> errors = UserValidator.validateForPasswordReset(normalized.getTemporaryPassword());
        if (!errors.isEmpty()) {
            throw new UserManagementException(String.join("\n", errors));
        }

        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new UserManagementException("User could not be found."));
            userRepository.updatePassword(id, hashPassword(normalized.getTemporaryPassword()),
                    normalized.isForcePasswordChange(),
                    LocalDateTime.now(clock));
            activityLogService.logUserAction(currentUser.getUserId(), ActivityLogService.USER_PASSWORD_RESET,
                    user.getUsername());
        } catch (UserManagementException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new UserManagementException("Unable to reset password right now. Please try again later.", exception);
        }
    }

    private void updateStatus(long id, User.Status status, String action) {
        AuthenticatedUser currentUser = requirePermission("user.delete");
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new UserManagementException("User could not be found."));
            if (user.getId().equals(currentUser.getUserId()) && status == User.Status.INACTIVE) {
                throw new UserManagementException("You cannot deactivate the currently signed-in user.");
            }

            userRepository.updateStatus(id, status, LocalDateTime.now(clock));
            activityLogService.logUserAction(currentUser.getUserId(), action, user.getUsername());
        } catch (UserManagementException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new UserManagementException("Unable to update user status right now. Please try again later.",
                    exception);
        }
    }

    private AuthenticatedUser requirePermission(String permissionCode) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new UserManagementException("Please sign in to manage users."));
        try {
            new PermissionGuard(currentUser).require(permissionCode);
            return currentUser;
        } catch (SecurityException exception) {
            throw new UserManagementException("You do not have permission to perform this action.", exception);
        }
    }

    private CreateUserRequest normalizeCreateRequest(CreateUserRequest request) {
        if (request == null) {
            return new CreateUserRequest("", "", null, null, "", false);
        }
        return new CreateUserRequest(normalizeUsername(request.getUsername()), nullToBlank(request.getFullName()),
                request.getRoleId(), request.getStatus(), nullToBlank(request.getTemporaryPassword()),
                request.isForcePasswordChange());
    }

    private UpdateUserRequest normalizeUpdateRequest(UpdateUserRequest request) {
        if (request == null) {
            return new UpdateUserRequest("", "", null, null, false);
        }
        return new UpdateUserRequest(normalizeUsername(request.getUsername()), nullToBlank(request.getFullName()),
                request.getRoleId(), request.getStatus(), request.isForcePasswordChange());
    }

    private void validateCreate(CreateUserRequest request) {
        List<String> errors = UserValidator.validateForCreate(request.getUsername(), request.getFullName(),
                request.getRoleId(), request.getStatus(), request.getTemporaryPassword());
        if (!errors.isEmpty()) {
            throw new UserManagementException(String.join("\n", errors));
        }
    }

    private void validateUpdate(UpdateUserRequest request) {
        List<String> errors = UserValidator.validateForUpdate(request.getUsername(), request.getFullName(),
                request.getRoleId(), request.getStatus());
        if (!errors.isEmpty()) {
            throw new UserManagementException(String.join("\n", errors));
        }
    }

    private void ensureRoleExists(long roleId) {
        if (!userRepository.roleExists(roleId)) {
            throw new UserManagementException("Selected role could not be found.");
        }
    }

    private String hashPassword(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value.strip();
    }

    public static class UserManagementException extends RuntimeException {
        public UserManagementException(String message) {
            super(message);
        }

        public UserManagementException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
