package com.churchmanagement.dto.report;

public enum ReportType {
    WEEKLY_CHURCH_COLLECTION("Weekly Church-wise Collection Report"),
    WEEKLY_REGION_SUMMARY("Weekly Region-wise Summary Report"),
    SUBMISSION_STATUS("Submission Status Report"),
    LATE_SUBMISSION("Late Submission Report"),
    CHURCH_ANNUAL_COLLECTION("Church-wise Annual Collection Report"),
    REGION_ANNUAL_COLLECTION("Region-wise Annual Collection Report"),
    CHURCH_MONTHLY_COLLECTION("Church-wise Monthly Collection Report"),
    REGION_MONTHLY_COLLECTION("Region-wise Monthly Collection Report"),
    CHURCH_PROGRESS("Church Progress Report"),
    REGION_PROGRESS("Region Progress Report"),
    CANCELLED_RECEIPT("Cancelled Receipt Report"),
    RECEIPT_PRINT_STATUS("Receipt Print Status Report"),
    SMS_DELIVERY("SMS Delivery Report"),
    USER_ACTIVITY("User Activity Report"),
    BACKUP_RESTORE_HISTORY("Backup & Restore History Report");

    private final String displayName;

    ReportType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
