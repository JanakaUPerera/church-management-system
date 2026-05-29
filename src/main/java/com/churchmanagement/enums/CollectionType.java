package com.churchmanagement.enums;

public enum CollectionType {
    OFFERTORY("Offertory"),
    TITHES("Tithes"),
    OTHER_DONATIONS("Other Donations");

    private final String displayLabel;

    CollectionType(String displayLabel) {
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
