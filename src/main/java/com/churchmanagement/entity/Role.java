package com.churchmanagement.entity;

import java.time.LocalDateTime;

public class Role {
    private Long id;
    private String roleName;
    private Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    public Role() {
    }

    public Role(Long id, String roleName, Status status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.roleName = roleName;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
