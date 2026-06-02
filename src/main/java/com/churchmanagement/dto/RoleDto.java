package com.churchmanagement.dto;

import com.churchmanagement.entity.Role;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RoleDto {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Long id;
    private final String roleName;
    private final String status;
    private final LocalDateTime createdAt;

    public RoleDto(Long id, String roleName, String status, LocalDateTime createdAt) {
        this.id = id;
        this.roleName = roleName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static RoleDto fromRole(Role role) {
        return new RoleDto(role.getId(), role.getRoleName(), role.getStatus().name(), role.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt == null ? "" : createdAt.format(DATE_TIME_FORMATTER);
    }

    public boolean isActive() {
        return Role.Status.ACTIVE.name().equals(status);
    }
}
