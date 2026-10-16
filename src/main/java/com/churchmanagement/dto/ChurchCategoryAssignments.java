package com.churchmanagement.dto;

import com.churchmanagement.enums.CollectionType;

import java.util.Map;

/**
 * A branch's categories for one accounting year, as shown in the branch dialog.
 * When the year has no rows yet, {@code categoryIds} holds the values that would be
 * carried forward and {@code carriedForwardFrom} names their year.
 */
public record ChurchCategoryAssignments(int year, Map<CollectionType, Long> categoryIds, boolean locked,
                                        String lockReason, Integer carriedForwardFrom) {
    public ChurchCategoryAssignments {
        categoryIds = Map.copyOf(categoryIds);
    }
}
