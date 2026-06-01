package com.churchmanagement.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PermissionGuardTest {
    @Test
    void forcedUserCannotAccessDashboardOrModules() {
        AuthenticatedUser user = new AuthenticatedUser(5L, "forced", "Forced User", 1L, "Admin",
                List.of("user.manage"), true);
        PermissionGuard permissionGuard = new PermissionGuard(user);

        assertFalse(permissionGuard.can(null));
        assertFalse(permissionGuard.canAny());
        assertFalse(permissionGuard.can("user.manage"));
    }
}
