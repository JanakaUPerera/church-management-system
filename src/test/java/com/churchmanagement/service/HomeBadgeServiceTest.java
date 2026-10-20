package com.churchmanagement.service;

import com.churchmanagement.repository.DashboardRepository;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.util.WeekUtil;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeBadgeServiceTest {
    // 21 Oct 2026, identifier day MONDAY; the expected week is computed with WeekUtil below.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneId.of("UTC"));

    @Test
    void badgesForPendingAndFailed() {
        FakeDashboardRepository dashboard = new FakeDashboardRepository(12);
        FakeSmsLogRepository sms = new FakeSmsLogRepository(3);

        Map<String, String> badges = service(dashboard, sms).badgeTexts();

        LocalDate identifier = WeekUtil.currentIdentifier(LocalDate.of(2026, 10, 21), DayOfWeek.MONDAY);
        assertEquals(Map.of("collections", "12 pending", "messaging", "3 failed"), badges);
        assertEquals(LocalDateTime.of(2026, 10, 14, 9, 0), sms.since);
        assertEquals(WeekUtil.weekStartFor(identifier), dashboard.weekStart);
        assertEquals(identifier, dashboard.weekEnd);
    }

    @Test
    void zeroCountsHaveNoBadge() {
        assertEquals(Map.of(), service(new FakeDashboardRepository(0), new FakeSmsLogRepository(0)).badgeTexts());
    }

    @Test
    void failureHidesOnlyThatBadge() {
        FakeDashboardRepository failing = new FakeDashboardRepository(-1);

        assertEquals(Map.of("messaging", "3 failed"),
                service(failing, new FakeSmsLogRepository(3)).badgeTexts());
    }

    private HomeBadgeService service(DashboardRepository dashboard, SmsLogRepository sms) {
        return new HomeBadgeService(dashboard, sms, new FakeSystemConfigurationCache(), CLOCK);
    }

    private static class FakeDashboardRepository extends DashboardRepository {
        private final long count;
        private LocalDate weekStart;
        private LocalDate weekEnd;

        private FakeDashboardRepository(long count) {
            super((DataSource) null);
            this.count = count;
        }

        @Override
        public long countPendingChurchesForWeek(LocalDate weekStart, LocalDate weekEnd) {
            if (count < 0) {
                throw new IllegalStateException("database down");
            }
            this.weekStart = weekStart;
            this.weekEnd = weekEnd;
            return count;
        }
    }

    private static class FakeSmsLogRepository extends SmsLogRepository {
        private final long count;
        private LocalDateTime since;

        private FakeSmsLogRepository(long count) {
            super((DataSource) null);
            this.count = count;
        }

        @Override
        public long countRecentFailed(LocalDateTime since) {
            this.since = since;
            return count;
        }
    }

    private static class FakeSystemConfigurationCache extends SystemConfigurationCache {
        private FakeSystemConfigurationCache() {
            super(null);
        }

        @Override
        public String getString(String key) {
            return "MONDAY";
        }
    }
}
