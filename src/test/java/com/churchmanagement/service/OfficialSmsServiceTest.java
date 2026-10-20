package com.churchmanagement.service;

import com.churchmanagement.dto.OfficialSmsRecipient;
import com.churchmanagement.dto.OfficialSmsResult;
import com.churchmanagement.entity.Church;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfficialSmsServiceTest {
    private FakeSmsLogRepository smsLogRepository;
    private FakeActivityLogService activityLogService;
    private Map<String, String> settings;
    private OfficialSmsService service;

    @BeforeEach
    void setUp() {
        smsLogRepository = new FakeSmsLogRepository();
        activityLogService = new FakeActivityLogService();
        settings = new HashMap<>(Map.of(OfficialSmsService.FOOTER_SETTING_KEY, "- Head Office"));
        service = new OfficialSmsService(smsLogRepository, new FakeChurchRepository(), activityLogService,
                new FakeSystemConfigurationCache(settings),
                Clock.fixed(Instant.parse("2026-10-20T09:00:00Z"), ZoneId.of("UTC")));
        AuthContext.setCurrentUser(new AuthenticatedUser(7L, "admin", "System Administrator", 1L,
                "Admin", List.of(OfficialSmsService.SEND_PERMISSION)));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void queuesOneRowPerValidRecipientWithFooter() {
        OfficialSmsResult result = service.sendOfficialMessage("Meeting on Sunday", List.of(1L, 2L, 3L));

        assertEquals(1, result.queuedCount());
        assertEquals(2, result.skipped().size());
        assertEquals(1, result.partCount());
        assertEquals(List.of(1L), smsLogRepository.recipients.stream().map(OfficialSmsRecipient::churchId).toList());
        assertEquals("+94771234567", smsLogRepository.recipients.getFirst().mobileNumber());
        assertEquals("Meeting on Sunday\n- Head Office", smsLogRepository.message);
        assertEquals(7L, smsLogRepository.queuedByUserId);
        assertEquals(result.batchUuid(), smsLogRepository.batchUuid);
        assertEquals(LocalDateTime.of(2026, 10, 20, 9, 0), smsLogRepository.queuedAt);
    }

    @Test
    void writesOneActivityEntryPerSend() {
        OfficialSmsResult result = service.sendOfficialMessage("Meeting on Sunday", List.of(1L, 2L, 3L));

        assertEquals(ActivityLogService.OFFICIAL_SMS_QUEUED, activityLogService.action);
        assertEquals(result.batchUuid(), activityLogService.batchUuid);
        assertEquals(1, activityLogService.queuedCount);
        assertEquals(2, activityLogService.skippedCount);
    }

    @Test
    void composeMessageWithoutFooterWhenBlank() {
        settings.put(OfficialSmsService.FOOTER_SETTING_KEY, "  ");

        assertEquals("Hello", service.composeMessage("  Hello  "));
    }

    @Test
    void countsPartsOfLongMessage() {
        OfficialSmsResult result = service.sendOfficialMessage("a".repeat(200), List.of(1L));

        assertEquals(2, result.partCount());
    }

    @Test
    void acceptsExactly459CharactersIncludingFooter() {
        // 445 + newline + 13-character footer = 459
        OfficialSmsResult result = service.sendOfficialMessage("a".repeat(445), List.of(1L));

        assertEquals(3, result.partCount());
    }

    @Test
    void rejectsMessageOver459CharactersIncludingFooter() {
        OfficialSmsService.OfficialSmsException exception = assertThrows(OfficialSmsService.OfficialSmsException.class,
                () -> service.sendOfficialMessage("a".repeat(446), List.of(1L)));

        assertEquals("Message is too long: 460 of 459 characters, footer included.", exception.getMessage());
        assertNull(smsLogRepository.message);
    }

    @Test
    void rejectsUnsupportedCharacter() {
        OfficialSmsService.OfficialSmsException exception = assertThrows(OfficialSmsService.OfficialSmsException.class,
                () -> service.sendOfficialMessage("Hello අ", List.of(1L)));

        assertEquals("Unsupported character: 'අ'. Only English (GSM) characters can be sent.",
                exception.getMessage());
    }

    @Test
    void rejectsWithoutPermission() {
        AuthContext.setCurrentUser(new AuthenticatedUser(8L, "user", "Standard User", 2L, "User",
                List.of("sms.menu.view")));

        OfficialSmsService.OfficialSmsException exception = assertThrows(OfficialSmsService.OfficialSmsException.class,
                () -> service.sendOfficialMessage("Hello", List.of(1L)));

        assertEquals("You do not have permission to send official messages.", exception.getMessage());
    }

    @Test
    void rejectsBlankMessage() {
        OfficialSmsService.OfficialSmsException exception = assertThrows(OfficialSmsService.OfficialSmsException.class,
                () -> service.sendOfficialMessage("   ", List.of(1L)));

        assertEquals("Message is required.", exception.getMessage());
    }

    @Test
    void rejectsNoRecipients() {
        OfficialSmsService.OfficialSmsException exception = assertThrows(OfficialSmsService.OfficialSmsException.class,
                () -> service.sendOfficialMessage("Hello", List.of()));

        assertEquals("Select at least one branch.", exception.getMessage());
    }

    @Test
    void rejectsWhenNoSelectedBranchHasValidNumber() {
        OfficialSmsService.OfficialSmsException exception = assertThrows(OfficialSmsService.OfficialSmsException.class,
                () -> service.sendOfficialMessage("Hello", List.of(2L, 3L)));

        assertEquals("None of the selected branches has a valid mobile number.", exception.getMessage());
        assertNull(smsLogRepository.message);
    }

    @Test
    void flagsMissingAndInvalidNumbers() {
        List<OfficialSmsRecipient> recipients = service.loadRecipients();

        assertEquals(OfficialSmsRecipient.Status.OK, recipients.get(0).status());
        assertEquals(OfficialSmsRecipient.Status.MISSING, recipients.get(1).status());
        assertEquals(OfficialSmsRecipient.Status.INVALID, recipients.get(2).status());
    }

    private static class FakeSmsLogRepository extends SmsLogRepository {
        private String batchUuid;
        private String message;
        private List<OfficialSmsRecipient> recipients;
        private long queuedByUserId;
        private LocalDateTime queuedAt;

        private FakeSmsLogRepository() {
            super((DataSource) null);
        }

        @Override
        public void enqueueOfficialBatch(String batchUuid, String message, List<OfficialSmsRecipient> recipients,
                                         long queuedByUserId, LocalDateTime queuedAt) {
            this.batchUuid = batchUuid;
            this.message = message;
            this.recipients = recipients;
            this.queuedByUserId = queuedByUserId;
            this.queuedAt = queuedAt;
        }
    }

    private static class FakeChurchRepository extends ChurchRepository {
        private FakeChurchRepository() {
            super((DataSource) null);
        }

        @Override
        public List<Church> findOperationalChurches() {
            return List.of(church(1L, "0001", "0771234567"), church(2L, "0002", null),
                    church(3L, "0003", "0111234567"));
        }

        private static Church church(long id, String code, String mobile) {
            Church church = new Church();
            church.setId(id);
            church.setChurchCode(code);
            church.setChurchName("Church " + code);
            church.setRegionName("Region 1");
            church.setSmsMobileNumber(mobile);
            return church;
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String action;
        private String batchUuid;
        private int queuedCount;
        private int skippedCount;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logOfficialSmsQueued(Long userId, String batchUuid, int queuedCount, int skippedCount,
                                         int partCount, String message) {
            this.action = OFFICIAL_SMS_QUEUED;
            this.batchUuid = batchUuid;
            this.queuedCount = queuedCount;
            this.skippedCount = skippedCount;
        }
    }

    private static class FakeSystemConfigurationCache extends SystemConfigurationCache {
        private final Map<String, String> values;

        private FakeSystemConfigurationCache(Map<String, String> values) {
            super(null);
            this.values = values;
        }

        @Override
        public String getString(String key) {
            return values.get(key);
        }
    }
}
