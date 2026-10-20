package com.churchmanagement.service;

import com.churchmanagement.dto.OfficialSmsRecipient;
import com.churchmanagement.dto.OfficialSmsResult;
import com.churchmanagement.entity.Church;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.repository.SmsLogRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.util.SmsLengthCalculator;
import com.churchmanagement.util.SriLankanMobileNumber;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Queues an official message to selected branches. Runs on any machine; the primary machine's
 * {@link SmsQueueProcessor} sends the queued rows.
 */
public class OfficialSmsService {
    public static final String FOOTER_SETTING_KEY = "sms.official.footer";
    public static final String SEND_PERMISSION = "sms.official.send";

    private final SmsLogRepository smsLogRepository;
    private final ChurchRepository churchRepository;
    private final ActivityLogService activityLogService;
    private final SystemConfigurationCache configurationCache;
    private final Clock clock;

    public OfficialSmsService() {
        this(new SmsLogRepository(), new ChurchRepository(), new ActivityLogService(),
                SystemConfigurationCache.getInstance(), Clock.systemDefaultZone());
    }

    public OfficialSmsService(SmsLogRepository smsLogRepository, ChurchRepository churchRepository,
                              ActivityLogService activityLogService, SystemConfigurationCache configurationCache,
                              Clock clock) {
        this.smsLogRepository = smsLogRepository;
        this.churchRepository = churchRepository;
        this.activityLogService = activityLogService;
        this.configurationCache = configurationCache;
        this.clock = clock;
    }

    /** Every active branch, with its number checked the same way the modem checks it. */
    public List<OfficialSmsRecipient> loadRecipients() {
        return churchRepository.findOperationalChurches().stream()
                .map(OfficialSmsService::toRecipient)
                .toList();
    }

    public static OfficialSmsRecipient toRecipient(Church church) {
        String normalized = SriLankanMobileNumber.normalize(church.getSmsMobileNumber());
        OfficialSmsRecipient.Status status = normalized == null
                ? OfficialSmsRecipient.Status.MISSING
                : SriLankanMobileNumber.isValid(normalized)
                ? OfficialSmsRecipient.Status.OK
                : OfficialSmsRecipient.Status.INVALID;
        return new OfficialSmsRecipient(church.getId(), church.getChurchCode(), church.getChurchName(),
                church.getRegionName(), normalized == null ? "" : normalized, status);
    }

    public String footer() {
        String footer = configurationCache.getString(FOOTER_SETTING_KEY);
        return footer == null ? "" : footer.strip();
    }

    public String composeMessage(String body) {
        String stripped = body == null ? "" : body.strip();
        String footer = footer();
        return footer.isEmpty() ? stripped : stripped + "\n" + footer;
    }

    public OfficialSmsResult sendOfficialMessage(String body, Collection<Long> churchIds) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new OfficialSmsException("Please sign in to send messages."));
        if (!new PermissionGuard(currentUser).can(SEND_PERMISSION)) {
            throw new OfficialSmsException("You do not have permission to send official messages.");
        }
        if (body == null || body.isBlank()) {
            throw new OfficialSmsException("Message is required.");
        }
        if (churchIds == null || churchIds.isEmpty()) {
            throw new OfficialSmsException("Select at least one branch.");
        }
        String message = composeMessage(body);
        String unsupported = SmsLengthCalculator.firstUnsupportedCharacter(message);
        if (unsupported != null) {
            throw new OfficialSmsException("Unsupported character: '" + unsupported
                    + "'. Only English (GSM) characters can be sent.");
        }
        int septets = SmsLengthCalculator.septetLength(message);
        if (septets > SmsLengthCalculator.MAX_SEPTETS) {
            throw new OfficialSmsException("Message is too long: " + septets + " of "
                    + SmsLengthCalculator.MAX_SEPTETS + " characters, footer included.");
        }

        Set<Long> selectedIds = new HashSet<>(churchIds);
        List<OfficialSmsRecipient> selected = loadRecipients().stream()
                .filter(recipient -> selectedIds.contains(recipient.churchId()))
                .toList();
        List<OfficialSmsRecipient> sendable = selected.stream().filter(OfficialSmsRecipient::isSendable).toList();
        List<OfficialSmsRecipient> skipped = selected.stream().filter(recipient -> !recipient.isSendable()).toList();
        if (sendable.isEmpty()) {
            throw new OfficialSmsException("None of the selected branches has a valid mobile number.");
        }

        String batchUuid = UUID.randomUUID().toString();
        int partCount = SmsLengthCalculator.partCount(message);
        smsLogRepository.enqueueOfficialBatch(batchUuid, message, sendable, currentUser.getUserId(),
                LocalDateTime.now(clock));
        activityLogService.logOfficialSmsQueued(currentUser.getUserId(), batchUuid, sendable.size(), skipped.size(),
                partCount, message);
        return new OfficialSmsResult(batchUuid, sendable.size(), skipped, partCount);
    }

    public static class OfficialSmsException extends RuntimeException {
        public OfficialSmsException(String message) {
            super(message);
        }
    }
}
