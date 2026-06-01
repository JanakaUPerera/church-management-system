package com.churchmanagement.dto;

import com.churchmanagement.entity.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class UserDto {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Long id;
    private final String username;
    private final String fullName;
    private final Long roleId;
    private final String roleName;
    private final String status;
    private final boolean forcePasswordChange;
    private final LocalDateTime createdAt;

    public UserDto(Long id, String username, String fullName, Long roleId, String roleName, String status,
                   boolean forcePasswordChange, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.roleId = roleId;
        this.roleName = roleName;
        this.status = status;
        this.forcePasswordChange = forcePasswordChange;
        this.createdAt = createdAt;
    }

    public static UserDto fromUser(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getFullName(), user.getRoleId(),
                user.getRoleName(), user.getStatus().name(), user.isForcePasswordChange(), user.getCreatedAt());
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public Long getRoleId() {
        return roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getStatus() {
        return status;
    }

    public boolean isForcePasswordChange() {
        return forcePasswordChange;
    }

    public String getForcePasswordChangeLabel() {
        return forcePasswordChange ? "Yes" : "No";
    }

    public String getCreatedAt() {
        return createdAt == null ? "" : createdAt.format(DATE_TIME_FORMATTER);
    }

    public boolean isActive() {
        return User.Status.ACTIVE.name().equals(status);
    }
}
