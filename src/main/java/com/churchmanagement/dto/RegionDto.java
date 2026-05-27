package com.churchmanagement.dto;

import com.churchmanagement.entity.Region;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RegionDto {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Long id;
    private final String regionCode;
    private final String regionName;
    private final String status;
    private final LocalDateTime createdAt;

    public RegionDto(Long id, String regionCode, String regionName, String status, LocalDateTime createdAt) {
        this.id = id;
        this.regionCode = regionCode;
        this.regionName = regionName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static RegionDto fromRegion(Region region) {
        return new RegionDto(
                region.getId(),
                region.getRegionCode(),
                region.getRegionName(),
                region.getStatus().name(),
                region.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
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
        if (createdAt == null) {
            return "";
        }

        return createdAt.format(DATE_TIME_FORMATTER);
    }

    public boolean isActive() {
        return Region.Status.ACTIVE.name().equals(status);
    }
}
