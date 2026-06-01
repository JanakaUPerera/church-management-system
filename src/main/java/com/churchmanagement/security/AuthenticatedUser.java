package com.churchmanagement.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AuthenticatedUser {
    private final long userId;
    private final String username;
    private final String fullName;
    private final long roleId;
    private final String roleName;
    private final List<String> permissions;
    private final Set<String> permissionSet;
    private final boolean forcePasswordChange;

    public AuthenticatedUser(long userId, String username, String fullName, long roleId, String roleName,
                             List<String> permissions) {
        this(userId, username, fullName, roleId, roleName, permissions, false);
    }

    public AuthenticatedUser(long userId, String username, String fullName, long roleId, String roleName,
                             List<String> permissions, boolean forcePasswordChange) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.roleId = roleId;
        this.roleName = roleName;
        this.permissions = List.copyOf(permissions);
        this.permissionSet = new HashSet<>(this.permissions);
        this.forcePasswordChange = forcePasswordChange;
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public long getRoleId() {
        return roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public List<String> getPermissions() {
        return Collections.unmodifiableList(permissions);
    }

    public boolean isForcePasswordChange() {
        return forcePasswordChange;
    }

    public AuthenticatedUser withForcePasswordChange(boolean forcePasswordChange) {
        return new AuthenticatedUser(userId, username, fullName, roleId, roleName, permissions, forcePasswordChange);
    }

    public boolean hasPermission(String permissionCode) {
        return permissionCode != null && permissionSet.contains(permissionCode);
    }

    public boolean hasAnyPermission(String... permissionCodes) {
        if (permissionCodes == null) {
            return false;
        }

        for (String permissionCode : permissionCodes) {
            if (hasPermission(permissionCode)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasRole(String roleName) {
        return roleName != null && this.roleName.toLowerCase(Locale.ROOT)
                .equals(roleName.toLowerCase(Locale.ROOT));
    }
}
