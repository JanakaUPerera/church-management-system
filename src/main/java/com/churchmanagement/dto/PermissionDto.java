package com.churchmanagement.dto;

import com.churchmanagement.entity.Permission;

public class PermissionDto {
    private final Long id;
    private final String permissionCode;
    private final String module;
    private final String description;

    public PermissionDto(Long id, String permissionCode, String module, String description) {
        this.id = id;
        this.permissionCode = permissionCode;
        this.module = module;
        this.description = description;
    }

    public static PermissionDto fromPermission(Permission permission) {
        return new PermissionDto(permission.getId(), permission.getPermissionCode(), permission.getModule(),
                permission.getDescription());
    }

    public Long getId() {
        return id;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public String getModule() {
        return module;
    }

    public String getDescription() {
        return description;
    }
}
