package com.churchmanagement.service;

import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.PermissionRepository;
import com.churchmanagement.repository.UserRepository;
import com.churchmanagement.security.AuthenticatedUser;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class AuthService {
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public AuthService() {
        this(new UserRepository(), new PermissionRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public AuthService(UserRepository userRepository, PermissionRepository permissionRepository,
                       ActivityLogService activityLogService, Clock clock) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public AuthenticatedUser login(String username, String password) {
        String normalizedUsername = username == null ? "" : username.strip();

        if (normalizedUsername.isBlank()) {
            throw new AuthException(AuthFailureReason.USERNAME_REQUIRED, "Username is required.");
        }

        if (password == null || password.isBlank()) {
            throw new AuthException(AuthFailureReason.PASSWORD_REQUIRED, "Password is required.");
        }

        try {
            UserRepository.UserCredentials credentials = userRepository.findByUsername(normalizedUsername)
                    .orElseThrow(() -> invalidCredentials(normalizedUsername));

            if (!credentials.active()) {
                activityLogService.logLoginFailed(normalizedUsername, "Inactive account");
                throw new AuthException(AuthFailureReason.INACTIVE_USER, "This user account is inactive.");
            }

            if (!BCrypt.checkpw(password, credentials.passwordHash())) {
                throw invalidCredentials(normalizedUsername);
            }

            List<String> permissions = permissionRepository.findPermissionCodesByRoleId(credentials.roleId());
            AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                    credentials.userId(),
                    credentials.username(),
                    credentials.fullName(),
                    credentials.roleId(),
                    credentials.roleName(),
                    permissions,
                    credentials.forcePasswordChange()
            );

            userRepository.updateLastLoginAt(credentials.userId(), LocalDateTime.now(clock));
            activityLogService.logLoginSuccess(credentials.userId(), credentials.username());
            if (credentials.forcePasswordChange()) {
                activityLogService.logForcePasswordChangeRequired(credentials.userId(), credentials.username());
            }

            return authenticatedUser;
        } catch (AuthException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new AuthException(AuthFailureReason.DATABASE_ERROR,
                    "Unable to sign in right now. Please try again later.", exception);
        }
    }

    private AuthException invalidCredentials(String username) {
        activityLogService.logLoginFailed(username, "Invalid username or password");
        return new AuthException(AuthFailureReason.INVALID_CREDENTIALS, "Invalid username or password.");
    }

    public enum AuthFailureReason {
        USERNAME_REQUIRED,
        PASSWORD_REQUIRED,
        INVALID_CREDENTIALS,
        INACTIVE_USER,
        DATABASE_ERROR
    }

    public static class AuthException extends RuntimeException {
        private final AuthFailureReason reason;

        public AuthException(AuthFailureReason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public AuthException(AuthFailureReason reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        public AuthFailureReason getReason() {
            return reason;
        }
    }
}
