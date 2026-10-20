package com.churchmanagement.service;

import com.churchmanagement.repository.DashboardRepository;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.util.WeekUtil;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Small status counts shown on Home tiles. A failing count is simply left out. */
public class HomeBadgeService {
    private static final int FAILED_SMS_DAYS = 7;

    private final DashboardRepository dashboardRepository;
    private final SmsLogRepository smsLogRepository;
    private final SystemConfigurationCache configurationCache;
    private final Clock clock;

    public HomeBadgeService() {
        this(new DashboardRepository(), new SmsLogRepository(), SystemConfigurationCache.getInstance(),
                Clock.systemDefaultZone());
    }

    public HomeBadgeService(DashboardRepository dashboardRepository, SmsLogRepository smsLogRepository,
                            SystemConfigurationCache configurationCache, Clock clock) {
        this.dashboardRepository = dashboardRepository;
        this.smsLogRepository = smsLogRepository;
        this.configurationCache = configurationCache;
        this.clock = clock;
    }

    /** Badge text keyed by module id; modules with nothing to report are absent. */
    public Map<String, String> badgeTexts() {
        Map<String, String> badges = new LinkedHashMap<>();
        put(badges, "collections", this::pendingSubmissions, " pending");
        put(badges, "messaging", this::recentFailedSms, " failed");
        return badges;
    }

    private long pendingSubmissions() {
        DayOfWeek identifierDay = WeekUtil.parseIdentifierDay(
                configurationCache.getString(WeekUtil.IDENTIFIER_DAY_SETTING_KEY));
        LocalDate identifier = WeekUtil.currentIdentifier(LocalDate.now(clock), identifierDay);
        return dashboardRepository.countPendingChurchesForWeek(WeekUtil.weekStartFor(identifier), identifier);
    }

    private long recentFailedSms() {
        return smsLogRepository.countRecentFailed(LocalDateTime.now(clock).minusDays(FAILED_SMS_DAYS));
    }

    private void put(Map<String, String> badges, String moduleId, LongSupplier count, String suffix) {
        try {
            long value = count.getAsLong();
            if (value > 0) {
                badges.put(moduleId, value + suffix);
            }
        } catch (RuntimeException exception) {
            System.err.println("Home badge '" + moduleId + "' unavailable: " + exception.getMessage());
        }
    }
}
