package com.churchmanagement.dto;

import com.churchmanagement.entity.User;

public class UpdateUserRequest {
    private String username;
    private String fullName;
    private Long roleId;
    private User.Status status;
    private boolean forcePasswordChange;

    public UpdateUserRequest(String username, String fullName, Long roleId, User.Status status,
                             boolean forcePasswordChange) {
        this.username = username;
        this.fullName = fullName;
        this.roleId = roleId;
        this.status = status;
        this.forcePasswordChange = forcePasswordChange;
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

    public User.Status getStatus() {
        return status;
    }

    public boolean isForcePasswordChange() {
        return forcePasswordChange;
    }
}
