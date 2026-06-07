package com.churchmanagement.service;

import com.churchmanagement.entity.Receipt;
import com.churchmanagement.dto.PrintResult;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.reports.ReceiptPdfGenerator;
import com.churchmanagement.repository.ReceiptPrintRepository;
import com.churchmanagement.repository.ReceiptPrintRepository.PrintStatus;
import com.churchmanagement.repository.ReceiptPrintRepository.PrintType;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import net.sf.jasperreports.engine.JasperCompileManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.InputStream;
import java.lang.reflect.Proxy;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptPrintServiceTest {
    private FakeReceiptPdfGenerator pdfGenerator;
    private FakeReceiptPrintRepository printRepository;
    private FakeActivityLogService activityLogService;
    private FakePrinterService printerService;
    private FakeDataSource dataSource;
    private ReceiptPrintService service;

    @BeforeEach
    void setUp() {
        pdfGenerator = new FakeReceiptPdfGenerator();
        printRepository = new FakeReceiptPrintRepository();
        activityLogService = new FakeActivityLogService();
        printerService = new FakePrinterService();
        dataSource = new FakeDataSource();
        service = new ReceiptPrintService(pdfGenerator, printRepository, activityLogService, printerService,
                dataSource, fixedClock());
        AuthContext.setCurrentUser(adminUser());
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void generatePdfForActiveReceipt() {
        String path = service.generatePdf(100L);

        assertEquals("./receipts/REC26000001.pdf", path);
        assertEquals(100L, pdfGenerator.generatedReceiptId);
        assertEquals(ActivityLogService.RECEIPT_PDF_GENERATED, activityLogService.actions.getFirst());
    }

    @Test
    void markOriginalPrintedAfterSuccessfulPrint() {
        service.printOriginalReceipt(100L);

        assertTrue(printRepository.receipt.isOriginalPrinted());
        assertEquals(7L, printRepository.receipt.getOriginalPrintedByUserId());
        assertEquals(1, printRepository.successLogs);
        assertEquals(ActivityLogService.RECEIPT_ORIGINAL_PRINTED, activityLogService.actions.getLast());
    }

    @Test
    void rejectSecondOriginalPrint() {
        printRepository.receipt.setOriginalPrinted(true);

        ReceiptPrintService.ReceiptPrintException exception = assertThrows(
                ReceiptPrintService.ReceiptPrintException.class,
                () -> service.printOriginalReceipt(100L));

        assertEquals("Original receipt has already been printed.", exception.getMessage());
        assertEquals(0, printerService.printCount);
        assertTrue(activityLogService.actions.contains(ActivityLogService.RECEIPT_PRINT_BLOCKED_ALREADY_PRINTED));
    }

    @Test
    void rejectOriginalPrintForCancelledReceipt() {
        printRepository.receipt.setStatus(ReceiptStatus.CANCELLED);

        ReceiptPrintService.ReceiptPrintException exception = assertThrows(
                ReceiptPrintService.ReceiptPrintException.class,
                () -> service.printOriginalReceipt(100L));

        assertEquals("Cancelled receipts cannot be printed as original.", exception.getMessage());
        assertEquals(0, printerService.printCount);
        assertTrue(activityLogService.actions.contains(ActivityLogService.RECEIPT_PRINT_BLOCKED_CANCELLED));
    }

    @Test
    void doNotMarkOriginalPrintedIfPrintFails() {
        printerService.failPrint = true;

        assertThrows(ReceiptPrintService.ReceiptPrintException.class, () -> service.printOriginalReceipt(100L));

        assertFalse(printRepository.receipt.isOriginalPrinted());
        assertEquals(1, printRepository.failureLogs);
        assertEquals(1, printRepository.receipt.getPrintAttemptCount());
    }

    @Test
    void insertPrintLogOnSuccess() {
        service.printOriginalReceipt(100L);

        assertEquals(PrintType.ORIGINAL, printRepository.lastPrintType);
        assertEquals(PrintStatus.SUCCESS, printRepository.lastPrintStatus);
    }

    @Test
    void insertPrintLogOnFailure() {
        printerService.failPrint = true;

        assertThrows(ReceiptPrintService.ReceiptPrintException.class, () -> service.printOriginalReceipt(100L));

        assertEquals(PrintType.ORIGINAL, printRepository.lastPrintType);
        assertEquals(PrintStatus.FAILED, printRepository.lastPrintStatus);
        assertEquals("Printer offline", printRepository.lastErrorMessage);
    }

    @Test
    void userWithoutReceiptPrintCannotPrint() {
        AuthContext.setCurrentUser(new AuthenticatedUser(8L, "user", "Standard User", 2L,
                "User", List.of("receipt.create")));

        ReceiptPrintService.ReceiptPrintException exception = assertThrows(
                ReceiptPrintService.ReceiptPrintException.class,
                () -> service.printOriginalReceipt(100L));

        assertEquals("You do not have permission to print receipts.", exception.getMessage());
        assertEquals(0, printerService.printCount);
    }

    @Test
    void receiptTemplateCompiles() throws Exception {
        try (InputStream inputStream = ReceiptPrintServiceTest.class.getResourceAsStream("/reports/receipt_template.jrxml")) {
            assertNotNull(inputStream);
            assertNotNull(JasperCompileManager.compileReport(inputStream));
        }
    }

    private AuthenticatedUser adminUser() {
        return new AuthenticatedUser(7L, "admin", "System Administrator", 1L,
                "Admin", List.of("receipt.print"));
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-18T09:00:00Z"), ZoneId.of("UTC"));
    }

    private static class FakeReceiptPdfGenerator extends ReceiptPdfGenerator {
        private long generatedReceiptId;

        private FakeReceiptPdfGenerator() {
            super(null, null, null, Clock.systemUTC());
        }

        @Override
        public String generateReceiptPdf(long receiptId) {
            generatedReceiptId = receiptId;
            return "./receipts/REC26000001.pdf";
        }
    }

    private static class FakeReceiptPrintRepository extends ReceiptPrintRepository {
        private final Receipt receipt = activeReceipt();
        private int successLogs;
        private int failureLogs;
        private boolean pendingPrintAttempt;
        private PrintType lastPrintType;
        private PrintStatus lastPrintStatus;
        private String lastErrorMessage;

        @Override
        public Optional<Receipt> lockReceiptForPrint(long receiptId, Connection connection) {
            return receipt.getId().equals(receiptId) ? Optional.of(receipt) : Optional.empty();
        }

        @Override
        public void markOriginalPrinted(long receiptId, long userId, Connection connection) {
            receipt.setOriginalPrinted(true);
            receipt.setOriginalPrintedByUserId(userId);
        }

        @Override
        public void incrementPrintAttempt(long receiptId, Connection connection) {
            pendingPrintAttempt = true;
        }

        @Override
        public void insertPrintLog(long receiptId, long printedByUserId, PrintType printType, PrintStatus status,
                                   String errorMessage, LocalDateTime printedAt, Connection connection) {
            lastPrintType = printType;
            lastPrintStatus = status;
            lastErrorMessage = errorMessage;
            if (pendingPrintAttempt) {
                receipt.setPrintAttemptCount(receipt.getPrintAttemptCount() + 1);
                pendingPrintAttempt = false;
            }
            if (status == PrintStatus.SUCCESS) {
                successLogs++;
            } else {
                failureLogs++;
            }
        }

        private static Receipt activeReceipt() {
            Receipt receipt = new Receipt();
            receipt.setId(100L);
            receipt.setReceiptNo("REC26000001");
            receipt.setChurchId(10L);
            receipt.setRegionId(2L);
            receipt.setWeekStartDate(LocalDate.of(2026, 5, 11));
            receipt.setWeekEndDate(LocalDate.of(2026, 5, 17));
            receipt.setReceiptDateTime(LocalDateTime.of(2026, 5, 18, 9, 0));
            receipt.setSubmittedByName("Treasurer");
            receipt.setIssuedByUserId(7L);
            receipt.setStatus(ReceiptStatus.ACTIVE);
            receipt.setPdfFilePath("./receipts/REC26000001.pdf");
            receipt.setCreatedAt(LocalDateTime.of(2026, 5, 18, 9, 0));
            return receipt;
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private final List<String> actions = new ArrayList<>();

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logReceiptPdfGenerated(Long userId, long receiptId, String pdfPath) {
            actions.add(RECEIPT_PDF_GENERATED);
        }

        @Override
        public void logReceiptOriginalPrinted(Long userId, long receiptId) {
            actions.add(RECEIPT_ORIGINAL_PRINTED);
        }

        @Override
        public void logReceiptPrintFailed(Long userId, long receiptId, String reason) {
            actions.add(RECEIPT_PRINT_FAILED);
        }

        @Override
        public void logReceiptPrintBlockedAlreadyPrinted(Long userId, long receiptId) {
            actions.add(RECEIPT_PRINT_BLOCKED_ALREADY_PRINTED);
        }

        @Override
        public void logReceiptPrintBlockedCancelled(Long userId, long receiptId) {
            actions.add(RECEIPT_PRINT_BLOCKED_CANCELLED);
        }
    }

    private static class FakePrinterService implements PrinterService {
        private boolean failPrint;
        private int printCount;

        @Override
        public PrintResult printPdf(String pdfPath) {
            printCount++;
            if (failPrint) {
                return new PrintResult(false, "Printer offline", "Test Printer",
                        LocalDateTime.of(2026, 5, 18, 9, 0));
            }
            return new PrintResult(true, "Printed", "Test Printer",
                    LocalDateTime.of(2026, 5, 18, 9, 0));
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
                        case "commit", "rollback", "close" -> null;
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
