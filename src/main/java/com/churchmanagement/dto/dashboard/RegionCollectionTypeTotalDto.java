package com.churchmanagement.dto.dashboard;

import java.math.BigDecimal;

public class RegionCollectionTypeTotalDto {
    private String regionName;
    private String collectionType;
    private BigDecimal amount = BigDecimal.ZERO;

    public RegionCollectionTypeTotalDto() {
    }

    public RegionCollectionTypeTotalDto(String regionName, String collectionType, BigDecimal amount) {
        this.regionName = regionName;
        this.collectionType = collectionType;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public String getCollectionType() {
        return collectionType;
    }

    public void setCollectionType(String collectionType) {
        this.collectionType = collectionType;
    }

    public BigDecimal getAmount() {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount == null ? BigDecimal.ZERO : amount;
    }
}
