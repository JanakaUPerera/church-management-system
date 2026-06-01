package com.churchmanagement.dto;

import com.churchmanagement.entity.User;

public class CreateUserRequest {
    private String username;
    private String fullName;
    private Long roleId;
    private User.Status status;
    private String temporaryPassword;
    private boolean forcePasswordChange;

    public CreateUserRequest(String username, String fullName, Long roleId, User.Status status,
                             String temporaryPassword, boolean forcePasswordChange) {
        this.username = username;
        this.fullName = fullName;
        this.roleId = roleId;
        this.status = status;
        this.temporaryPassword = temporaryPassword;
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

    public String getTemporaryPassword() {
        return temporaryPassword;
    }

    public boolean isForcePasswordChange() {
        return forcePasswordChange;
    }
}
