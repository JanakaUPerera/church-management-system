package com.churchmanagement.service;

import com.churchmanagement.dto.SmsLogDto;
import com.churchmanagement.dto.SmsLogSearchCriteria;
import com.churchmanagement.enums.SmsSendStatus;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmsLogServiceTest {
    private FakeSmsLogRepository smsLogRepository;
    private FakeActivityLogService activityLogService;
    private SmsLogService smsLogService;

    @BeforeEach
    void setUp() {
        smsLogRepository = new FakeSmsLogRepository();
        activityLogService = new FakeActivityLogService();
        smsLogService = new SmsLogService(smsLogRepository, activityLogService);
        AuthContext.setCurrentUser(new AuthenticatedUser(7L, "admin", "System Administrator", 1L,
                "Admin", List.of("sms.logs.view")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void searchLatestLogs() {
        List<SmsLogDto> logs = smsLogService.latestLogs(100);

        assertEquals(2, logs.size());
        assertEquals(100, smsLogRepository.criteria.getLimit());
        assertEquals(ActivityLogService.SMS_LOGS_VIEWED, activityLogService.action);
    }

    @Test
    void filterByDateRange() {
        SmsLogSearchCriteria criteria = new SmsLogSearchCriteria();
        criteria.setDateFrom(LocalDate.of(2026, 5, 1));
        criteria.setDateTo(LocalDate.of(2026, 5, 31));

        smsLogService.searchSmsLogs(criteria);

        assertEquals(LocalDate.of(2026, 5, 1), smsLogRepository.criteria.getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 31), smsLogRepository.criteria.getDateTo());
    }

    @Test
    void filterByChurch() {
        SmsLogSearchCriteria criteria = new SmsLogSearchCriteria();
        criteria.setChurchId(10L);

        smsLogService.searchSmsLogs(criteria);

        assertEquals(10L, smsLogRepository.criteria.getChurchId());
    }

    @Test
    void filterByStatus() {
        SmsLogSearchCriteria criteria = new SmsLogSearchCriteria();
        criteria.setStatus(SmsLogRepository.SmsStatus.FAILED);

        smsLogService.searchSmsLogs(criteria);

        assertEquals(SmsSendStatus.FAILED, smsLogRepository.criteria.getStatus());
    }

    @Test
    void filterByMobileNumber() {
        SmsLogSearchCriteria criteria = new SmsLogSearchCriteria();
        criteria.setMobileNumber("071");

        smsLogService.searchSmsLogs(criteria);

        assertEquals("071", smsLogRepository.criteria.getMobileNumber());
    }

    @Test
    void rejectInvalidDateRange() {
        SmsLogSearchCriteria criteria = new SmsLogSearchCriteria();
        criteria.setDateFrom(LocalDate.of(2026, 6, 1));
        criteria.setDateTo(LocalDate.of(2026, 5, 31));

        SmsLogService.SmsLogException exception = assertThrows(
                SmsLogService.SmsLogException.class,
                () -> smsLogService.searchSmsLogs(criteria));

        assertEquals("Invalid date range.", exception.getMessage());
    }

    @Test
    void rejectUserWithoutSmsLogsViewPermission() {
        AuthContext.setCurrentUser(new AuthenticatedUser(8L, "user", "Standard User", 2L,
                "User", List.of("receipt.create")));

        SmsLogService.SmsLogException exception = assertThrows(
                SmsLogService.SmsLogException.class,
                () -> smsLogService.latestLogs(100));

        assertEquals("You do not have permission to view SMS logs.", exception.getMessage());
    }

    private static class FakeSmsLogRepository extends SmsLogRepository {
        private SmsLogSearchCriteria criteria;

        private FakeSmsLogRepository() {
            super((DataSource) null);
        }

        @Override
        public List<SmsLogDto> searchSmsLogs(SmsLogSearchCriteria criteria) {
            this.criteria = criteria;
            List<SmsLogDto> logs = new ArrayList<>();
            logs.add(new SmsLogDto());
            logs.add(new SmsLogDto());
            return logs;
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String action;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logSmsLogsViewed(Long userId, int resultCount) {
            action = SMS_LOGS_VIEWED;
        }

        @Override
        public void logSmsLogsSearched(Long userId, int resultCount) {
            action = SMS_LOGS_SEARCHED;
        }
    }
}
