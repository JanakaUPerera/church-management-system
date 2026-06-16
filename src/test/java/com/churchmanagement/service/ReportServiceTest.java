package com.churchmanagement.service;

import com.churchmanagement.dto.PrintResult;
import com.churchmanagement.dto.report.*;
import com.churchmanagement.repository.ReportRepository;
import com.churchmanagement.reports.export.ReportExcelExporter;
import com.churchmanagement.reports.export.ReportPdfExporter;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {
    private FakeReportRepository repository;
    private ReportService service;

    @BeforeEach
    void setUp() {
        repository = new FakeReportRepository();
        service = new ReportService(repository, new ActivityLogService(null), new ReportPdfExporter(fixedClock()),
                new ReportExcelExporter(), new CapturingPrinterService(), fixedClock());
        AuthContext.setCurrentUser(user("report.view", "report.export", "report.print"));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void weeklyReportTotalsUseRepositoryActiveOnlyTotals() {
        ReportResult<? extends ReportTableRow> result = service.loadReport(criteria(ReportType.WEEKLY_CHURCH_COLLECTION));

        assertEquals(new BigDecimal("100.00"), result.getTotals().getOffertoryTotal());
        assertEquals(new BigDecimal("25.00"), result.getTotals().getTithesTotal());
        assertEquals(new BigDecimal("15.00"), result.getTotals().getOtherDonationsTotal());
        assertEquals(new BigDecimal("140.00"), result.getTotals().getGrandTotal());
    }

    @Test
    void cancelledReceiptsExcludedFromNormalTotals() {
        service.loadReport(criteria(ReportType.CHURCH_ANNUAL_COLLECTION));

        assertFalse(repository.summaryIncludedCancelledReceipts);
    }

    @Test
    void submissionStatusIncludesMissingChurches() {
        ReportResult<? extends ReportTableRow> result = service.loadReport(criteria(ReportType.SUBMISSION_STATUS));

        assertEquals(2, result.getRows().size());
        assertTrue(result.getRows().stream().anyMatch(row -> "MISSING".equals(row.columns().get("Status"))));
    }

    @Test
    void lateSubmissionFilteringShowsOnlyLateRows() {
        ReportResult<? extends ReportTableRow> result = service.loadReport(criteria(ReportType.LATE_SUBMISSION));

        assertEquals(1, result.getRows().size());
        assertTrue(result.getRows().getFirst().searchText().contains("late reason"));
    }

    @Test
    void annualTotalsArePreparedForFinancialReport() {
        ReportResult<? extends ReportTableRow> result = service.loadReport(criteria(ReportType.CHURCH_ANNUAL_COLLECTION));

        assertEquals("140.00", result.getTotals().getGrandTotal().toPlainString());
    }

    @Test
    void annualCollectionDoesNotShowMonthColumn() {
        ReportResult<? extends ReportTableRow> result = service.loadReport(criteria(ReportType.CHURCH_ANNUAL_COLLECTION));

        assertFalse(result.getRows().getFirst().columns().containsKey("Month"));
    }

    @Test
    void annualCollectionReportsDefaultFromDateToCurrentYearStart() {
        assertEquals(LocalDate.of(2026, 1, 1),
                service.defaultCriteria(ReportType.CHURCH_ANNUAL_COLLECTION).getDateFrom());
        assertEquals(LocalDate.of(2026, 1, 1),
                service.defaultCriteria(ReportType.REGION_ANNUAL_COLLECTION).getDateFrom());
    }

    @Test
    void monthlyCollectionShowsMonthName() {
        ReportResult<? extends ReportTableRow> result = service.loadReport(criteria(ReportType.REGION_MONTHLY_COLLECTION));

        assertEquals("June", result.getRows().getFirst().columns().get("Month"));
    }

    @Test
    void exportPdfCreatesFile() {
        Path pdf = service.exportPdf(criteria(ReportType.WEEKLY_CHURCH_COLLECTION));

        assertTrue(Files.exists(pdf));
        assertTrue(pdf.toString().endsWith(".pdf"));
    }

    @Test
    void exportExcelCreatesFile() {
        Path excel = service.exportExcel(criteria(ReportType.WEEKLY_CHURCH_COLLECTION));

        assertTrue(Files.exists(excel));
        assertTrue(excel.toString().endsWith(".xlsx"));
    }

    @Test
    void excelExportCreatesChartSheetForSupportedReports() throws Exception {
        Path excel = service.exportExcel(criteria(ReportType.WEEKLY_CHURCH_COLLECTION));

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(excel))) {
            assertNotNull(workbook.getSheet("Charts"));
        }
    }

    @Test
    void excelExportSkipsChartSheetForLateSubmissionReport() throws Exception {
        Path excel = service.exportExcel(criteria(ReportType.LATE_SUBMISSION));

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(excel))) {
            assertNull(workbook.getSheet("Charts"));
        }
    }

    @Test
    void printPermissionIsEnforced() {
        AuthContext.setCurrentUser(user("report.view"));

        ReportService.ReportException exception = assertThrows(ReportService.ReportException.class,
                () -> service.printReport(criteria(ReportType.WEEKLY_CHURCH_COLLECTION)));

        assertEquals("Print failed.", exception.getMessage());
    }

    @Test
    void paginationIsPassedToRepository() {
        ReportSearchCriteria criteria = criteria(ReportType.WEEKLY_CHURCH_COLLECTION);
        criteria.setOffset(25);
        criteria.setLimit(25);

        service.loadReport(criteria);

        assertEquals(25, repository.lastCriteria.getOffset());
        assertEquals(25, repository.lastCriteria.getLimit());
    }

    @Test
    void regionFilteringIsPassedToRepository() {
        ReportSearchCriteria criteria = criteria(ReportType.WEEKLY_CHURCH_COLLECTION);
        criteria.setRegionId(7L);

        service.loadReport(criteria);

        assertEquals(7L, repository.lastCriteria.getRegionId());
    }

    @Test
    void churchFilteringIsPassedToRepository() {
        ReportSearchCriteria criteria = criteria(ReportType.WEEKLY_CHURCH_COLLECTION);
        criteria.setChurchId(9L);

        service.loadReport(criteria);

        assertEquals(9L, repository.lastCriteria.getChurchId());
    }

    @Test
    void dateValidationRejectsInvalidRange() {
        ReportSearchCriteria criteria = criteria(ReportType.CHURCH_ANNUAL_COLLECTION);
        criteria.setDateFrom(LocalDate.of(2026, 6, 2));
        criteria.setDateTo(LocalDate.of(2026, 6, 1));

        ReportService.ReportException exception = assertThrows(ReportService.ReportException.class,
                () -> service.loadReport(criteria));

        assertEquals("Invalid date range.", exception.getMessage());
    }

    @Test
    void weeklyReportsIgnoreHiddenDateRangeFilters() {
        ReportSearchCriteria criteria = criteria(ReportType.SUBMISSION_STATUS);
        criteria.setWeekStartDate(LocalDate.of(2026, 5, 25));
        criteria.setDateFrom(LocalDate.of(2026, 6, 1));
        criteria.setDateTo(LocalDate.of(2026, 6, 30));

        service.loadReport(criteria);

        assertNull(repository.lastCriteria.getDateFrom());
        assertNull(repository.lastCriteria.getDateTo());
        assertEquals(LocalDate.of(2026, 5, 25), repository.lastCriteria.getWeekStartDate());
    }

    @Test
    void submissionStatusIncludesLateSubmissionColumn() {
        ReportResult<? extends ReportTableRow> result = service.loadReport(criteria(ReportType.SUBMISSION_STATUS));

        assertEquals("Yes", result.getRows().getFirst().columns().get("Late Submission"));
    }

    @Test
    void userActivityReportShowsIndividualActivityRows() {
        ReportResult<? extends ReportTableRow> result = service.loadReport(criteria(ReportType.USER_ACTIVITY));

        assertEquals("LOGIN", result.getRows().getFirst().columns().get("Action"));
        assertTrue(result.getRows().getFirst().columns().containsKey("Details"));
        assertFalse(result.getRows().getFirst().columns().containsKey("Activity Count"));
    }

    private ReportSearchCriteria criteria(ReportType reportType) {
        ReportSearchCriteria criteria = new ReportSearchCriteria();
        criteria.setReportType(reportType);
        criteria.setWeekStartDate(LocalDate.of(2026, 6, 1));
        criteria.setDateFrom(LocalDate.of(2026, 1, 1));
        criteria.setDateTo(LocalDate.of(2026, 12, 31));
        criteria.setLimit(100);
        return criteria;
    }

    private AuthenticatedUser user(String... permissions) {
        return new AuthenticatedUser(1L, "admin", "Admin", 1L, "User", List.of(permissions));
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-06-08T00:00:00Z"), ZoneId.of("UTC"));
    }

    private static class FakeReportRepository extends ReportRepository {
        private ReportSearchCriteria lastCriteria;
        private boolean summaryIncludedCancelledReceipts;

        private FakeReportRepository() {
            super(null);
        }

        @Override
        public List<WeeklyChurchCollectionReportDto> getWeeklyChurchCollectionReport(ReportSearchCriteria criteria) {
            lastCriteria = criteria;
            WeeklyChurchCollectionReportDto row = new WeeklyChurchCollectionReportDto();
            row.setReceiptId(10L);
            row.setRegionName("North");
            row.setChurchCode("CH001");
            row.setChurchName("Main Church");
            row.setWeekStartDate(criteria.getWeekStartDate());
            row.setReceiptNo("R-1");
            row.setOffertoryTotal(new BigDecimal("100.00"));
            row.setTithesTotal(new BigDecimal("25.00"));
            row.setOtherDonationsTotal(new BigDecimal("15.00"));
            row.setGrandTotal(new BigDecimal("140.00"));
            return List.of(row);
        }

        @Override
        public List<LateSubmissionReportDto> getLateSubmissionReport(ReportSearchCriteria criteria) {
            lastCriteria = criteria;
            LateSubmissionReportDto row = new LateSubmissionReportDto();
            row.setReceiptId(3L);
            row.setRegionName("North");
            row.setChurchCode("CH003");
            row.setChurchName("Late Church");
            row.setWeekStartDate(criteria.getWeekStartDate());
            row.setReason("Late reason");
            return List.of(row);
        }

        @Override
        public List<CollectionReportDto> getCollectionReport(ReportSearchCriteria criteria, boolean churchWise,
                                                             boolean monthly) {
            lastCriteria = criteria;
            CollectionReportDto row = new CollectionReportDto();
            row.setChurchWise(churchWise);
            row.setMonthly(monthly);
            row.setYear(2026);
            row.setMonth(monthly ? 6 : null);
            row.setRegionName("North");
            row.setChurchName("Main Church");
            row.setGrandTotal(new BigDecimal("140.00"));
            return List.of(row);
        }

        @Override
        public List<SubmissionStatusReportDto> getSubmissionStatusReport(ReportSearchCriteria criteria) {
            lastCriteria = criteria;
            SubmissionStatusReportDto submitted = new SubmissionStatusReportDto();
            submitted.setReceiptId(4L);
            submitted.setRegionName("North");
            submitted.setChurchCode("CH004");
            submitted.setChurchName("Submitted Church");
            submitted.setWeekStartDate(criteria.getWeekStartDate());
            submitted.setStatus("SUBMITTED");
            submitted.setReceiptNo("R-4");
            submitted.setLateSubmission(true);
            submitted.setGrandTotal(new BigDecimal("140.00"));

            SubmissionStatusReportDto missing = new SubmissionStatusReportDto();
            missing.setRegionName("North");
            missing.setChurchCode("CH005");
            missing.setChurchName("Missing Church");
            missing.setWeekStartDate(criteria.getWeekStartDate());
            missing.setStatus("MISSING");
            missing.setLateSubmission(false);

            return List.of(submitted, missing);
        }

        @Override
        public List<UserActivityReportDto> getUserActivityReport(ReportSearchCriteria criteria) {
            lastCriteria = criteria;
            UserActivityReportDto row = new UserActivityReportDto();
            row.setId(50L);
            row.setUsername("admin");
            row.setFullName("System Administrator");
            row.setAction("LOGIN");
            row.setModule("Authentication");
            row.setEntityName("User");
            row.setEntityId(1L);
            row.setDetails("Successful login");
            row.setActivityAt(java.time.LocalDateTime.of(2026, 6, 8, 9, 30));
            return List.of(row);
        }

        @Override
        public ReportSummaryTotals getSummaryTotals(ReportSearchCriteria criteria) {
            summaryIncludedCancelledReceipts = criteria.getReportType() == ReportType.CANCELLED_RECEIPT;
            ReportSummaryTotals totals = new ReportSummaryTotals();
            totals.setOffertoryTotal(new BigDecimal("100.00"));
            totals.setTithesTotal(new BigDecimal("25.00"));
            totals.setOtherDonationsTotal(new BigDecimal("15.00"));
            totals.setGrandTotal(new BigDecimal("140.00"));
            return totals;
        }
    }

    private static class CapturingPrinterService implements PrinterService {
        @Override
        public PrintResult printPdf(String pdfFilePath) {
            return new PrintResult(true, "Printed", "Test Printer", null);
        }
    }
}
