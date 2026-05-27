package com.churchmanagement.security;

import java.util.HashMap;
import java.util.Map;

public class PermissionGuard {
    private static final Map<String, String[]> LEGACY_PERMISSION_ALIASES = new HashMap<>();

    static {
        LEGACY_PERMISSION_ALIASES.put("region.view", new String[]{"REGION_MANAGE"});
        LEGACY_PERMISSION_ALIASES.put("church.view", new String[]{"CHURCH_MANAGE"});
        LEGACY_PERMISSION_ALIASES.put("receipt.create", new String[]{"RECEIPT_CREATE"});
        LEGACY_PERMISSION_ALIASES.put("receipt.view", new String[]{"RECEIPT_CREATE", "RECEIPT_CANCEL"});
        LEGACY_PERMISSION_ALIASES.put("report.view", new String[]{"REPORT_VIEW"});
        LEGACY_PERMISSION_ALIASES.put("user.manage", new String[]{"USER_MANAGE"});
        LEGACY_PERMISSION_ALIASES.put("role.manage", new String[]{"ROLE_MANAGE"});
        LEGACY_PERMISSION_ALIASES.put("backup.create", new String[]{"BACKUP_MANAGE"});
        LEGACY_PERMISSION_ALIASES.put("backup.restore", new String[]{"BACKUP_MANAGE"});
        LEGACY_PERMISSION_ALIASES.put("settings.manage", new String[]{"SETTINGS_MANAGE"});
    }

    private final AuthenticatedUser user;

    public PermissionGuard(AuthenticatedUser user) {
        this.user = user;
    }

    public boolean can(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return true;
        }

        if (user.hasRole("Admin")) {
            return true;
        }

        if (user.hasPermission(permissionCode)) {
            return true;
        }

        String[] aliases = LEGACY_PERMISSION_ALIASES.get(permissionCode);
        return aliases != null && user.hasAnyPermission(aliases);
    }

    public boolean canAny(String... permissionCodes) {
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
