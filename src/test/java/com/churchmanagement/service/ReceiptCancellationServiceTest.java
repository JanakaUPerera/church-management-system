package com.churchmanagement.service;

import com.churchmanagement.dto.CancelReceiptRequest;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.entity.Receipt;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.repository.ReceiptCancellationRepository;
import com.churchmanagement.repository.ReceiptRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptCancellationServiceTest {
    private FakeReceiptRepository receiptRepository;
    private FakeReceiptCancellationRepository cancellationRepository;
    private FakeActivityLogService activityLogService;
    private FakeDataSource dataSource;
    private ReceiptCancellationService service;

    @BeforeEach
    void setUp() {
        receiptRepository = new FakeReceiptRepository();
        cancellationRepository = new FakeReceiptCancellationRepository();
        activityLogService = new FakeActivityLogService();
        dataSource = new FakeDataSource();
        service = new ReceiptCancellationService(receiptRepository, cancellationRepository, activityLogService,
                fixedClock(), dataSource);
        AuthContext.setCurrentUser(new AuthenticatedUser(7L, "admin", "System Administrator", 1L,
                "Admin", List.of("RECEIPT_CANCEL")));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void cancelActiveReceipt() {
        ReceiptResponseDto response = service.cancelReceipt(request("Duplicate entry."));

        assertEquals(ReceiptStatus.CANCELLED, response.getStatus());
        assertEquals(ReceiptStatus.CANCELLED, receiptRepository.receipt.getStatus());
        assertEquals("Duplicate entry.", cancellationRepository.cancelReason);
        assertTrue(dataSource.connection.committed);
        assertEquals(ActivityLogService.RECEIPT_CANCELLED, activityLogService.loggedAction);
    }

    @Test
    void rejectCancelWithoutReason() {
        ReceiptCancellationService.ReceiptCancellationException exception = assertThrows(
                ReceiptCancellationService.ReceiptCancellationException.class,
                () -> service.cancelReceipt(request(" ")));

        assertTrue(exception.getMessage().contains("Cancellation reason is required"));
        assertFalse(dataSource.connection.committed);
    }

    @Test
    void rejectCancellingAlreadyCancelledReceipt() {
        receiptRepository.receipt.setStatus(ReceiptStatus.CANCELLED);

        ReceiptCancellationService.ReceiptCancellationException exception = assertThrows(
                ReceiptCancellationService.ReceiptCancellationException.class,
                () -> service.cancelReceipt(request("Wrong amount.")));

        assertTrue(exception.getMessage().contains("already cancelled"));
        assertTrue(dataSource.connection.rolledBack);
        assertEquals(ReceiptStatus.CANCELLED, receiptRepository.receipt.getStatus());
    }

    @Test
    void rejectCancellingReceiptAfterOneWeek() {
        receiptRepository.receipt.setReceiptDateTime(LocalDateTime.of(2026, 5, 10, 8, 0));

        ReceiptCancellationService.ReceiptCancellationException exception = assertThrows(
                ReceiptCancellationService.ReceiptCancellationException.class,
                () -> service.cancelReceipt(request("Too late.")));

        assertTrue(exception.getMessage().contains("within one week"));
        assertTrue(dataSource.connection.rolledBack);
        assertEquals(ReceiptStatus.ACTIVE, receiptRepository.receipt.getStatus());
    }

    private CancelReceiptRequest request(String reason) {
        CancelReceiptRequest request = new CancelReceiptRequest();
        request.setReceiptId(100L);
        request.setCancelReason(reason);
        return request;
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-05-18T09:00:00Z"), ZoneId.of("UTC"));
    }

    private static class FakeReceiptRepository extends ReceiptRepository {
        private final Receipt receipt = activeReceipt();

        private FakeReceiptRepository() {
            super((DataSource) null);
        }

        @Override
        public Optional<Receipt> findReceiptByIdForUpdate(long receiptId, Connection connection) {
            return receipt.getId().equals(receiptId) ? Optional.of(receipt) : Optional.empty();
        }

        @Override
        public void updateReceiptStatus(long receiptId, ReceiptStatus status, LocalDateTime updatedAt,
                                        Connection connection) {
            receipt.setStatus(status);
            receipt.setUpdatedAt(updatedAt);
        }

        @Override
        public Optional<ReceiptResponseDto> findReceiptDetailsById(long receiptId, Connection connection) {
            ReceiptResponseDto response = new ReceiptResponseDto();
            response.setId(receiptId);
            response.setReceiptNo(receipt.getReceiptNo());
            response.setChurchCode("CH010");
            response.setChurchName("Central Church");
            response.setWeekStartDate(receipt.getWeekStartDate());
            response.setWeekEndDate(receipt.getWeekEndDate());
            response.setStatus(receipt.getStatus());
            return Optional.of(response);
        }

        private static Receipt activeReceipt() {
            Receipt receipt = new Receipt();
            receipt.setId(100L);
            receipt.setReceiptNo("REC26000001");
            receipt.setChurchId(10L);
            receipt.setWeekStartDate(LocalDate.of(2026, 5, 11));
            receipt.setWeekEndDate(LocalDate.of(2026, 5, 17));
            receipt.setReceiptDateTime(LocalDateTime.of(2026, 5, 18, 8, 0));
            receipt.setCreatedAt(LocalDateTime.of(2026, 5, 18, 8, 0));
            receipt.setStatus(ReceiptStatus.ACTIVE);
            return receipt;
        }
    }

    private static class FakeReceiptCancellationRepository extends ReceiptCancellationRepository {
        private String cancelReason;

        private FakeReceiptCancellationRepository() {
            super(null);
        }

        @Override
        public void insertCancellation(long receiptId, long cancelledByUserId, String cancelReason,
                                       LocalDateTime cancelledAt, LocalDateTime createdAt, Connection connection) {
            this.cancelReason = cancelReason;
        }
    }

    private static class FakeActivityLogService extends ActivityLogService {
        private String loggedAction;

        private FakeActivityLogService() {
            super(null);
        }

        @Override
        public void logReceiptCancelled(Long userId, ReceiptResponseDto receipt, String cancelReason) {
            loggedAction = RECEIPT_CANCELLED;
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
