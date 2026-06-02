package com.churchmanagement.entity;

import java.time.LocalDateTime;

public class Permission {
    private Long id;
    private String permissionCode;
    private String module;
    private String description;
    private LocalDateTime createdAt;

    public Permission() {
    }

    public Permission(Long id, String permissionCode, String module, String description, LocalDateTime createdAt) {
        this.id = id;
        this.permissionCode = permissionCode;
        this.module = module;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
