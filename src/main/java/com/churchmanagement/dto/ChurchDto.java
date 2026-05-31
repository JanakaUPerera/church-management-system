package com.churchmanagement.dto;

import com.churchmanagement.entity.Church;
import com.churchmanagement.enums.AuthorizedPersonPosition;
import com.churchmanagement.enums.ReceiptLanguage;

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
    private final String authorizedPersonName;
    private final AuthorizedPersonPosition authorizedPersonPosition;
    private final String authorizedPersonPositionOther;
    private final String smsMobileNumber;
    private final ReceiptLanguage receiptLanguage;
    private final LocalDateTime createdAt;

    public ChurchDto(Long id, String churchCode, String churchName, Long regionId, String regionCode,
                     String regionName, String status, String authorizedPersonName,
                     AuthorizedPersonPosition authorizedPersonPosition, String authorizedPersonPositionOther,
                     String smsMobileNumber, ReceiptLanguage receiptLanguage, LocalDateTime createdAt) {
        this.id = id;
        this.churchCode = churchCode;
        this.churchName = churchName;
        this.regionId = regionId;
        this.regionCode = regionCode;
        this.regionName = regionName;
        this.status = status;
        this.authorizedPersonName = authorizedPersonName;
        this.authorizedPersonPosition = authorizedPersonPosition;
        this.authorizedPersonPositionOther = authorizedPersonPositionOther;
        this.smsMobileNumber = smsMobileNumber;
        this.receiptLanguage = receiptLanguage;
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
                church.getAuthorizedPersonName(),
                church.getAuthorizedPersonPosition(),
                church.getAuthorizedPersonPositionOther(),
                church.getSmsMobileNumber(),
                church.getReceiptLanguage(),
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

    public String getAuthorizedPersonName() {
        return authorizedPersonName;
    }

    public AuthorizedPersonPosition getAuthorizedPersonPosition() {
        return authorizedPersonPosition;
    }

    public String getAuthorizedPersonPositionLabel() {
        if (authorizedPersonPosition == null) {
            return "";
        }

        if (authorizedPersonPosition == AuthorizedPersonPosition.OTHER && authorizedPersonPositionOther != null) {
            return authorizedPersonPositionOther;
        }

        return authorizedPersonPosition.getDisplayLabel();
    }

    public String getAuthorizedPersonPositionOther() {
        return authorizedPersonPositionOther;
    }

    public String getSmsMobileNumber() {
        return smsMobileNumber;
    }

    public ReceiptLanguage getReceiptLanguage() {
        return receiptLanguage;
    }

    public String getReceiptLanguageLabel() {
        return receiptLanguage == null ? "" : receiptLanguage.getDisplayLabel();
    }

    public String getCreatedAt() {
        return createdAt == null ? "" : createdAt.format(DATE_TIME_FORMATTER);
    }

    public boolean isActive() {
        return Church.Status.ACTIVE.name().equals(status);
    }
}
