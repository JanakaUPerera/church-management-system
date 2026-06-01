package com.churchmanagement.service;

import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.UserRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.validation.PasswordValidator;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class PasswordChangeService {
    private final UserRepository userRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;

    public PasswordChangeService() {
        this(new UserRepository(), new ActivityLogService(), Clock.systemDefaultZone());
    }

    public PasswordChangeService(UserRepository userRepository, ActivityLogService activityLogService, Clock clock) {
        this.userRepository = userRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
    }

    public void changeForcedPassword(String currentPassword, String newPassword, String confirmPassword) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new PasswordChangeException("Please sign in to change your password."));

        try {
            validate(currentPassword, newPassword, confirmPassword, currentUser);
            String newPasswordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
            userRepository.updatePasswordAndClearForceChange(currentUser.getUserId(), newPasswordHash,
                    LocalDateTime.now(clock));
            AuthContext.clearForcePasswordChange();
            activityLogService.logForcedPasswordChanged(currentUser.getUserId(), currentUser.getUsername());
        } catch (PasswordChangeException exception) {
            activityLogService.logPasswordChangeFailed(currentUser.getUserId(), currentUser.getUsername(),
                    exception.getMessage());
            throw exception;
        } catch (DatabaseException exception) {
            activityLogService.logPasswordChangeFailed(currentUser.getUserId(), currentUser.getUsername(),
                    "Database error");
            throw new PasswordChangeException("Unable to change password right now. Please try again later.",
                    exception);
        }
    }

    private void validate(String currentPassword, String newPassword, String confirmPassword,
                          AuthenticatedUser currentUser) {
        List<String> errors = PasswordValidator.validateForcedChange(currentPassword, newPassword, confirmPassword);
        if (!errors.isEmpty()) {
            throw new PasswordChangeException(String.join("\n", errors));
        }

        String currentPasswordHash = userRepository.findPasswordHashByUserId(currentUser.getUserId())
                .orElseThrow(() -> new PasswordChangeException("User account could not be found."));
        if (!BCrypt.checkpw(currentPassword, currentPasswordHash)) {
            throw new PasswordChangeException("Current password is incorrect.");
        }
    }

    public static class PasswordChangeException extends RuntimeException {
        public PasswordChangeException(String message) {
            super(message);
        }

        public PasswordChangeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
