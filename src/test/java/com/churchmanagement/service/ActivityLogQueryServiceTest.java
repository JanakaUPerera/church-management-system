package com.churchmanagement.service;

import com.churchmanagement.dto.ActivityLogDto;
import com.churchmanagement.dto.ActivityLogSearchCriteria;
import com.churchmanagement.repository.ActivityLogRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActivityLogQueryServiceTest {
    private FakeActivityLogRepository activityLogRepository;
    private FakeActivityLogService activityLogService;
    private ActivityLogQueryService activityLogQueryService;

    @BeforeEach
    void setUp() {
        activityLogRepository = new FakeActivityLogRepository();
        activityLogService = new FakeActivityLogService();
        activityLogQueryService = new ActivityLogQueryService(activityLogRepository, activityLogService);
        AuthContext.setCurrentUser(new AuthenticatedUser(7L, "admin", "System Administrator", 1L,
                "Admin", List.of("activity.view")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void userWithActivityViewCanSearchLogs() {
        List<ActivityLogDto> logs = activityLogQueryService.searchLogs(new ActivityLogSearchCriteria());

        assertEquals(3, logs.size());
        assertEquals(ActivityLogService.ACTIVITY_LOGS_SEARCHED, activityLogService.action);
    }

    @Test
    void userWithoutActivityViewCannotSearchLogs() {
        AuthContext.setCurrentUser(new AuthenticatedUser(8L, "user", "Standard User", 2L,
                "User", List.of("receipt.create")));

        ActivityLogQueryService.ActivityLogException exception = assertThrows(
                ActivityLogQueryService.ActivityLogException.class,
                () -> activityLogQueryService.searchLogs(new ActivityLogSearchCriteria()));

        assertEquals("You do not have permission to view activity logs.", exception.getMessage());
    }

    @Test
    void rejectInvalidDateRange() {
        ActivityLogSearchCriteria criteria = new ActivityLogSearchCriteria();
        criteria.setDateFrom(LocalDate.of(2026, 6, 1));
        criteria.setDateTo(LocalDate.of(2026, 5, 31));

        ActivityLogQueryService.ActivityLogException exception = assertThrows(
                ActivityLogQueryService.ActivityLogException.class,
                () -> activityLogQueryService.searchLogs(criteria));

        assertEquals("Invalid date range.", exception.getMessage());
    }

    @Test
    void searchByAction() {
        ActivityLogSearchCriteria criteria = new ActivityLogSearchCriteria();
        criteria.setAction("LOGIN_SUCCESS");

        List<ActivityLogDto> logs = activityLogQueryService.searchLogs(criteria);

        assertEquals(1, logs.size());
        assertEquals("LOGIN_SUCCESS", logs.getFirst().getAction());
    }

    @Test
    void searchByModule() {
        ActivityLogSearchCriteria criteria = new ActivityLogSearchCriteria();
        criteria.setModule("SMS");

        List<ActivityLogDto> logs = activityLogQueryService.searchLogs(criteria);

        assertEquals(1, logs.size());
        assertEquals("SMS", logs.getFirst().getModule());
    }

    @Test
    void searchByUser() {
        ActivityLogSearchCriteria criteria = new ActivityLogSearchCriteria();
        criteria.setUserId(8L);

        List<ActivityLogDto> logs = activityLogQueryService.searchLogs(criteria);

        assertEquals(1, logs.size());
        assertEquals(8L, logs.getFirst().getUserId());
    }

    @Test
    void searchByKeyword() {
        ActivityLogSearchCriteria criteria = new ActivityLogSearchCriteria();
        criteria.setKeyword("receipt");

        List<ActivityLogDto> logs = activityLogQueryService.searchLogs(criteria);

        assertEquals(1, logs.size());
        assertEquals("Receipt created", logs.getFirst().getDescription());
    }

    @Test
    void getLogDetailsById() {
        ActivityLogDto log = activityLogQueryService.getLogDetails(2L);

        assertEquals(2L, log.getId());
        assertEquals(ActivityLogService.ACTIVITY_LOG_DETAILS_VIEWED, activityLogService.action);
    }

    @Test
    void rejectMissingLogId() {
        ActivityLogQueryService.ActivityLogException exception = assertThrows(
                ActivityLogQueryService.ActivityLogException.class,
                () -> activityLogQueryService.getLogDetails(99L));

        assertEquals("Activity log not found.", exception.getMessage());
    }

    private static class FakeActivityLogRepository extends ActivityLogRepository {
        private final List<ActivityLogDto> logs = List.of(
                log(1L, 7L, "admin", "LOGIN_SUCCESS", "AUTH", null, "Successful login"),
                log(2L, 7L, "admin", "SMS_SENT", "SMS", "42", "SMS sent"),
                log(3L, 8L, "clerk", "RECEIPT_CREATED", "Receipt", "R-1", "Receipt created")
        );

        private FakeActivityLogRepository() {
            super((DataSource) null);
        }

        @Override
        public List<ActivityLogDto> searchLogs(ActivityLogSearchCriteria criteria) {
            List<ActivityLogDto> matches = new ArrayList<>(logs);
            if (criteria.getAction() != null) {
                matches.removeIf(log -> !criteria.getAction().equals(log.getAction()));
            }
            if (criteria.getModule() != null) {
                matches.removeIf(log -> !criteria.getModule().equals(log.getModule()));
            }
            if (criteria.getUserId() != null) {
                matches.removeIf(log -> !criteria.getUserId().equals(log.getUserId()));
            }
            if (criteria.getKeyword() != null) {
                String keyword = criteria.getKeyword().toLowerCase();
                matches.removeIf(log -> !contains(log.getAction(), keyword)
                        && !contains(log.getModule(), keyword)
                        && !contains(log.getRecordId(), keyword)
                        && !contains(log.getUsername(), keyword)
                        && !contains(log.getDescription(), keyword));
            }
            return matches;
        }

        @Override
        public Optional<ActivityLogDto> findById(long id) {
            return logs.stream().filter(log -> log.getId() == id).findFirst();
        }

        private static ActivityLogDto log(Long id, Long userId, String username, String action, String module,
                                          String recordId, String description) {
            ActivityLogDto log = new ActivityLogDto();
            log.setId(id);
            log.setUserId(userId);
            log.setUsername(username);
            log.setAction(action);
            log.setModule(module);
            log.setRecordId(recordId);
            log.setDescription(description);
            return log;
        }

        private static boolean contains(String value, String keyword) {
            return value != null && value.toLowerCase().contains(keyword);
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String action;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logActivityLogsViewed(Long userId, int resultCount) {
            action = ACTIVITY_LOGS_VIEWED;
        }

        @Override
        public void logActivityLogsSearched(Long userId, int resultCount) {
            action = ACTIVITY_LOGS_SEARCHED;
        }

        @Override
        public void logActivityLogDetailsViewed(Long userId, long activityLogId) {
            action = ACTIVITY_LOG_DETAILS_VIEWED;
        }
    }
}
