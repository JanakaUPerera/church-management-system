package com.churchmanagement.entity;

import com.churchmanagement.enums.AuthorizedPersonPosition;

import java.time.LocalDateTime;

public class Church {
    private Long id;
    private String churchCode;
    private String churchName;
    private Long regionId;
    private String regionCode;
    private String regionName;
    private Status status;
    private String authorizedPersonName;
    private AuthorizedPersonPosition authorizedPersonPosition;
    private String authorizedPersonPositionOther;
    private String smsMobileNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    public Church() {
    }

    public Church(Long id, String churchCode, String churchName, Long regionId, String regionCode, String regionName,
                  Status status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.churchCode = churchCode;
        this.churchName = churchName;
        this.regionId = regionId;
        this.regionCode = regionCode;
        this.regionName = regionName;
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

    public String getChurchCode() {
        return churchCode;
    }

    public void setChurchCode(String churchCode) {
        this.churchCode = churchCode;
    }

    public String getChurchName() {
        return churchName;
    }

    public void setChurchName(String churchName) {
        this.churchName = churchName;
    }

    public Long getRegionId() {
        return regionId;
    }

    public void setRegionId(Long regionId) {
        this.regionId = regionId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getAuthorizedPersonName() {
        return authorizedPersonName;
    }

    public void setAuthorizedPersonName(String authorizedPersonName) {
        this.authorizedPersonName = authorizedPersonName;
    }

    public AuthorizedPersonPosition getAuthorizedPersonPosition() {
        return authorizedPersonPosition;
    }

    public void setAuthorizedPersonPosition(AuthorizedPersonPosition authorizedPersonPosition) {
        this.authorizedPersonPosition = authorizedPersonPosition;
    }

    public String getAuthorizedPersonPositionOther() {
        return authorizedPersonPositionOther;
    }

    public void setAuthorizedPersonPositionOther(String authorizedPersonPositionOther) {
        this.authorizedPersonPositionOther = authorizedPersonPositionOther;
    }

    public String getSmsMobileNumber() {
        return smsMobileNumber;
    }

    public void setSmsMobileNumber(String smsMobileNumber) {
        this.smsMobileNumber = smsMobileNumber;
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
