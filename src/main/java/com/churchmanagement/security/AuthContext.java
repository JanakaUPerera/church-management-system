package com.churchmanagement.security;

import java.util.Optional;

public final class AuthContext {
    private static AuthenticatedUser currentUser;

    private AuthContext() {
    }

    public static Optional<AuthenticatedUser> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public static void setCurrentUser(AuthenticatedUser user) {
        currentUser = user;
    }

    public static void clearForcePasswordChange() {
        if (currentUser != null) {
            currentUser = currentUser.withForcePasswordChange(false);
        }
    }

    public static void updateCurrentUserProfile(String fullName, String profilePicturePath) {
        if (currentUser != null) {
            currentUser = currentUser.withProfile(fullName, profilePicturePath);
        }
    }

    public static void clear() {
        currentUser = null;
    }
}
