package com.churchmanagement.dto;

import java.util.List;

public class RolePermissionUpdateRequest {
    private final Long roleId;
    private final List<String> permissionCodes;

    public RolePermissionUpdateRequest(Long roleId, List<String> permissionCodes) {
        this.roleId = roleId;
        this.permissionCodes = permissionCodes == null ? List.of() : List.copyOf(permissionCodes);
    }

    public Long getRoleId() {
        return roleId;
    }

    public List<String> getPermissionCodes() {
        return permissionCodes;
    }
}
