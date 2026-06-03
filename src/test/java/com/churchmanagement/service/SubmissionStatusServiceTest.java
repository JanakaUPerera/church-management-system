package com.churchmanagement.service;

import com.churchmanagement.dto.SubmissionDetailsDto;
import com.churchmanagement.dto.SubmissionStatusDto;
import com.churchmanagement.dto.SubmissionSummaryDto;
import com.churchmanagement.dto.SubmissionTotalsDto;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.repository.SubmissionStatusRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionStatusServiceTest {
    private FakeSubmissionStatusRepository repository;
    private FakeActivityLogService activityLogService;
    private SubmissionStatusService service;

    @BeforeEach
    void setUp() {
        repository = new FakeSubmissionStatusRepository();
        activityLogService = new FakeActivityLogService();
        service = new SubmissionStatusService(repository, activityLogService, fixedClock());
        AuthContext.setCurrentUser(new AuthenticatedUser(7L, "admin", "System Administrator", 1L,
                "Admin", List.of("receipt.view")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void submittedPendingAndCancelledStatusesAreCalculated() {
        List<SubmissionStatusDto> rows = service.loadWeeklyStatus(LocalDate.of(2026, 5, 25), null, "ALL");

        assertEquals("SUBMITTED", row(rows, 1L).getStatus());
        assertEquals("PENDING", row(rows, 2L).getStatus());
        assertEquals("CANCELLED", row(rows, 3L).getStatus());
        assertEquals(ActivityLogService.SUBMISSION_STATUS_VIEWED, activityLogService.lastAction);
    }

    @Test
    void totalsUseActiveReceiptsOnlyAndExcludeCancelled() {
        SubmissionTotalsDto totals = service.loadSubmissionTotals(LocalDate.of(2026, 5, 25), null);
        SubmissionSummaryDto summary = service.loadWeeklySummary(LocalDate.of(2026, 5, 25), null);

        assertEquals(new BigDecimal("1000.00"), totals.getTotalOffertory());
        assertEquals(new BigDecimal("500.00"), totals.getTotalTithes());
        assertEquals(new BigDecimal("200.00"), totals.getTotalOtherDonations());
        assertEquals(new BigDecimal("1700.00"), totals.getGrandTotal());
        assertEquals(new BigDecimal("1700.00"), summary.getTotalCollectionAmount());
        assertEquals(1, summary.getCancelledReceipts());
    }

    @Test
    void lateSubmissionDisplayComesFromActiveSubmission() {
        List<SubmissionStatusDto> rows = service.loadWeeklyStatus(LocalDate.of(2026, 5, 25), null, "ALL");

        assertTrue(row(rows, 1L).isLateSubmission());
        assertFalse(row(rows, 2L).isLateSubmission());
        assertEquals(1, service.loadWeeklySummary(LocalDate.of(2026, 5, 25), null).getLateSubmissions());
    }

    @Test
    void regionAndStatusFilteringAreApplied() {
        List<SubmissionStatusDto> northSubmitted = service.loadWeeklyStatus(
                LocalDate.of(2026, 5, 25), 1L, "SUBMITTED");
        List<SubmissionStatusDto> southCancelled = service.loadWeeklyStatus(
                LocalDate.of(2026, 5, 25), 2L, "CANCELLED");

        assertEquals(1, northSubmitted.size());
        assertEquals(1L, northSubmitted.getFirst().getChurchId());
        assertEquals(1, southCancelled.size());
        assertEquals(3L, southCancelled.getFirst().getChurchId());
    }

    @Test
    void defaultPreviousWeekSupportsPreviousAndNextNavigationMath() {
        LocalDate defaultWeek = service.defaultWeekStart();

        assertEquals(LocalDate.of(2026, 5, 25), defaultWeek);
        assertEquals(LocalDate.of(2026, 5, 18), defaultWeek.minusWeeks(1));
        assertEquals(LocalDate.of(2026, 6, 1), defaultWeek.plusWeeks(1));
    }

    @Test
    void loadSubmissionDetailsLogsDetailsView() {
        SubmissionDetailsDto details = service.loadSubmissionDetails(10L);

        assertEquals("R-10", details.getReceiptNo());
        assertEquals(ReceiptStatus.ACTIVE, details.getReceiptStatus());
        assertEquals(ActivityLogService.SUBMISSION_DETAILS_VIEWED, activityLogService.lastAction);
    }

    @Test
    void nonMondayWeekStartIsRejected() {
        assertThrows(SubmissionStatusService.SubmissionStatusException.class,
                () -> service.loadWeeklyStatus(LocalDate.of(2026, 5, 26), null, "ALL"));
    }

    private SubmissionStatusDto row(List<SubmissionStatusDto> rows, long churchId) {
        return rows.stream()
                .filter(row -> row.getChurchId() == churchId)
                .findFirst()
                .orElseThrow();
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-06-02T04:30:00Z"), ZoneId.of("Asia/Colombo"));
    }

    private static class FakeSubmissionStatusRepository extends SubmissionStatusRepository {
        private final List<ChurchRow> churches = List.of(
                new ChurchRow(1L, "C001", "Central", 1L, "North"),
                new ChurchRow(2L, "C002", "Hill", 1L, "North"),
                new ChurchRow(3L, "C003", "Lake", 2L, "South")
        );
        private final List<ReceiptRow> receipts = List.of(
                new ReceiptRow(10L, 1L, 1L, "R-10", LocalDate.of(2026, 5, 25),
                        LocalDateTime.of(2026, 6, 2, 9, 0), "ACTIVE", true,
                        new BigDecimal("1000.00"), new BigDecimal("500.00"), new BigDecimal("200.00")),
                new ReceiptRow(11L, 3L, 2L, "R-11", LocalDate.of(2026, 5, 25),
                        LocalDateTime.of(2026, 6, 1, 9, 0), "CANCELLED", false,
                        new BigDecimal("300.00"), BigDecimal.ZERO, BigDecimal.ZERO),
                new ReceiptRow(12L, 3L, 2L, "R-12", LocalDate.of(2026, 5, 18),
                        LocalDateTime.of(2026, 5, 20, 9, 0), "ACTIVE", false,
                        new BigDecimal("900.00"), BigDecimal.ZERO, BigDecimal.ZERO)
        );

        private FakeSubmissionStatusRepository() {
            super((DataSource) null);
        }

        @Override
        public List<SubmissionStatusDto> getWeeklySubmissionStatus(LocalDate weekStartDate, Long regionId,
                                                                   Long churchId, String status) {
            return churches.stream()
                    .filter(church -> regionId == null || church.regionId().equals(regionId))
                    .filter(church -> churchId == null || church.id().equals(churchId))
                    .map(church -> statusRow(church, weekStartDate))
                    .filter(row -> status == null || "ALL".equals(status) || status.equals(row.getStatus()))
                    .toList();
        }

        @Override
        public SubmissionSummaryDto getWeeklySummary(LocalDate weekStartDate, Long regionId, Long churchId) {
            List<SubmissionStatusDto> rows = getWeeklySubmissionStatus(weekStartDate, regionId, churchId, "ALL");
            SubmissionSummaryDto summary = new SubmissionSummaryDto();
            summary.setTotalChurches(rows.size());
            summary.setSubmittedChurches(rows.stream().filter(SubmissionStatusDto::isSubmitted).count());
            summary.setPendingChurches(rows.stream().filter(SubmissionStatusDto::isPending).count());
            summary.setCancelledReceipts(rows.stream().filter(SubmissionStatusDto::isCancelled).count());
            summary.setLateSubmissions(rows.stream()
                    .filter(SubmissionStatusDto::isSubmitted)
                    .filter(SubmissionStatusDto::isLateSubmission)
                    .count());
            summary.setTotalCollectionAmount(rows.stream()
                    .filter(SubmissionStatusDto::isSubmitted)
                    .map(SubmissionStatusDto::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            summary.setSmsFailedCount(1);
            summary.setUnprintedReceiptsCount(1);
            return summary;
        }

        @Override
        public SubmissionTotalsDto getSubmissionTotals(LocalDate weekStartDate, Long regionId, Long churchId) {
            SubmissionTotalsDto totals = new SubmissionTotalsDto();
            List<ReceiptRow> activeRows = receipts.stream()
                    .filter(receipt -> receipt.weekStartDate().equals(weekStartDate))
                    .filter(receipt -> "ACTIVE".equals(receipt.status()))
                    .filter(receipt -> regionId == null || receipt.regionId().equals(regionId))
                    .filter(receipt -> churchId == null || receipt.churchId().equals(churchId))
                    .toList();
            totals.setTotalOffertory(activeRows.stream()
                    .map(ReceiptRow::offertory)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            totals.setTotalTithes(activeRows.stream()
                    .map(ReceiptRow::tithes)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            totals.setTotalOtherDonations(activeRows.stream()
                    .map(ReceiptRow::other)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            totals.setGrandTotal(activeRows.stream()
                    .map(ReceiptRow::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            return totals;
        }

        @Override
        public Optional<SubmissionDetailsDto> getSubmissionDetails(long receiptId) {
            return receipts.stream()
                    .filter(receipt -> receipt.id() == receiptId)
                    .findFirst()
                    .map(receipt -> {
                        SubmissionDetailsDto details = new SubmissionDetailsDto();
                        ChurchRow church = church(receipt.churchId());
                        details.setReceiptId(receipt.id());
                        details.setReceiptNo(receipt.receiptNo());
                        details.setChurchCode(church.code());
                        details.setChurchName(church.name());
                        details.setWeekStartDate(receipt.weekStartDate());
                        details.setWeekEndDate(receipt.weekStartDate().plusDays(6));
                        details.setBearerName("Treasurer");
                        details.setSubmittedBy("System Administrator");
                        details.setSubmittedDate(receipt.submittedDate());
                        details.setLateSubmission(receipt.late());
                        details.setReceiptStatus(ReceiptStatus.valueOf(receipt.status()));
                        details.setOffertoryAmount(receipt.offertory());
                        details.setTithesAmount(receipt.tithes());
                        details.setOtherDonationsAmount(receipt.other());
                        details.setTotalAmount(receipt.total());
                        details.setAuditHistory(List.of("Created"));
                        return details;
                    });
        }

        private SubmissionStatusDto statusRow(ChurchRow church, LocalDate weekStartDate) {
            Optional<ReceiptRow> latest = receipts.stream()
                    .filter(receipt -> receipt.churchId().equals(church.id()))
                    .filter(receipt -> receipt.weekStartDate().equals(weekStartDate))
                    .max(Comparator.comparing(ReceiptRow::submittedDate).thenComparing(ReceiptRow::id));
            SubmissionStatusDto dto = new SubmissionStatusDto();
            dto.setChurchId(church.id());
            dto.setChurchCode(church.code());
            dto.setChurchName(church.name());
            dto.setRegionName(church.regionName());
            if (latest.isEmpty()) {
                dto.setStatus("PENDING");
                return dto;
            }
            ReceiptRow receipt = latest.get();
            dto.setReceiptId(receipt.id());
            dto.setReceiptNo(receipt.receiptNo());
            dto.setSubmittedDate(receipt.submittedDate());
            dto.setLateSubmission(receipt.late());
            dto.setStatus("ACTIVE".equals(receipt.status()) ? "SUBMITTED" : "CANCELLED");
            dto.setOffertoryAmount(receipt.offertory());
            dto.setTithesAmount(receipt.tithes());
            dto.setOtherDonationsAmount(receipt.other());
            dto.setTotalAmount(receipt.total());
            return dto;
        }

        private ChurchRow church(Long churchId) {
            return churches.stream()
                    .filter(church -> church.id().equals(churchId))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String lastAction;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logSubmissionStatusViewed(Long userId, String weekStartDate, Long regionId, Long churchId,
                                              String status, int resultCount) {
            lastAction = SUBMISSION_STATUS_VIEWED;
        }

        @Override
        public void logSubmissionDetailsViewed(Long userId, long receiptId, String receiptNo) {
            lastAction = SUBMISSION_DETAILS_VIEWED;
        }
    }

    private record ChurchRow(Long id, String code, String name, Long regionId, String regionName) {
    }

    private record ReceiptRow(Long id, Long churchId, Long regionId, String receiptNo, LocalDate weekStartDate,
                              LocalDateTime submittedDate, String status, boolean late, BigDecimal offertory,
                              BigDecimal tithes, BigDecimal other) {
        private BigDecimal total() {
            return offertory.add(tithes).add(other);
        }
    }
}
