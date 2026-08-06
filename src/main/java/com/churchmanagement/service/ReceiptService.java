package com.churchmanagement.service;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.CreateReceiptRequest;
import com.churchmanagement.dto.ReceiptItemDto;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Receipt;
import com.churchmanagement.entity.ReceiptItem;
import com.churchmanagement.entity.Region;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.repository.ReceiptRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import com.churchmanagement.util.WeekUtil;
import com.churchmanagement.validation.ReceiptValidator;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReceiptService {
    private static final String DUPLICATE_ACTIVE_RECEIPT_MESSAGE =
            "An active receipt already exists for this church and week. Cancel the existing receipt before creating a corrected receipt.";

    private final ReceiptRepository receiptRepository;
    private final ChurchRepository churchRepository;
    private final ReceiptNumberGeneratorService receiptNumberGeneratorService;
    private final ReceiptPrintService receiptPrintService;
    private final ReceiptSmsNotificationService receiptSmsNotificationService;
    private final ActivityLogService activityLogService;
    private final Clock clock;
    private final DataSource dataSource;
    private final SystemConfigurationCache configurationCache;

    public ReceiptService() {
        this(new ReceiptRepository(), new ChurchRepository(), new ReceiptNumberGeneratorService(),
                new ReceiptPrintService(), new ReceiptSmsNotificationService(), new ActivityLogService(),
                Clock.systemDefaultZone(), DatabaseConfig.getDataSource());
    }

    public ReceiptService(ReceiptRepository receiptRepository, ChurchRepository churchRepository,
                          ReceiptNumberGeneratorService receiptNumberGeneratorService,
                          ActivityLogService activityLogService, Clock clock, DataSource dataSource) {
        this(receiptRepository, churchRepository, receiptNumberGeneratorService, null, null,
                activityLogService, clock, dataSource);
    }

    public ReceiptService(ReceiptRepository receiptRepository, ChurchRepository churchRepository,
                          ReceiptNumberGeneratorService receiptNumberGeneratorService,
                          ReceiptPrintService receiptPrintService,
                          ActivityLogService activityLogService, Clock clock, DataSource dataSource) {
        this(receiptRepository, churchRepository, receiptNumberGeneratorService, receiptPrintService, null,
                activityLogService, clock, dataSource);
    }

    public ReceiptService(ReceiptRepository receiptRepository, ChurchRepository churchRepository,
                          ReceiptNumberGeneratorService receiptNumberGeneratorService,
                          ReceiptPrintService receiptPrintService,
                          ReceiptSmsNotificationService receiptSmsNotificationService,
                          ActivityLogService activityLogService, Clock clock, DataSource dataSource) {
        this(receiptRepository, churchRepository, receiptNumberGeneratorService, receiptPrintService,
                receiptSmsNotificationService, activityLogService, clock, dataSource,
                SystemConfigurationCache.getInstance());
    }

    public ReceiptService(ReceiptRepository receiptRepository, ChurchRepository churchRepository,
                          ReceiptNumberGeneratorService receiptNumberGeneratorService,
                          ReceiptPrintService receiptPrintService,
                          ReceiptSmsNotificationService receiptSmsNotificationService,
                          ActivityLogService activityLogService, Clock clock, DataSource dataSource,
                          SystemConfigurationCache configurationCache) {
        this.receiptRepository = receiptRepository;
        this.churchRepository = churchRepository;
        this.receiptNumberGeneratorService = receiptNumberGeneratorService;
        this.receiptPrintService = receiptPrintService;
        this.receiptSmsNotificationService = receiptSmsNotificationService;
        this.activityLogService = activityLogService;
        this.clock = clock;
        this.dataSource = dataSource;
        this.configurationCache = configurationCache;
    }

    public ReceiptResponseDto createReceipt(CreateReceiptRequest request) {
        return createReceipt(request, true);
    }

    public ReceiptResponseDto createReceipt(CreateReceiptRequest request, boolean printOriginal) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new ReceiptException("Please sign in to create receipts."));
        new PermissionGuard(currentUser).require("receipt.create");

        LocalDate today = LocalDate.now(clock);
        boolean lateSubmission = request != null && WeekUtil.isBackWeek(request.getWeekStartDate(), today);
        enforceBackWeekSetting(lateSubmission);
        validateRequest(request, today, lateSubmission);

        Church church = loadActiveChurch(request.getChurchId());
        List<ReceiptItem> items = toReceiptItems(request.getItems());
        BigDecimal totalAmount = items.stream()
                .map(ReceiptItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Connection connection = null;
        boolean previousAutoCommit = true;
        boolean receiptCommitted = false;
        String warningMessage = null;
        try {
            connection = dataSource.getConnection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            if (receiptRepository.findActiveReceiptForChurchAndWeekForUpdate(
                    church.getId(), request.getWeekStartDate(), connection).isPresent()) {
                throw new ReceiptException(DUPLICATE_ACTIVE_RECEIPT_MESSAGE);
            }
            validateCorrectionReceipt(request, church, connection);

            LocalDateTime now = LocalDateTime.now(clock);
            Receipt receipt = buildReceipt(request, church, currentUser.getUserId(), lateSubmission, now);
            receipt.setReceiptNo(receiptNumberGeneratorService.generateReceiptNumber(connection));

            long receiptId = receiptRepository.insertReceipt(receipt, connection);
            for (ReceiptItem item : items) {
                item.setCreatedAt(now);
                receiptRepository.insertReceiptItem(receiptId, item, connection);
            }

            connection.commit();
            receiptCommitted = true;
            if (printOriginal && receiptPrintService != null) {
                try {
                    receiptPrintService.printOriginalReceipt(receiptId);
                } catch (ReceiptPrintService.ReceiptPrintException exception) {
                    // Receipt remains saved; original_printed stays false so history can offer Print Original.
                }
            }
            if (receiptSmsNotificationService != null) {
                try {
                    receiptSmsNotificationService.sendReceiptSubmissionSms(receiptId);
                } catch (ReceiptSmsNotificationService.SmsNotificationException exception) {
                    warningMessage = "Receipt saved, but SMS notification failed.";
                }
            }
            ReceiptResponseDto response = receiptRepository.findReceiptDetailsById(receiptId)
                    .orElseThrow(() -> new ReceiptException("Receipt was saved but could not be loaded."));
            response.setWarningMessage(warningMessage);
            activityLogService.logReceiptCreated(currentUser.getUserId(), response, church.getId(), totalAmount);
            return response;
        } catch (ReceiptException exception) {
            if (!receiptCommitted) {
                rollback(connection);
                logCreateFailed(currentUser.getUserId(), request, church, totalAmount, lateSubmission, exception.getMessage());
            }
            throw exception;
        } catch (SQLException | DatabaseException exception) {
            if (!receiptCommitted) {
                rollback(connection);
                logCreateFailed(currentUser.getUserId(), request, church, totalAmount, lateSubmission, exception.getMessage());
            }
            throw new ReceiptException("Unable to save receipt right now. Please try again later.", exception);
        } catch (RuntimeException exception) {
            if (!receiptCommitted) {
                rollback(connection);
                logCreateFailed(currentUser.getUserId(), request, church, totalAmount, lateSubmission, exception.getMessage());
            }
            throw new ReceiptException("Unable to save receipt right now. Please try again later.", exception);
        } finally {
            restoreAutoCommitAndClose(connection, previousAutoCommit);
        }
    }

    public void validateReceiptBeforeConfirmation(CreateReceiptRequest request) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new ReceiptException("Please sign in to create receipts."));
        new PermissionGuard(currentUser).require("receipt.create");

        LocalDate today = LocalDate.now(clock);
        boolean lateSubmission = request != null && WeekUtil.isBackWeek(request.getWeekStartDate(), today);
        enforceBackWeekSetting(lateSubmission);
        validateRequest(request, today, lateSubmission);
        Church church = loadActiveChurch(request.getChurchId());

        try {
            if (receiptRepository.existsActiveReceiptForChurchAndWeek(church.getId(), request.getWeekStartDate())) {
                throw new ReceiptException(DUPLICATE_ACTIVE_RECEIPT_MESSAGE);
            }
        } catch (ReceiptException exception) {
            throw exception;
        } catch (DatabaseException exception) {
            throw new ReceiptException("Unable to check existing receipts right now. Please try again later.", exception);
        }
    }

    private void validateRequest(CreateReceiptRequest request, LocalDate today, boolean lateSubmission) {
        List<String> errors = ReceiptValidator.validateForCreate(request, today, lateSubmission,
                isLateSubmissionReasonRequired());
        if (!errors.isEmpty()) {
            throw new ReceiptException(String.join("\n", errors));
        }
    }

    private void enforceBackWeekSetting(boolean lateSubmission) {
        String value = configurationCache.getString("receipt.allow.back.week");
        boolean backWeekAllowed = value == null || value.isBlank() || Boolean.parseBoolean(value);
        if (lateSubmission && !backWeekAllowed) {
            throw new ReceiptException("Back-week receipts are disabled in system settings.");
        }
    }

    private boolean isLateSubmissionReasonRequired() {
        return Boolean.parseBoolean(configurationCache.getString("receipt.late.reason.required"));
    }

    private Church loadActiveChurch(Long churchId) {
        Church church = churchRepository.findById(churchId)
                .orElseThrow(() -> new ReceiptException("Selected church could not be found."));
        if (church.getStatus() != Church.Status.ACTIVE) {
            throw new ReceiptException("Selected church is inactive. Choose an active church.");
        }
        if (church.getRegionStatus() == Region.Status.INACTIVE) {
            throw new ReceiptException("Selected church belongs to an inactive region. Choose a church from an active region.");
        }
        return church;
    }

    private Receipt buildReceipt(CreateReceiptRequest request, Church church, long userId, boolean lateSubmission,
                                 LocalDateTime now) {
        Receipt receipt = new Receipt();
        receipt.setChurchId(church.getId());
        receipt.setRegionId(church.getRegionId());
        receipt.setWeekStartDate(request.getWeekStartDate());
        receipt.setWeekEndDate(request.getWeekEndDate());
        receipt.setChurchServiceDate(request.getChurchServiceDate());
        receipt.setReceiptDateTime(now);
        receipt.setSubmittedByName(request.getSubmittedByName().strip());
        receipt.setIssuedByUserId(userId);
        receipt.setStatus(ReceiptStatus.ACTIVE);
        receipt.setLateSubmission(lateSubmission);
        receipt.setLateSubmissionReason(lateSubmission ? normalizeLateSubmissionReason(request.getLateSubmissionReason()) : null);
        receipt.setCorrectedFromReceiptId(request.getCorrectedFromReceiptId());
        receipt.setCreatedAt(now);
        return receipt;
    }

    private String normalizeLateSubmissionReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.strip();
    }

    private void validateCorrectionReceipt(CreateReceiptRequest request, Church church, Connection connection) {
        Long correctedFromReceiptId = request.getCorrectedFromReceiptId();
        if (correctedFromReceiptId == null) {
            return;
        }

        Receipt correctedReceipt = receiptRepository.findReceiptByIdForUpdate(correctedFromReceiptId, connection)
                .orElseThrow(() -> new ReceiptException("Corrected receipt could not be found."));
        if (correctedReceipt.getStatus() != ReceiptStatus.CANCELLED) {
            throw new ReceiptException("Corrected receipt must be cancelled before creating a new receipt.");
        }
        if (!church.getId().equals(correctedReceipt.getChurchId())
                || !request.getWeekStartDate().equals(correctedReceipt.getWeekStartDate())) {
            throw new ReceiptException("Corrected receipt must belong to the same church and week.");
        }
    }

    private List<ReceiptItem> toReceiptItems(List<ReceiptItemDto> itemDtos) {
        List<ReceiptItem> items = new ArrayList<>();
        for (ReceiptItemDto itemDto : itemDtos) {
            ReceiptItem item = new ReceiptItem();
            item.setCollectionType(itemDto.getCollectionType());
            item.setAmount(itemDto.getAmount());
            item.setNote(itemDto.getNote() == null || itemDto.getNote().isBlank() ? null : itemDto.getNote().strip());
            items.add(item);
        }
        return items;
    }

    private void logCreateFailed(long userId, CreateReceiptRequest request, Church church, BigDecimal totalAmount,
                                 boolean lateSubmission, String reason) {
        activityLogService.logReceiptCreateFailed(userId, request, church, totalAmount, lateSubmission, reason);
    }

    private void rollback(Connection connection) {
        if (connection == null) {
            return;
        }

        try {
            connection.rollback();
        } catch (SQLException exception) {
            System.err.println("Receipt rollback failed: " + exception.getMessage());
        }
    }

    private void restoreAutoCommitAndClose(Connection connection, boolean previousAutoCommit) {
        if (connection == null) {
            return;
        }

        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException exception) {
            System.err.println("Unable to restore receipt connection auto-commit: " + exception.getMessage());
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            System.err.println("Unable to close receipt connection: " + exception.getMessage());
        }
    }

    public static class ReceiptException extends RuntimeException {
        public ReceiptException(String message) {
            super(message);
        }

        public ReceiptException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
