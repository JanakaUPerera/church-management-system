package com.churchmanagement.dto;

import com.churchmanagement.enums.CollectionType;

import java.math.BigDecimal;

public class ReceiptItemDto {
    private CollectionType collectionType;
    private BigDecimal amount;
    private String note;

    public ReceiptItemDto() {
    }

    public ReceiptItemDto(CollectionType collectionType, BigDecimal amount, String note) {
        this.collectionType = collectionType;
        this.amount = amount;
        this.note = note;
    }

    public CollectionType getCollectionType() {
        return collectionType;
    }

    public void setCollectionType(CollectionType collectionType) {
        this.collectionType = collectionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
