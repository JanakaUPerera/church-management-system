package com.churchmanagement.entity;

import java.time.LocalDateTime;

public class ReceiptSequence {
    private Long id;
    private int sequenceYear;
    private long lastNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ReceiptSequence() {
    }

    public ReceiptSequence(Long id, int sequenceYear, long lastNumber, LocalDateTime createdAt,
                           LocalDateTime updatedAt) {
        this.id = id;
        this.sequenceYear = sequenceYear;
        this.lastNumber = lastNumber;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getSequenceYear() {
        return sequenceYear;
    }

    public void setSequenceYear(int sequenceYear) {
        this.sequenceYear = sequenceYear;
    }

    public long getLastNumber() {
        return lastNumber;
    }

    public void setLastNumber(long lastNumber) {
        this.lastNumber = lastNumber;
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
