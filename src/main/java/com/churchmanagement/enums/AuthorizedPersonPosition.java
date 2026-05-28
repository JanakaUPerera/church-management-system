package com.churchmanagement.enums;

public enum AuthorizedPersonPosition {
    PASTOR("Pastor"),
    TREASURER("Treasurer"),
    SECRETARY("Secretary"),
    CHURCH_LEADER("Church Leader"),
    BROTHER("Brother"),
    SISTER("Sister"),
    OTHER("Other");

    private final String displayLabel;

    AuthorizedPersonPosition(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    @Override
    public String toString() {
        return displayLabel;
    }
}
