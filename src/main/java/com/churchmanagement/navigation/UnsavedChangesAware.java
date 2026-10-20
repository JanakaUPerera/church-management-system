package com.churchmanagement.navigation;

/** A screen that can hold input the user would lose by navigating away. */
public interface UnsavedChangesAware {
    boolean hasUnsavedChanges();
}
