package com.churchmanagement.service;

import com.churchmanagement.dto.CreateReceiptRequest;
import com.churchmanagement.dto.ReceiptItemDto;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.entity.Church;
import com.churchmanagement.entity.Receipt;
import com.churchmanagement.entity.ReceiptItem;
import com.churchmanagement.entity.Region;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.repository.ChurchRepository;
import com.churchmanagement.repository.ReceiptRepository;
import com.churchmanagement.repository.SystemSettingRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.util.ReceiptNumberFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptServiceTest {
    private static final LocalDate CURRENT_WEEK_START = LocalDate.of(2026, 5, 12);
    private static final LocalDate CURRENT_WEEK_END = LocalDate.of(2026, 5, 18);
    private static final LocalDate BACK_WEEK_START = LocalDate.of(2026, 5, 5);
    private static final LocalDate BACK_WEEK_END = LocalDate.of(2026, 5, 11);

    private FakeReceiptRepository receiptRepository;
    private FakeChurchRepository churchRepository;
    private FakeReceiptNumberGeneratorService receiptNumberGeneratorService;
    private FakeReceiptPrintService receiptPrintService;
    private FakeActivityLogService activityLogService;
    private FakeDataSource dataSource;
    private FakeSystemConfigurationCache configurationCache;
    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        receiptRepository = new FakeReceiptRepository();
        churchRepository = new FakeChurchRepository();
        receiptNumberGeneratorService = new FakeReceiptNumberGeneratorService();
        receiptPrintService = new FakeReceiptPrintService(receiptRepository);
        activityLogService = new FakeActivityLogService();
        dataSource = new FakeDataSource();
        configurationCache = new FakeSystemConfigurationCache();
        receiptService = new ReceiptService(receiptRepository, churchRepository, receiptNumberGeneratorService,
                null, null, activityLogService, fixedClock(), dataSource, configurationCache);
        AuthContext.setCurrentUser(new AuthenticatedUser(7L, "admin", "System Administrator", 1L,
                "Admin", List.of("receipt.create")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createValidReceiptForCurrentSubmissionWeek() {
        ReceiptResponseDto response = receiptService.createReceipt(validRequest());

        assertEquals("REC26000001", response.getReceiptNo());
        assertEquals(1, receiptRepository.insertedReceipts.size());
        assertEquals(2, receiptRepository.insertedItems.size());
        assertFalse(receiptRepository.insertedReceipts.getFirst().isLateSubmission());
        assertTrue(dataSource.connection.committed);
        assertEquals(ActivityLogService.RECEIPT_CREATED, activityLogService.createdAction);
    }

    @Test
    void createReceiptPrintsOriginalAfterSaveWhenPrintServiceIsAvailable() {
        receiptService = new ReceiptService(receiptRepository, churchRepository, receiptNumberGeneratorService,
                receiptPrintService, null, activityLogService, fixedClock(), dataSource, configurationCache);

        ReceiptResponseDto response = receiptService.createReceipt(validRequest());

        assertEquals(100L, receiptPrintService.printedReceiptId);
        assertTrue(response.isOriginalPrinted());
        assertEquals(1, response.getPrintAttemptCount());
    }

    @Test
    void createReceiptRemainsSavedWhenAutomaticPrintFails() {
        receiptPrintService.failPrint = true;
        receiptService = new ReceiptService(receiptRepository, churchRepository, receiptNumberGeneratorService,
                receiptPrintService, null, activityLogService, fixedClock(), dataSource, configurationCache);

        ReceiptResponseDto response = receiptService.createReceipt(validRequest());

        assertEquals("REC26000001", response.getReceiptNo());
        assertEquals(1, receiptRepository.insertedReceipts.size());
        assertFalse(response.isOriginalPrinted());
        assertEquals(1, response.getPrintAttemptCount());
    }

    @Test
    void createReceiptRemainsSavedWhenSmsNotificationFails() {
        FakeReceiptSmsNotificationService smsNotificationService = new FakeReceiptSmsNotificationService();
        smsNotificationService.fail = true;
        receiptService = new ReceiptService(receiptRepository, churchRepository, receiptNumberGeneratorService,
                receiptPrintService, smsNotificationService, activityLogService, fixedClock(), dataSource,
                configurationCache);

        ReceiptResponseDto response = receiptService.createReceipt(validRequest());

        assertEquals("REC26000001", response.getReceiptNo());
        assertEquals(1, receiptRepository.insertedReceipts.size());
        assertTrue(dataSource.connection.committed);
        assertFalse(dataSource.connection.rolledBack);
        assertEquals(100L, smsNotificationService.receiptId);
        assertEquals("Receipt saved, but SMS notification failed.", response.getWarningMessage());
    }

    @Test
    void createReceiptWithoutPrintStillSendsSmsNotification() {
        FakeReceiptSmsNotificationService smsNotificationService = new FakeReceiptSmsNotificationService();
        receiptService = new ReceiptService(receiptRepository, churchRepository, receiptNumberGeneratorService,
                receiptPrintService, smsNotificationService, activityLogService, fixedClock(), dataSource,
                configurationCache);

        ReceiptResponseDto response = receiptService.createReceipt(validRequest(), false);

        assertEquals("REC26000001", response.getReceiptNo());
        assertEquals(0L, receiptPrintService.printedReceiptId);
        assertFalse(response.isOriginalPrinted());
        assertEquals(100L, smsNotificationService.receiptId);
    }

    @Test
    void rejectFutureWeek() {
        CreateReceiptRequest request = validRequest();
        request.setWeekStartDate(LocalDate.of(2026, 5, 19));
        request.setWeekEndDate(LocalDate.of(2026, 5, 25));

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Future weeks are not allowed."));
        assertEquals(0, receiptNumberGeneratorService.generateCount);
    }

    @Test
    void rejectWeekEndThatIsNotMonday() {
        CreateReceiptRequest request = validRequest();
        request.setWeekEndDate(LocalDate.of(2026, 5, 17));
        request.setWeekStartDate(LocalDate.of(2026, 5, 11));

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Week end date must be a Monday."));
    }

    @Test
    void rejectWeekStartThatIsNotTuesday() {
        CreateReceiptRequest request = validRequest();
        request.setWeekStartDate(LocalDate.of(2026, 5, 13));

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Week start date must be a Tuesday."));
    }

    @Test
    void rejectWeekEndNotEqualToStartPlusSixDays() {
        CreateReceiptRequest request = validRequest();
        request.setWeekStartDate(LocalDate.of(2026, 5, 5));
        request.setWeekEndDate(LocalDate.of(2026, 5, 18));

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Week end date must be 6 days after week start date."));
    }

    @Test
    void rejectChurchServiceDateOutsideWeek() {
        CreateReceiptRequest request = validRequest();
        request.setChurchServiceDate(CURRENT_WEEK_START.minusDays(1));

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Date of the church service must be within the selected week."));
    }

    @Test
    void rejectMissingSubmittedByName() {
        CreateReceiptRequest request = validRequest();
        request.setSubmittedByName(" ");

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Submitted by name is required."));
    }

    @Test
    void rejectReceiptWithoutItems() {
        CreateReceiptRequest request = validRequest();
        request.setItems(List.of());

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("At least one collection item is required."));
    }

    @Test
    void rejectReceiptForChurchInInactiveRegion() {
        churchRepository.regionStatus = Region.Status.INACTIVE;

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(validRequest()));

        assertTrue(exception.getMessage().contains("inactive region"));
        assertEquals(0, receiptRepository.insertedReceipts.size());
    }

    @Test
    void rejectDuplicateCollectionTypes() {
        CreateReceiptRequest request = validRequest();
        request.setItems(List.of(
                item(CollectionType.OFFERTORY, "100.00"),
                item(CollectionType.OFFERTORY, "50.00")
        ));

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Duplicate collection type is not allowed."));
    }

    @Test
    void rejectZeroAmount() {
        CreateReceiptRequest request = validRequest();
        request.setItems(List.of(item(CollectionType.OFFERTORY, "0.00")));

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Amount must be greater than zero."));
    }

    @Test
    void rejectNegativeAmount() {
        CreateReceiptRequest request = validRequest();
        request.setItems(List.of(item(CollectionType.OFFERTORY, "-1.00")));

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Amount must be greater than zero."));
    }

    @Test
    void rejectCreatingSecondActiveReceiptForSameChurchAndWeek() {
        receiptRepository.existingActiveReceipt = true;

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(validRequest()));

        assertTrue(exception.getMessage().contains("An active receipt already exists"));
        assertTrue(dataSource.connection.rolledBack);
        assertEquals(0, receiptNumberGeneratorService.generateCount);
    }

    @Test
    void rejectActiveReceiptBeforeConfirmation() {
        receiptRepository.existingActiveReceiptForExistsCheck = true;

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.validateReceiptBeforeConfirmation(validRequest()));

        assertTrue(exception.getMessage().contains("An active receipt already exists"));
        assertEquals(0, receiptNumberGeneratorService.generateCount);
        assertFalse(dataSource.connection.committed);
    }

    @Test
    void allowBackWeekReceiptAndMarkAsLate() {
        CreateReceiptRequest request = validRequest();
        request.setWeekStartDate(BACK_WEEK_START);
        request.setWeekEndDate(BACK_WEEK_END);
        request.setChurchServiceDate(BACK_WEEK_END);
        request.setLateSubmissionReason("Submitted after treasurer travel.");

        ReceiptResponseDto response = receiptService.createReceipt(request);

        assertTrue(response.isLateSubmission());
        assertEquals("Submitted after treasurer travel.", response.getLateSubmissionReason());
    }

    @Test
    void allowBackWeekReceiptWithoutLateSubmissionReasonByDefault() {
        CreateReceiptRequest request = validRequest();
        request.setWeekStartDate(BACK_WEEK_START);
        request.setWeekEndDate(BACK_WEEK_END);
        request.setChurchServiceDate(BACK_WEEK_END);

        ReceiptResponseDto response = receiptService.createReceipt(request);

        assertTrue(response.isLateSubmission());
        assertEquals(null, response.getLateSubmissionReason());
    }

    @Test
    void rejectBackWeekReceiptWithoutLateSubmissionReasonWhenSettingIsEnabled() {
        configurationCache.put("receipt.late.reason.required", "true");
        CreateReceiptRequest request = validRequest();
        request.setWeekStartDate(BACK_WEEK_START);
        request.setWeekEndDate(BACK_WEEK_END);

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("Late submission reason is required for back week receipts."));
    }

    @Test
    void generateReceiptNumberOnlyAfterValidationPasses() {
        CreateReceiptRequest request = validRequest();
        request.setItems(List.of(item(CollectionType.OFFERTORY, "0")));

        assertThrows(ReceiptService.ReceiptException.class, () -> receiptService.createReceipt(request));

        assertEquals(0, receiptNumberGeneratorService.generateCount);
        assertEquals(0, receiptRepository.insertedReceipts.size());
    }

    @Test
    void rollbackReceiptNumberAndReceiptInsertIfItemInsertFails() {
        receiptRepository.failItemInsert = true;

        assertThrows(ReceiptService.ReceiptException.class, () -> receiptService.createReceipt(validRequest()));

        assertEquals(1, receiptNumberGeneratorService.generateCount);
        assertTrue(dataSource.connection.rolledBack);
        assertFalse(dataSource.connection.committed);
    }

    @Test
    void allowCorrectedReceiptAfterCancellation() {
        Receipt cancelledReceipt = cancelledReceiptForCorrection();
        receiptRepository.correctionReceipt = cancelledReceipt;
        CreateReceiptRequest request = validRequest();
        request.setCorrectedFromReceiptId(cancelledReceipt.getId());

        ReceiptResponseDto response = receiptService.createReceipt(request);

        assertEquals(cancelledReceipt.getId(), receiptRepository.insertedReceipts.getFirst().getCorrectedFromReceiptId());
        assertEquals(cancelledReceipt.getId(), response.getCorrectedFromReceiptId());
        assertEquals("REC26000001", response.getReceiptNo());
        assertEquals(ActivityLogService.CORRECTED_RECEIPT_CREATED, activityLogService.createdAction);
    }

    @Test
    void rejectCorrectedReceiptThatIsNotCancelled() {
        Receipt activeReceipt = cancelledReceiptForCorrection();
        activeReceipt.setStatus(ReceiptStatus.ACTIVE);
        receiptRepository.correctionReceipt = activeReceipt;
        CreateReceiptRequest request = validRequest();
        request.setCorrectedFromReceiptId(activeReceipt.getId());

        ReceiptService.ReceiptException exception = assertThrows(ReceiptService.ReceiptException.class,
                () -> receiptService.createReceipt(request));

        assertTrue(exception.getMessage().contains("must be cancelled"));
        assertEquals(0, receiptRepository.insertedReceipts.size());
    }

    @Test
    void receiptNumberIsNotReusedForCorrectedReceipt() {
        ReceiptResponseDto original = receiptService.createReceipt(validRequest());
        receiptRepository.insertedItems.clear();
        receiptRepository.correctionReceipt = cancelledReceiptForCorrection();
        CreateReceiptRequest correction = validRequest();
        correction.setCorrectedFromReceiptId(receiptRepository.correctionReceipt.getId());

        ReceiptResponseDto corrected = receiptService.createReceipt(correction);

        assertEquals("REC26000001", original.getReceiptNo());
        assertEquals("REC26000002", corrected.getReceiptNo());
        assertFalse(original.getReceiptNo().equals(corrected.getReceiptNo()));
    }

    private CreateReceiptRequest validRequest() {
        CreateReceiptRequest request = new CreateReceiptRequest();
        request.setChurchId(10L);
        request.setWeekStartDate(CURRENT_WEEK_START);
        request.setWeekEndDate(CURRENT_WEEK_END);
        request.setChurchServiceDate(CURRENT_WEEK_END);
        request.setSubmittedByName("Treasurer");
        request.setItems(List.of(
                item(CollectionType.OFFERTORY, "100.00"),
                item(CollectionType.TITHES, "250.00")
        ));
        return request;
    }

    private ReceiptItemDto item(CollectionType type, String amount) {
        return new ReceiptItemDto(type, new BigDecimal(amount), null);
    }

    private Receipt cancelledReceiptForCorrection() {
        Receipt receipt = new Receipt();
        receipt.setId(50L);
        receipt.setReceiptNo("REC26000000");
        receipt.setChurchId(10L);
        receipt.setRegionId(2L);
        receipt.setWeekStartDate(CURRENT_WEEK_START);
        receipt.setWeekEndDate(CURRENT_WEEK_END);
        receipt.setStatus(ReceiptStatus.CANCELLED);
        receipt.setCreatedAt(LocalDateTime.now());
        return receipt;
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-18T09:00:00Z"), ZoneId.of("UTC"));
    }

    private static class FakeReceiptRepository extends ReceiptRepository {
        private boolean existingActiveReceipt;
        private boolean existingActiveReceiptForExistsCheck;
        private boolean failItemInsert;
        private Receipt correctionReceipt;
        private boolean autoPrinted;
        private int autoPrintAttemptCount;
        private final List<Receipt> insertedReceipts = new ArrayList<>();
        private final List<ReceiptItem> insertedItems = new ArrayList<>();
        private Receipt lastReceipt;

        private FakeReceiptRepository() {
            super((DataSource) null);
        }

        @Override
        public boolean existsActiveReceiptForChurchAndWeek(long churchId, LocalDate weekStartDate) {
            return existingActiveReceiptForExistsCheck;
        }

        @Override
        public Optional<Receipt> findActiveReceiptForChurchAndWeekForUpdate(long churchId, LocalDate weekStartDate,
                                                                            Connection connection) {
            return existingActiveReceipt ? Optional.of(new Receipt()) : Optional.empty();
        }

        @Override
        public Optional<Receipt> findReceiptByIdForUpdate(long receiptId, Connection connection) {
            return correctionReceipt != null && correctionReceipt.getId().equals(receiptId)
                    ? Optional.of(correctionReceipt)
                    : Optional.empty();
        }

        @Override
        public long insertReceipt(Receipt receipt, Connection connection) {
            receipt.setId(100L);
            insertedReceipts.add(receipt);
            lastReceipt = receipt;
            return 100L;
        }

        @Override
        public void insertReceiptItem(long receiptId, ReceiptItem item, Connection connection) {
            if (failItemInsert) {
                throw new RuntimeException("Item insert failed");
            }
            item.setReceiptId(receiptId);
            insertedItems.add(item);
        }

        @Override
        public Optional<ReceiptResponseDto> findReceiptDetailsById(long receiptId) {
            ReceiptResponseDto response = new ReceiptResponseDto();
            response.setId(receiptId);
            response.setReceiptNo(lastReceipt.getReceiptNo());
            response.setChurchCode("CH010");
            response.setChurchName("Central Church");
            response.setRegionCode("REG001");
            response.setRegionName("North");
            response.setWeekStartDate(lastReceipt.getWeekStartDate());
            response.setWeekEndDate(lastReceipt.getWeekEndDate());
            response.setReceiptDateTime(lastReceipt.getReceiptDateTime());
            response.setSubmittedByName(lastReceipt.getSubmittedByName());
            response.setIssuedByFullName("System Administrator");
            response.setStatus(lastReceipt.getStatus());
            response.setLateSubmission(lastReceipt.isLateSubmission());
            response.setLateSubmissionReason(lastReceipt.getLateSubmissionReason());
            response.setCorrectedFromReceiptId(lastReceipt.getCorrectedFromReceiptId());
            response.setCorrectedFromReceiptNo(lastReceipt.getCorrectedFromReceiptId() == null ? null : "REC26000000");
            response.setOriginalPrinted(autoPrinted);
            response.setPrintAttemptCount(autoPrintAttemptCount);
            response.setItems(insertedItems.stream()
                    .map(item -> new ReceiptItemDto(item.getCollectionType(), item.getAmount(), item.getNote()))
                    .toList());
            response.setTotalAmount(insertedItems.stream()
                    .map(ReceiptItem::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            return Optional.of(response);
        }
    }

    private static class FakeChurchRepository extends ChurchRepository {
        private Region.Status regionStatus = Region.Status.ACTIVE;

        private FakeChurchRepository() {
            super((DataSource) null);
        }

        @Override
        public Optional<Church> findById(long id) {
            Church church = new Church(id, "CH010", "Central Church", 2L, "REG001", "North",
                    Church.Status.ACTIVE, LocalDateTime.now(), null);
            church.setRegionStatus(regionStatus);
            return Optional.of(church);
        }
    }

    private static class FakeReceiptNumberGeneratorService extends ReceiptNumberGeneratorService {
        private int generateCount;

        private FakeReceiptNumberGeneratorService() {
            super(null, null, new ReceiptNumberFormatter(), Clock.systemUTC());
        }

        @Override
        public String generateReceiptNumber(Connection connection) {
            generateCount++;
            return "REC2600000" + generateCount;
        }
    }

    private static class FakeReceiptPrintService extends ReceiptPrintService {
        private final FakeReceiptRepository receiptRepository;
        private boolean failPrint;
        private long printedReceiptId;

        private FakeReceiptPrintService(FakeReceiptRepository receiptRepository) {
            super(null, null, null, null, null, Clock.systemUTC());
            this.receiptRepository = receiptRepository;
        }

        @Override
        public void printOriginalReceipt(long receiptId) {
            printedReceiptId = receiptId;
            if (failPrint) {
                receiptRepository.autoPrintAttemptCount++;
                throw new ReceiptPrintException("Printer offline");
            }
            receiptRepository.autoPrintAttemptCount++;
            receiptRepository.autoPrinted = true;
        }
    }

    private static class FakeReceiptSmsNotificationService extends ReceiptSmsNotificationService {
        private boolean fail;
        private long receiptId;

        private FakeReceiptSmsNotificationService() {
            super(null, null, null, null, null, null, Clock.systemUTC());
        }

        @Override
        public void sendReceiptSubmissionSms(long receiptId) {
            this.receiptId = receiptId;
            if (fail) {
                throw new SmsNotificationException("SMS gateway unavailable");
            }
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String createdAction;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logReceiptCreated(Long userId, ReceiptResponseDto receipt, Long churchId, BigDecimal totalAmount) {
            createdAction = receipt.getCorrectedFromReceiptId() == null ? RECEIPT_CREATED : CORRECTED_RECEIPT_CREATED;
        }

        @Override
        public void logReceiptCreateFailed(Long userId, CreateReceiptRequest request, Church church,
                                           BigDecimal totalAmount, boolean lateSubmission, String reason) {
        }
    }

    private static class FakeSystemConfigurationCache extends SystemConfigurationCache {
        private final java.util.Map<String, String> values = new java.util.HashMap<>();

        private FakeSystemConfigurationCache() {
            super(new SystemSettingRepository((DataSource) null));
            values.put("receipt.allow.back.week", "true");
            values.put("receipt.late.reason.required", "false");
        }

        @Override
        public String getString(String key) {
            return values.get(key);
        }

        private void put(String key, String value) {
            values.put(key, value);
        }
    }

    private static class FakeDataSource implements DataSource {
        private final FakeConnection connection = new FakeConnection();

        @Override
        public Connection getConnection() {
            return connection.proxy();
        }

        @Override
        public Connection getConnection(String username, String password) {
            return connection.proxy();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not wrapped");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    private static class FakeConnection {
        private boolean autoCommit = true;
        private boolean committed;
        private boolean rolledBack;

        private Connection proxy() {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getAutoCommit" -> autoCommit;
                        case "setAutoCommit" -> {
                            autoCommit = (boolean) args[0];
                            yield null;
                        }
                        case "commit" -> {
                            committed = true;
                            yield null;
                        }
                        case "rollback" -> {
                            rolledBack = true;
                            yield null;
                        }
                        case "close" -> null;
                        case "isClosed" -> false;
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            return null;
        }
    }
}
