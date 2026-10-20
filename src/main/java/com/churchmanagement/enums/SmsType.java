package com.churchmanagement.enums;

public enum SmsType {
    RECEIPT("Receipt"),
    OFFICIAL_MESSAGE("Official");

    private final String label;

    SmsType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static SmsType fromDatabase(String value) {
        if (value == null) {
            return RECEIPT;
        }
        for (SmsType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return RECEIPT;
    }
}
