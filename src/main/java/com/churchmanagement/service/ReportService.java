package com.churchmanagement.service;

import com.churchmanagement.dto.PrintResult;
import com.churchmanagement.dto.report.*;
import com.churchmanagement.repository.ReportRepository;
import com.churchmanagement.reports.export.ReportExcelExporter;
import com.churchmanagement.reports.export.ReportPdfExporter;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.util.WeekUtil;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;

public class ReportService {
    private static final int EXPORT_LIMIT = 100_000;

    private final ReportRepository reportRepository;
    private final ActivityLogService activityLogService;
    private final ReportPdfExporter pdfExporter;
    private final ReportExcelExporter excelExporter;
    private final PrinterService printerService;
    private final Clock clock;

    public ReportService() {
        this(new ReportRepository(), new ActivityLogService(), new ReportPdfExporter(),
                new ReportExcelExporter(), new MockPrinterService(), Clock.systemDefaultZone());
    }

    public ReportService(ReportRepository reportRepository, ActivityLogService activityLogService,
                         ReportPdfExporter pdfExporter, ReportExcelExporter excelExporter,
                         PrinterService printerService, Clock clock) {
        this.reportRepository = reportRepository;
        this.activityLogService = activityLogService;
        this.pdfExporter = pdfExporter;
        this.excelExporter = excelExporter;
        this.printerService = printerService;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public ReportResult<? extends ReportTableRow> loadReport(ReportSearchCriteria criteria) {
        AuthenticatedUser user = requireCurrentUser();
        requirePermission(user, "report.view", "You do not have permission to view reports.");
        ReportSearchCriteria safeCriteria = normalizeAndValidate(criteria);
        List<? extends ReportTableRow> rows = collectionColumnFilteredRows(safeCriteria, rowsFor(safeCriteria));
        ReportResult<ReportTableRow> result = new ReportResult<>();
        result.setReportType(safeCriteria.getReportType());
        result.setRows((List<ReportTableRow>) rows);
        result.setTotals(reportRepository.getSummaryTotals(safeCriteria));
        result.setTotalRows(rows.size());
        activityLogService.logReportViewed(user.getUserId(), safeCriteria.getReportType().name(), rows.size());
        return result;
    }

    public Path exportPdf(ReportSearchCriteria criteria) {
        AuthenticatedUser user = requireCurrentUser();
        requirePermission(user, "report.export", "Export failed.");
        ReportSearchCriteria exportCriteria = exportCriteria(criteria);
        ReportResult<? extends ReportTableRow> result = loadReportWithUser(exportCriteria, user);
        Path path = pdfExporter.export(result.getReportType(), exportCriteria, result.getRows(), result.getTotals());
        activityLogService.logReportExportedPdf(user.getUserId(), exportCriteria.getReportType().name(), path.toString());
        return path;
    }

    public Path exportExcel(ReportSearchCriteria criteria) {
        AuthenticatedUser user = requireCurrentUser();
        requirePermission(user, "report.export", "Export failed.");
        ReportSearchCriteria exportCriteria = exportCriteria(criteria);
        ReportResult<? extends ReportTableRow> result = loadReportWithUser(exportCriteria, user);
        Path path = excelExporter.export(result.getReportType(), result.getRows(), result.getTotals());
        activityLogService.logReportExportedExcel(user.getUserId(), exportCriteria.getReportType().name(), path.toString());
        return path;
    }

    public PrintResult printReport(ReportSearchCriteria criteria) {
        AuthenticatedUser user = requireCurrentUser();
        requirePermission(user, "report.print", "Print failed.");
        ReportSearchCriteria printCriteria = exportCriteria(criteria);
        ReportResult<? extends ReportTableRow> report = loadReportWithUser(printCriteria, user);
        Path pdfPath = pdfExporter.export(report.getReportType(), printCriteria, report.getRows(), report.getTotals());
        PrintResult result = printerService.printPdf(pdfPath.toString());
        if (!result.isSuccess()) {
            throw new ReportException("Print failed.");
        }
        activityLogService.logReportPrinted(user.getUserId(), printCriteria.getReportType().name(), pdfPath.toString());
        return result;
    }

    public void logFilterChanged(ReportSearchCriteria criteria) {
        AuthContext.getCurrentUser().ifPresent(user -> activityLogService.logReportFilterChanged(
                user.getUserId(),
                criteria == null || criteria.getReportType() == null ? "" : criteria.getReportType().name(),
                filterSummary(criteria)));
    }

    public ReportSearchCriteria defaultCriteria(ReportType reportType) {
        ReportSearchCriteria criteria = new ReportSearchCriteria();
        ReportType selectedType = reportType == null ? ReportType.WEEKLY_CHURCH_COLLECTION : reportType;
        criteria.setReportType(selectedType);
        LocalDate today = LocalDate.now(clock);
        criteria.setDateFrom(isAnnualCollectionReport(selectedType)
                ? LocalDate.of(today.getYear(), 1, 1)
                : today.withDayOfMonth(1));
        criteria.setDateTo(today);
        criteria.setWeekStartDate(WeekUtil.getCurrentWeekMonday(today));
        return criteria;
    }

    private boolean isAnnualCollectionReport(ReportType reportType) {
        return reportType == ReportType.CHURCH_ANNUAL_COLLECTION
                || reportType == ReportType.REGION_ANNUAL_COLLECTION;
    }

    public DateRange quickRange(String quickFilter) {
        LocalDate today = LocalDate.now(clock);
        return switch (quickFilter) {
            case "This Week" -> {
                LocalDate monday = WeekUtil.getCurrentWeekMonday(today);
                yield new DateRange(monday, monday.plusDays(6));
            }
            case "Previous Week" -> {
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
                yield new DateRange(monday, monday.plusDays(6));
            }
            case "Quarter" -> {
                int quarterStart = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                yield new DateRange(LocalDate.of(today.getYear(), quarterStart, 1), today);
            }
            case "Year" -> new DateRange(LocalDate.of(today.getYear(), 1, 1), today);
            default -> new DateRange(today.withDayOfMonth(1), today);
        };
    }

    private ReportResult<? extends ReportTableRow> loadReportWithUser(ReportSearchCriteria criteria, AuthenticatedUser user) {
        requirePermission(user, "report.view", "You do not have permission to view reports.");
        ReportSearchCriteria safeCriteria = normalizeAndValidate(criteria);
        List<? extends ReportTableRow> rows = collectionColumnFilteredRows(safeCriteria, rowsFor(safeCriteria));
        ReportResult<ReportTableRow> result = new ReportResult<>();
        result.setReportType(safeCriteria.getReportType());
        result.setRows((List<ReportTableRow>) rows);
        result.setTotals(reportRepository.getSummaryTotals(safeCriteria));
        result.setTotalRows(rows.size());
        return result;
    }

    private List<? extends ReportTableRow> rowsFor(ReportSearchCriteria criteria) {
        return switch (criteria.getReportType()) {
            case WEEKLY_CHURCH_COLLECTION -> reportRepository.getWeeklyChurchCollectionReport(criteria);
            case WEEKLY_REGION_SUMMARY -> reportRepository.getWeeklyRegionSummaryReport(criteria);
            case SUBMISSION_STATUS -> reportRepository.getSubmissionStatusReport(criteria);
            case LATE_SUBMISSION -> reportRepository.getLateSubmissionReport(criteria);
            case CHURCH_ANNUAL_COLLECTION -> reportRepository.getCollectionReport(criteria, true, false);
            case REGION_ANNUAL_COLLECTION -> reportRepository.getCollectionReport(criteria, false, false);
            case CHURCH_MONTHLY_COLLECTION -> reportRepository.getCollectionReport(criteria, true, true);
            case REGION_MONTHLY_COLLECTION -> reportRepository.getCollectionReport(criteria, false, true);
            case CHURCH_PROGRESS -> reportRepository.getChurchProgressReport(criteria);
            case REGION_PROGRESS -> reportRepository.getRegionProgressReport(criteria);
            case CANCELLED_RECEIPT -> reportRepository.getCancelledReceiptReport(criteria);
            case RECEIPT_PRINT_STATUS -> reportRepository.getReceiptPrintStatusReport(criteria);
            case SMS_DELIVERY -> reportRepository.getSmsDeliveryReport(criteria);
            case USER_ACTIVITY -> reportRepository.getUserActivityReport(criteria);
            case BACKUP_RESTORE_HISTORY -> reportRepository.getBackupRestoreHistoryReport(criteria);
        };
    }

    private List<ReportTableRow> collectionColumnFilteredRows(ReportSearchCriteria criteria,
                                                              List<? extends ReportTableRow> rows) {
        if (!supportsCollectionColumnSelection(criteria.getReportType())) {
            return (List<ReportTableRow>) rows;
        }
        return rows.stream()
                .map(row -> (ReportTableRow) new CollectionColumnFilteredReportRow(row,
                        criteria.isOffertoryColumnSelected(),
                        criteria.isTithesColumnSelected(),
                        criteria.isOtherDonationsColumnSelected(),
                        shouldShowGrandTotalColumn(criteria)))
                .toList();
    }

    private boolean shouldShowGrandTotalColumn(ReportSearchCriteria criteria) {
        return criteria.isGrandTotalColumnSelected()
                || noCollectionTypeColumnSelected(criteria);
    }

    private boolean noCollectionTypeColumnSelected(ReportSearchCriteria criteria) {
        return !criteria.isOffertoryColumnSelected()
                && !criteria.isTithesColumnSelected()
                && !criteria.isOtherDonationsColumnSelected();
    }

    private boolean supportsCollectionColumnSelection(ReportType reportType) {
        return reportType == ReportType.WEEKLY_CHURCH_COLLECTION
                || reportType == ReportType.WEEKLY_REGION_SUMMARY
                || reportType == ReportType.SUBMISSION_STATUS
                || reportType == ReportType.LATE_SUBMISSION
                || reportType == ReportType.CHURCH_ANNUAL_COLLECTION
                || reportType == ReportType.REGION_ANNUAL_COLLECTION
                || reportType == ReportType.CHURCH_MONTHLY_COLLECTION
                || reportType == ReportType.REGION_MONTHLY_COLLECTION
                || reportType == ReportType.CHURCH_PROGRESS
                || reportType == ReportType.REGION_PROGRESS;
    }

    private ReportSearchCriteria normalizeAndValidate(ReportSearchCriteria criteria) {
        if (criteria == null || criteria.getReportType() == null) {
            throw new ReportException("Report Type is required.");
        }
        ReportSearchCriteria safe = copy(criteria);
        if (usesWeekStart(safe.getReportType())) {
            if (safe.getWeekStartDate() == null) {
                safe.setWeekStartDate(defaultCriteria(safe.getReportType()).getWeekStartDate());
            }
            if (!WeekUtil.isWeekStartMonday(safe.getWeekStartDate())) {
                throw new ReportException("Week Start Date must be Monday.");
            }
            safe.setDateFrom(null);
            safe.setDateTo(null);
        } else {
            if (safe.getDateFrom() == null || safe.getDateTo() == null) {
                DateRange defaults = quickRange("This Month");
                safe.setDateFrom(safe.getDateFrom() == null ? defaults.dateFrom() : safe.getDateFrom());
                safe.setDateTo(safe.getDateTo() == null ? defaults.dateTo() : safe.getDateTo());
            }
            if (safe.getDateFrom().isAfter(safe.getDateTo())) {
                throw new ReportException("Invalid date range.");
            }
        }
        return safe;
    }

    private boolean usesWeekStart(ReportType reportType) {
        return reportType == ReportType.WEEKLY_CHURCH_COLLECTION
                || reportType == ReportType.WEEKLY_REGION_SUMMARY
                || reportType == ReportType.SUBMISSION_STATUS
                || reportType == ReportType.LATE_SUBMISSION;
    }

    private ReportSearchCriteria exportCriteria(ReportSearchCriteria criteria) {
        ReportSearchCriteria copy = copy(criteria);
        copy.setOffset(0);
        copy.setLimit(EXPORT_LIMIT);
        return copy;
    }

    private ReportSearchCriteria copy(ReportSearchCriteria source) {
        ReportSearchCriteria copy = new ReportSearchCriteria();
        if (source == null) {
            return copy;
        }
        copy.setDateFrom(source.getDateFrom());
        copy.setDateTo(source.getDateTo());
        copy.setWeekStartDate(source.getWeekStartDate());
        copy.setRegionId(source.getRegionId());
        copy.setChurchId(source.getChurchId());
        copy.setStatus(source.getStatus());
        copy.setReceiptNo(source.getReceiptNo());
        copy.setUserId(source.getUserId());
        copy.setReportType(source.getReportType());
        copy.setOffset(source.getOffset());
        copy.setLimit(source.getLimit());
        copy.setOffertoryColumnSelected(source.isOffertoryColumnSelected());
        copy.setTithesColumnSelected(source.isTithesColumnSelected());
        copy.setOtherDonationsColumnSelected(source.isOtherDonationsColumnSelected());
        copy.setGrandTotalColumnSelected(source.isGrandTotalColumnSelected());
        return copy;
    }

    private void requirePermission(AuthenticatedUser user, String permission, String message) {
        if (!new PermissionGuard(user).can(permission)) {
            throw new ReportException(message);
        }
    }

    private AuthenticatedUser requireCurrentUser() {
        return AuthContext.getCurrentUser()
                .orElseThrow(() -> new ReportException("You do not have permission to view reports."));
    }

    private String filterSummary(ReportSearchCriteria criteria) {
        return criteria == null ? "" : "date_from=" + criteria.getDateFrom()
                + ", date_to=" + criteria.getDateTo()
                + ", week_start_date=" + criteria.getWeekStartDate()
                + ", region_id=" + criteria.getRegionId()
                + ", church_id=" + criteria.getChurchId()
                + ", status=" + criteria.getStatus()
                + ", receipt_no=" + criteria.getReceiptNo()
                + ", user_id=" + criteria.getUserId()
                + ", collection_columns="
                + (criteria.isOffertoryColumnSelected() ? "Offertory;" : "")
                + (criteria.isTithesColumnSelected() ? "tithes;" : "")
                + (criteria.isOtherDonationsColumnSelected() ? "other_donations;" : "")
                + (criteria.isGrandTotalColumnSelected() ? "grand_total;" : "");
    }

    private record CollectionColumnFilteredReportRow(ReportTableRow delegate, boolean showOffertory,
                                                     boolean showTithes, boolean showOtherDonations,
                                                     boolean showGrandTotal)
            implements ReportTableRow {
        @Override
        public LinkedHashMap<String, Object> columns() {
            LinkedHashMap<String, Object> visible = new LinkedHashMap<>();
            delegate.columns().forEach((header, value) -> {
                if (isHiddenCollectionColumn(header)) {
                    return;
                }
                visible.put(header, value);
            });
            return visible;
        }

        @Override
        public Long detailId() {
            return delegate.detailId();
        }

        private boolean isHiddenCollectionColumn(String header) {
            return ("Offerings".equals(header) && !showOffertory)
                    || ("Tithes".equals(header) && !showTithes)
                    || ("Other Donations".equals(header) && !showOtherDonations)
                    || (isTotalColumn(header) && !showGrandTotal);
        }

        private boolean isTotalColumn(String header) {
            return "Grand Total".equals(header) || "Total Collections".equals(header);
        }
    }

    public record DateRange(LocalDate dateFrom, LocalDate dateTo) {
    }

    public static class ReportException extends RuntimeException {
        public ReportException(String message) {
            super(message);
        }
    }
}
