package com.churchmanagement.entity;

import com.churchmanagement.enums.CollectionType;

public record CategoryAssignment(long churchId, CollectionType collectionType, long categoryId, int accountingYear) {
}
