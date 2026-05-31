package com.churchmanagement.enums;

public enum ReceiptLanguage {
    ENGLISH("en", "English"),
    SINHALA("si", "සිංහල"),
    TAMIL("ta", "தமிழ்");

    private final String code;
    private final String displayName;

    ReceiptLanguage(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayLabel() {
        return displayName;
    }
}
