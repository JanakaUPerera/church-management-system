package com.churchmanagement.security;

public class PermissionGuard {
    private final AuthenticatedUser user;

    public PermissionGuard(AuthenticatedUser user) {
        this.user = user;
    }

    public boolean can(String permissionCode) {
        if (user.isForcePasswordChange()) {
            return false;
        }

        if (permissionCode == null || permissionCode.isBlank()) {
            return true;
        }

        if (user.hasRole("Admin")) {
            return true;
        }

        return user.hasPermission(permissionCode);
    }

    public boolean canAny(String... permissionCodes) {
        if (user.isForcePasswordChange()) {
            return false;
        }

        if (permissionCodes == null || permissionCodes.length == 0) {
            return true;
        }

        for (String permissionCode : permissionCodes) {
            if (can(permissionCode)) {
                return true;
            }
        }

        return false;
    }

    public void require(String permissionCode) {
        if (!can(permissionCode)) {
            throw new SecurityException("You do not have permission to access this module.");
        }
    }
}
