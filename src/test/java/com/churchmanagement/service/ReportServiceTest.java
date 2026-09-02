package com.churchmanagement.service;

import com.churchmanagement.dto.PrintResult;
import com.churchmanagement.dto.report.*;
import com.churchmanagement.repository.ReportRepository;
import com.churchmanagement.reports.export.ReportExcelExporter;
import com.churchmanagement.reports.export.ReportPdfExporter;
import com.churchmanagement.repository.SystemSettingRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
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
    private FakeSystemConfigurationCache configurationCache;
    private ReportService service;

    @BeforeEach
    void setUp() {
        repository = new FakeReportRepository();
        configurationCache = new FakeSystemConfigurationCache();
        service = new ReportService(repository, new ActivityLogService(null), new ReportPdfExporter(fixedClock()),
                new ReportExcelExporter(), new CapturingPrinterService(), fixedClock(), configurationCache);
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

        assertEquals(3, result.getRows().size());
        assertTrue(result.getRows().stream().anyMatch(row -> "MISSING".equals(row.columns().get("Status"))));
    }

    @Test
    void submissionStatusLateFilterShowsOnlyLateSubmissions() {
        ReportSearchCriteria criteria = criteria(ReportType.SUBMISSION_STATUS);
        criteria.setStatus("LATE");

        ReportResult<? extends ReportTableRow> result = service.loadReport(criteria);

        assertEquals(1, result.getRows().size());
        assertEquals("SUBMITTED", result.getRows().getFirst().columns().get("Status"));
        assertEquals("Yes", result.getRows().getFirst().columns().get("Late Submission"));
    }

    @Test
    void submissionStatusOnTimeFilterShowsOnlyOnTimeSubmissions() {
        ReportSearchCriteria criteria = criteria(ReportType.SUBMISSION_STATUS);
        criteria.setStatus("ON_TIME");

        ReportResult<? extends ReportTableRow> result = service.loadReport(criteria);

        assertEquals(1, result.getRows().size());
        assertEquals("SUBMITTED", result.getRows().getFirst().columns().get("Status"));
        assertEquals("No", result.getRows().getFirst().columns().get("Late Submission"));
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
    void identifierDaySettingIsActuallyReadNotHardcodedToMonday() {
        FakeSystemConfigurationCache wednesdayCache = new FakeSystemConfigurationCache();
        wednesdayCache.put("receipt.week.identifier.day", "WEDNESDAY");
        ReportService wednesdayService = new ReportService(repository, new ActivityLogService(null),
                new ReportPdfExporter(fixedClock()), new ReportExcelExporter(), new CapturingPrinterService(),
                fixedClock(), wednesdayCache);

        assertEquals(LocalDate.of(2026, 6, 3), wednesdayService.defaultWeekIdentifier());
        assertEquals(LocalDate.of(2026, 5, 28),
                wednesdayService.defaultCriteria(ReportType.WEEKLY_CHURCH_COLLECTION).getWeekStartDate());
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
    void exportWeeklyRegionPdfCreatesFile() {
        Path pdf = service.exportPdf(criteria(ReportType.WEEKLY_REGION_SUMMARY));

        assertTrue(Files.exists(pdf));
        assertTrue(pdf.toString().endsWith(".pdf"));
    }

    @Test
    void exportSubmissionStatusPdfCreatesFileWithChart() {
        Path pdf = service.exportPdf(criteria(ReportType.SUBMISSION_STATUS));

        assertTrue(Files.exists(pdf));
        assertTrue(pdf.toString().endsWith(".pdf"));
    }

    @Test
    void exportChurchAnnualPdfCreatesFileWithCharts() {
        Path pdf = service.exportPdf(criteria(ReportType.CHURCH_ANNUAL_COLLECTION));

        assertTrue(Files.exists(pdf));
        assertTrue(pdf.toString().endsWith(".pdf"));
    }

    @Test
    void exportRegionAnnualPdfCreatesFileWithCharts() {
        Path pdf = service.exportPdf(criteria(ReportType.REGION_ANNUAL_COLLECTION));

        assertTrue(Files.exists(pdf));
        assertTrue(pdf.toString().endsWith(".pdf"));
    }

    @Test
    void exportChurchMonthlyPdfCreatesFileWithCharts() {
        Path pdf = service.exportPdf(criteria(ReportType.CHURCH_MONTHLY_COLLECTION));

        assertTrue(Files.exists(pdf));
        assertTrue(pdf.toString().endsWith(".pdf"));
    }

    @Test
    void exportRegionMonthlyPdfCreatesFileWithCharts() {
        Path pdf = service.exportPdf(criteria(ReportType.REGION_MONTHLY_COLLECTION));

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
            assertEquals(2, workbook.getSheet("Charts").getDrawingPatriarch().getCharts().size());
            XSSFChart barChart = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(0);
            XSSFChart pieChart = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(1);
            assertTrue(barChart.getCTChart().isSetLegend());
            assertTrue(barChart.getCTChart().getPlotArea().getValAxArray(0).isSetMajorGridlines());
            assertEquals(20, barChart.getCTChart().getPlotArea().getBarChartArray(0).getSerArray(0).sizeOfDPtArray());
            assertTrue(barChart.getCTChart().getPlotArea().getBarChartArray(0).getSerArray(0)
                    .getDPtArray(0).getSpPr().isSetSolidFill());
            assertFalse(barChart.getCTChart().getPlotArea().getBarChartArray(0).getSerArray(0)
                    .getDPtArray(0).getSpPr().isSetGradFill());
            assertTrue(pieChart.getCTChart().isSetLegend());
            assertEquals(3, pieChart.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).sizeOfDPtArray());
            assertTrue(pieChart.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0)
                    .getDPtArray(0).getSpPr().isSetSolidFill());
            assertTrue(pieChart.getCTChart().getPlotArea().getPieChartArray(0).isSetDLbls());
            assertEquals("Top 20 Churches", workbook.getSheet("Charts").getRow(0).getCell(0).getStringCellValue());
            assertEquals("Church 25", workbook.getSheet("Charts").getRow(2).getCell(0).getStringCellValue());
            assertEquals("Church 06", workbook.getSheet("Charts").getRow(21).getCell(0).getStringCellValue());
            assertEquals("Collection Type-wise (All Churches)",
                    workbook.getSheet("Charts").getRow(26).getCell(0).getStringCellValue());
            assertEquals("Pie chart uses all churches in this report,\nnot only the Top 20.",
                    workbook.getSheet("Charts").getRow(27).getCell(0).getStringCellValue());
            assertTrue(workbook.getSheet("Charts").getRow(27).getCell(0).getCellStyle().getWrapText());
            assertEquals("Offerings", workbook.getSheet("Charts").getRow(29).getCell(0).getStringCellValue());
            assertEquals(325.00, workbook.getSheet("Charts").getRow(29).getCell(1).getNumericCellValue());
            assertNull(workbook.getSheet("Charts").getRow(22));
        }
    }

    @Test
    void excelExportCreatesRegionChartSheetWithCollectionTypePie() throws Exception {
        Path excel = service.exportExcel(criteria(ReportType.WEEKLY_REGION_SUMMARY));

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(excel))) {
            assertNotNull(workbook.getSheet("Charts"));
            assertEquals(2, workbook.getSheet("Charts").getDrawingPatriarch().getCharts().size());
            XSSFChart barChart = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(0);
            XSSFChart pieChart = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(1);
            assertEquals(20, barChart.getCTChart().getPlotArea().getBarChartArray(0).getSerArray(0).sizeOfDPtArray());
            assertEquals(3, pieChart.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).sizeOfDPtArray());
            assertTrue(pieChart.getCTChart().getPlotArea().getPieChartArray(0).isSetDLbls());
            assertEquals("Top 20 Regions", workbook.getSheet("Charts").getRow(0).getCell(0).getStringCellValue());
            assertEquals("Region 25", workbook.getSheet("Charts").getRow(2).getCell(0).getStringCellValue());
            assertEquals("Collection Type-wise (All Regions)",
                    workbook.getSheet("Charts").getRow(26).getCell(0).getStringCellValue());
            assertEquals("Pie chart uses all regions in this report,\nnot only the Top 20.",
                    workbook.getSheet("Charts").getRow(27).getCell(0).getStringCellValue());
            assertEquals("Offerings", workbook.getSheet("Charts").getRow(29).getCell(0).getStringCellValue());
            assertEquals(325.00, workbook.getSheet("Charts").getRow(29).getCell(1).getNumericCellValue());
        }
    }

    @Test
    void excelExportCreatesSubmissionStatusPieChart() throws Exception {
        Path excel = service.exportExcel(criteria(ReportType.SUBMISSION_STATUS));

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(excel))) {
            assertNotNull(workbook.getSheet("Charts"));
            assertEquals(1, workbook.getSheet("Charts").getDrawingPatriarch().getCharts().size());
            XSSFChart pieChart = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().getFirst();
            assertEquals(2, pieChart.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).sizeOfDPtArray());
            assertTrue(pieChart.getCTChart().getPlotArea().getPieChartArray(0).isSetDLbls());
            assertEquals("Submission Status Breakdown",
                    workbook.getSheet("Charts").getRow(0).getCell(0).getStringCellValue());
            assertEquals("Pie chart shows churches grouped by submission status.",
                    workbook.getSheet("Charts").getRow(1).getCell(0).getStringCellValue());
            assertEquals("Status", workbook.getSheet("Charts").getRow(2).getCell(0).getStringCellValue());
            assertEquals("Church Count", workbook.getSheet("Charts").getRow(2).getCell(1).getStringCellValue());
        }
    }

    @Test
    void excelExportCreatesAnnualChurchGroupedBarAndYearPies() throws Exception {
        Path excel = service.exportExcel(criteria(ReportType.CHURCH_ANNUAL_COLLECTION));

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(excel))) {
            assertNotNull(workbook.getSheet("Charts"));
            assertEquals(4, workbook.getSheet("Charts").getDrawingPatriarch().getCharts().size());
            XSSFChart barChart = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(0);
            XSSFChart typeBarChart = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(1);
            XSSFChart firstPie = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(2);
            assertEquals(2, barChart.getCTChart().getPlotArea().getBarChartArray(0).sizeOfSerArray());
            assertTrue(barChart.getCTChart().getPlotArea().getBarChartArray(0).getSerArray(0).isSetSpPr());
            assertEquals(3, typeBarChart.getCTChart().getPlotArea().getBarChartArray(0).sizeOfSerArray());
            assertEquals(3, firstPie.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).sizeOfDPtArray());
            assertEquals("Top 20 Churches", workbook.getSheet("Charts").getRow(0).getCell(0).getStringCellValue());
            assertEquals(2025.0, workbook.getSheet("Charts").getRow(1).getCell(1).getNumericCellValue());
            assertEquals(2026.0, workbook.getSheet("Charts").getRow(1).getCell(2).getNumericCellValue());
            assertEquals("Church 25", workbook.getSheet("Charts").getRow(2).getCell(0).getStringCellValue());
            assertEquals("Collection Types by Year (All Churches)",
                    workbook.getSheet("Charts").getRow(28).getCell(0).getStringCellValue());
            assertEquals("Collection Type-wise (2026)",
                    workbook.getSheet("Charts").getRow(52).getCell(0).getStringCellValue());
            assertEquals("Collection Type-wise (2025)",
                    workbook.getSheet("Charts").getRow(72).getCell(0).getStringCellValue());
        }
    }

    @Test
    void excelExportCreatesAnnualRegionGroupedBarAndYearPies() throws Exception {
        Path excel = service.exportExcel(criteria(ReportType.REGION_ANNUAL_COLLECTION));

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(excel))) {
            assertNotNull(workbook.getSheet("Charts"));
            assertEquals(4, workbook.getSheet("Charts").getDrawingPatriarch().getCharts().size());
            XSSFChart barChart = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(0);
            XSSFChart typeBarChart = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(1);
            XSSFChart firstPie = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(2);
            assertEquals(2, barChart.getCTChart().getPlotArea().getBarChartArray(0).sizeOfSerArray());
            assertTrue(barChart.getCTChart().getPlotArea().getBarChartArray(0).getSerArray(0).isSetSpPr());
            assertEquals(3, typeBarChart.getCTChart().getPlotArea().getBarChartArray(0).sizeOfSerArray());
            assertEquals(3, firstPie.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).sizeOfDPtArray());
            assertEquals("Top 20 Regions", workbook.getSheet("Charts").getRow(0).getCell(0).getStringCellValue());
            assertEquals(2025.0, workbook.getSheet("Charts").getRow(1).getCell(1).getNumericCellValue());
            assertEquals(2026.0, workbook.getSheet("Charts").getRow(1).getCell(2).getNumericCellValue());
            assertEquals("Region 25", workbook.getSheet("Charts").getRow(2).getCell(0).getStringCellValue());
            assertEquals("Collection Types by Year (All Regions)",
                    workbook.getSheet("Charts").getRow(28).getCell(0).getStringCellValue());
            assertEquals("Collection Type-wise (2026)",
                    workbook.getSheet("Charts").getRow(52).getCell(0).getStringCellValue());
            assertEquals("Collection Type-wise (2025)",
                    workbook.getSheet("Charts").getRow(72).getCell(0).getStringCellValue());
        }
    }

    @Test
    void excelExportCreatesMonthlyChurchGroupedBarAndMonthPies() throws Exception {
        Path excel = service.exportExcel(criteria(ReportType.CHURCH_MONTHLY_COLLECTION));

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(excel))) {
            assertNotNull(workbook.getSheet("Charts"));
            assertEquals(5, workbook.getSheet("Charts").getDrawingPatriarch().getCharts().size());
            XSSFChart barChart = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(0);
            XSSFChart typeBarChart = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(1);
            XSSFChart firstPie = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(2);
            assertEquals(3, barChart.getCTChart().getPlotArea().getBarChartArray(0).sizeOfSerArray());
            assertTrue(barChart.getCTChart().getPlotArea().getBarChartArray(0).getSerArray(0).isSetSpPr());
            assertEquals(3, typeBarChart.getCTChart().getPlotArea().getBarChartArray(0).sizeOfSerArray());
            assertEquals(3, firstPie.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).sizeOfDPtArray());
            assertEquals("Top 20 Churches", workbook.getSheet("Charts").getRow(0).getCell(0).getStringCellValue());
            assertEquals("June 2025", workbook.getSheet("Charts").getRow(1).getCell(1).getStringCellValue());
            assertEquals("January 2026", workbook.getSheet("Charts").getRow(1).getCell(2).getStringCellValue());
            assertEquals("February 2026", workbook.getSheet("Charts").getRow(1).getCell(3).getStringCellValue());
            assertEquals("Church 25", workbook.getSheet("Charts").getRow(2).getCell(0).getStringCellValue());
            assertEquals("Collection Types by Month (All Churches)",
                    workbook.getSheet("Charts").getRow(28).getCell(0).getStringCellValue());
            assertEquals("Collection Type-wise (February 2026)",
                    workbook.getSheet("Charts").getRow(52).getCell(0).getStringCellValue());
            assertEquals("Collection Type-wise (January 2026)",
                    workbook.getSheet("Charts").getRow(72).getCell(0).getStringCellValue());
            assertEquals("Collection Type-wise (June 2025)",
                    workbook.getSheet("Charts").getRow(92).getCell(0).getStringCellValue());
        }
    }

    @Test
    void excelExportCreatesMonthlyRegionGroupedBarAndMonthPies() throws Exception {
        Path excel = service.exportExcel(criteria(ReportType.REGION_MONTHLY_COLLECTION));

        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(excel))) {
            assertNotNull(workbook.getSheet("Charts"));
            assertEquals(5, workbook.getSheet("Charts").getDrawingPatriarch().getCharts().size());
            XSSFChart barChart = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(0);
            XSSFChart typeBarChart = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(1);
            XSSFChart firstPie = workbook.getSheet("Charts").getDrawingPatriarch().getCharts().get(2);
            assertEquals(3, barChart.getCTChart().getPlotArea().getBarChartArray(0).sizeOfSerArray());
            assertTrue(barChart.getCTChart().getPlotArea().getBarChartArray(0).getSerArray(0).isSetSpPr());
            assertEquals(3, typeBarChart.getCTChart().getPlotArea().getBarChartArray(0).sizeOfSerArray());
            assertEquals(3, firstPie.getCTChart().getPlotArea().getPieChartArray(0).getSerArray(0).sizeOfDPtArray());
            assertEquals("Top 20 Regions", workbook.getSheet("Charts").getRow(0).getCell(0).getStringCellValue());
            assertEquals("June 2025", workbook.getSheet("Charts").getRow(1).getCell(1).getStringCellValue());
            assertEquals("January 2026", workbook.getSheet("Charts").getRow(1).getCell(2).getStringCellValue());
            assertEquals("February 2026", workbook.getSheet("Charts").getRow(1).getCell(3).getStringCellValue());
            assertEquals("Region 25", workbook.getSheet("Charts").getRow(2).getCell(0).getStringCellValue());
            assertEquals("Collection Types by Month (All Regions)",
                    workbook.getSheet("Charts").getRow(28).getCell(0).getStringCellValue());
            assertEquals("Collection Type-wise (February 2026)",
                    workbook.getSheet("Charts").getRow(52).getCell(0).getStringCellValue());
            assertEquals("Collection Type-wise (January 2026)",
                    workbook.getSheet("Charts").getRow(72).getCell(0).getStringCellValue());
            assertEquals("Collection Type-wise (June 2025)",
                    workbook.getSheet("Charts").getRow(92).getCell(0).getStringCellValue());
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
            return java.util.stream.IntStream.rangeClosed(1, 25)
                    .mapToObj(index -> {
                        WeeklyChurchCollectionReportDto row = new WeeklyChurchCollectionReportDto();
                        row.setReceiptId((long) index);
                        row.setRegionName("North");
                        row.setChurchCode("CH%03d".formatted(index));
                        row.setChurchName("Church %02d".formatted(index));
                        row.setWeekStartDate(criteria.getWeekStartDate());
                        row.setReceiptNo("R-" + index);
                        row.setOffertoryTotal(new BigDecimal(index + ".00"));
                        row.setTithesTotal(new BigDecimal("2.00"));
                        row.setOtherDonationsTotal(new BigDecimal("3.00"));
                        row.setGrandTotal(new BigDecimal(index + 5 + ".00"));
                        return row;
                    })
                    .toList();
        }

        @Override
        public List<WeeklyRegionSummaryReportDto> getWeeklyRegionSummaryReport(ReportSearchCriteria criteria) {
            lastCriteria = criteria;
            return java.util.stream.IntStream.rangeClosed(1, 25)
                    .mapToObj(index -> {
                        WeeklyRegionSummaryReportDto row = new WeeklyRegionSummaryReportDto();
                        row.setRegionId((long) index);
                        row.setRegionCode("RG%03d".formatted(index));
                        row.setRegionName("Region %02d".formatted(index));
                        row.setWeekStartDate(criteria.getWeekStartDate());
                        row.setTotalChurches(10);
                        row.setSubmittedChurches(8);
                        row.setMissingChurches(2);
                        row.setLateSubmissions(1);
                        row.setOffertoryTotal(new BigDecimal(index + ".00"));
                        row.setTithesTotal(new BigDecimal("2.00"));
                        row.setOtherDonationsTotal(new BigDecimal("3.00"));
                        row.setGrandTotal(new BigDecimal(index + 5 + ".00"));
                        return row;
                    })
                    .toList();
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
            if (monthly) {
                List<int[]> months = List.of(new int[]{2025, 6}, new int[]{2026, 1}, new int[]{2026, 2});
                return java.util.stream.IntStream.rangeClosed(1, 25)
                        .boxed()
                        .flatMap(index -> months.stream().map(period -> {
                            CollectionReportDto row = new CollectionReportDto();
                            row.setChurchWise(churchWise);
                            row.setMonthly(true);
                            row.setYear(period[0]);
                            row.setMonth(period[1]);
                            row.setRegionName("Region %02d".formatted(index));
                            row.setChurchName("Church %02d".formatted(index));
                            long offset = switch (period[1]) {
                                case 6 -> 0L;
                                case 1 -> 100L;
                                default -> 200L;
                            };
                            BigDecimal base = BigDecimal.valueOf(index + offset);
                            row.setOffertoryTotal(base);
                            row.setTithesTotal(new BigDecimal("2.00"));
                            row.setOtherDonationsTotal(new BigDecimal("3.00"));
                            row.setGrandTotal(base.add(new BigDecimal("5.00")));
                            return row;
                        }))
                        .toList();
            }
            if (!monthly) {
                return java.util.stream.IntStream.rangeClosed(1, 25)
                        .boxed()
                        .flatMap(index -> java.util.stream.Stream.of(2025, 2026).map(year -> {
                            CollectionReportDto row = new CollectionReportDto();
                            row.setChurchWise(churchWise);
                            row.setMonthly(false);
                            row.setYear(year);
                            row.setRegionName("Region %02d".formatted(index));
                            row.setChurchName("Church %02d".formatted(index));
                            BigDecimal base = BigDecimal.valueOf(index + (year == 2026 ? 100L : 0L));
                            row.setOffertoryTotal(base);
                            row.setTithesTotal(new BigDecimal("2.00"));
                            row.setOtherDonationsTotal(new BigDecimal("3.00"));
                            row.setGrandTotal(base.add(new BigDecimal("5.00")));
                            return row;
                        }))
                        .toList();
            }
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

            SubmissionStatusReportDto onTime = new SubmissionStatusReportDto();
            onTime.setReceiptId(6L);
            onTime.setRegionName("North");
            onTime.setChurchCode("CH006");
            onTime.setChurchName("On Time Church");
            onTime.setWeekStartDate(criteria.getWeekStartDate());
            onTime.setStatus("SUBMITTED");
            onTime.setReceiptNo("R-6");
            onTime.setLateSubmission(false);
            onTime.setGrandTotal(new BigDecimal("120.00"));

            SubmissionStatusReportDto missing = new SubmissionStatusReportDto();
            missing.setRegionName("North");
            missing.setChurchCode("CH005");
            missing.setChurchName("Missing Church");
            missing.setWeekStartDate(criteria.getWeekStartDate());
            missing.setStatus("MISSING");
            missing.setLateSubmission(false);

            List<SubmissionStatusReportDto> rows = List.of(submitted, onTime, missing);
            if ("SUBMITTED".equalsIgnoreCase(criteria.getStatus())) {
                return rows.stream().filter(row -> "SUBMITTED".equals(row.columns().get("Status"))).toList();
            }
            if ("MISSING".equalsIgnoreCase(criteria.getStatus())) {
                return rows.stream().filter(row -> "MISSING".equals(row.columns().get("Status"))).toList();
            }
            if ("LATE".equalsIgnoreCase(criteria.getStatus()) || "LATE_SUBMISSION".equalsIgnoreCase(criteria.getStatus())) {
                return rows.stream().filter(SubmissionStatusReportDto::isLateSubmission).toList();
            }
            if ("ON_TIME".equalsIgnoreCase(criteria.getStatus())) {
                return rows.stream()
                        .filter(row -> "SUBMITTED".equals(row.columns().get("Status")))
                        .filter(row -> !row.isLateSubmission())
                        .toList();
            }
            return rows;
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

    private static class FakeSystemConfigurationCache extends SystemConfigurationCache {
        private final java.util.Map<String, String> values = new java.util.HashMap<>();

        private FakeSystemConfigurationCache() {
            super(new SystemSettingRepository((DataSource) null));
        }

        @Override
        public String getString(String key) {
            return values.get(key);
        }

        private void put(String key, String value) {
            values.put(key, value);
        }
    }
}
