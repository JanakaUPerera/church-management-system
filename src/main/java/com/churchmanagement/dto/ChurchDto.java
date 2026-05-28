package com.churchmanagement.dto;

import com.churchmanagement.entity.Church;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChurchDto {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Long id;
    private final String churchCode;
    private final String churchName;
    private final Long regionId;
    private final String regionCode;
    private final String regionName;
    private final String status;
    private final LocalDateTime createdAt;

    public ChurchDto(Long id, String churchCode, String churchName, Long regionId, String regionCode,
                     String regionName, String status, LocalDateTime createdAt) {
        this.id = id;
        this.churchCode = churchCode;
        this.churchName = churchName;
        this.regionId = regionId;
        this.regionCode = regionCode;
        this.regionName = regionName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static ChurchDto fromChurch(Church church) {
        return new ChurchDto(
                church.getId(),
                church.getChurchCode(),
                church.getChurchName(),
                church.getRegionId(),
                church.getRegionCode(),
                church.getRegionName(),
                church.getStatus().name(),
                church.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public String getChurchCode() {
        return churchCode;
    }

    public String getChurchName() {
        return churchName;
    }

    public Long getRegionId() {
        return regionId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getRegionName() {
        return regionName;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt == null ? "" : createdAt.format(DATE_TIME_FORMATTER);
    }

    public boolean isActive() {
        return Church.Status.ACTIVE.name().equals(status);
    }
}
