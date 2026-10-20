package com.churchmanagement.dto.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportTypeGroupTest {
    @Test
    void messagingHasOnlySmsDelivery() {
        assertEquals(List.of(ReportType.SMS_DELIVERY), ReportType.inGroup(ReportGroup.MESSAGING));
    }

    @Test
    void administrationHasActivityAndBackupHistory() {
        assertEquals(List.of(ReportType.USER_ACTIVITY, ReportType.BACKUP_RESTORE_HISTORY),
                ReportType.inGroup(ReportGroup.ADMINISTRATION));
    }

    @Test
    void collectionsHasTheOtherThirteenInOrder() {
        List<ReportType> collections = ReportType.inGroup(ReportGroup.COLLECTIONS);

        assertEquals(13, collections.size());
        assertEquals(ReportType.RECEIPT_COLLECTION, collections.getFirst());
        assertEquals(ReportType.RECEIPT_PRINT_STATUS, collections.getLast());
    }

    @Test
    void noGroupMeansEveryType() {
        assertEquals(List.of(ReportType.values()), ReportType.inGroup(null));
    }
}
