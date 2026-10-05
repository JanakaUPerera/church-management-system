package com.churchmanagement.dto.report;

import com.churchmanagement.enums.CollectionType;

import java.util.Objects;

/**
 * One entry in a report's Except List: either a whole church
 * ({@code collectionType == null}) or one collection type of a church.
 */
public record ReportExclusion(Long churchId, String churchLabel, CollectionType collectionType) {
    public ReportExclusion {
        Objects.requireNonNull(churchId, "churchId");
    }

    public boolean entireChurch() {
        return collectionType == null;
    }

    public String displayText() {
        return churchLabel + " (" + (entireChurch() ? "All collections" : collectionType.getDisplayLabel()) + ")";
    }
}
