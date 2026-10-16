package com.churchmanagement.repository;

import com.churchmanagement.dto.report.ReportExclusion;
import com.churchmanagement.dto.report.ReportSearchCriteria;
import com.churchmanagement.dto.report.ReportType;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.testsupport.RecordingDataSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportRepositoryTest {
    private final RecordingDataSource dataSource = new RecordingDataSource();
    private final ReportRepository repository = new ReportRepository(dataSource);

    @Test
    void receiptCollectionReportFiltersByCategoryTogetherWithExceptList() {
        ReportSearchCriteria criteria = receiptCollectionCriteria();
        criteria.setCategoryId(5L);
        criteria.setExclusions(List.of(new ReportExclusion(7L, "0007 Hanwella", CollectionType.TITHES)));

        repository.getReceiptCollectionReport(criteria);

        String sql = dataSource.lastSql();
        assertTrue(sql.contains("AND ri.category_id = ?"));
        assertTrue(sql.contains("AND NOT (r.church_id = ? AND ri.collection_type = ?)"));
        assertTrue(dataSource.lastValues().contains(5L));
    }

    @Test
    void summaryTotalsUseTheCategoryFilter() {
        ReportSearchCriteria criteria = receiptCollectionCriteria();
        criteria.setCategoryId(5L);

        // RecordingDataSource returns no rows and queryOne() calls orElseThrow(); only the SQL matters here.
        assertThrows(NoSuchElementException.class, () -> repository.getSummaryTotals(criteria));

        assertTrue(dataSource.lastSql().contains("AND ri.category_id = ?"));
        assertTrue(dataSource.lastValues().contains(5L));
    }

    @Test
    void noCategorySelectedAddsNoFilter() {
        repository.getReceiptCollectionReport(receiptCollectionCriteria());

        assertFalse(dataSource.lastSql().contains("ri.category_id"));
    }

    private ReportSearchCriteria receiptCollectionCriteria() {
        ReportSearchCriteria criteria = new ReportSearchCriteria();
        criteria.setReportType(ReportType.RECEIPT_COLLECTION);
        return criteria;
    }
}
