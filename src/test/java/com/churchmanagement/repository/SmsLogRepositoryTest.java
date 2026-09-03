package com.churchmanagement.repository;

import com.churchmanagement.dto.SmsLogSearchCriteria;
import com.churchmanagement.enums.SmsDeliveryStatus;
import com.churchmanagement.enums.SmsSendStatus;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmsLogRepositoryTest {
    @Test
    void saveSentWithModemReference() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);

        repository.insertSmsLog(1L, 2L, "+94771234567", "Hello", "SIM Dongle",
                SmsSendStatus.SENT, SmsDeliveryStatus.PENDING, "45", "+CMGS: 45\r\nOK",
                null, null, null, 1, LocalDateTime.of(2026, 6, 6, 10, 0),
                LocalDateTime.of(2026, 6, 6, 10, 0), LocalDateTime.of(2026, 6, 6, 10, 0));

        assertEquals(SmsSendStatus.SENT.name(), dataSource.valueAt(9));
        assertEquals(SmsDeliveryStatus.PENDING.name(), dataSource.valueAt(10));
        assertEquals("45", dataSource.valueAt(7));
    }

    @Test
    void saveFailedWithErrorCode() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);

        repository.insertSmsLog(1L, 2L, "+94771234567", "Hello", "SIM Dongle",
                SmsSendStatus.FAILED, SmsDeliveryStatus.FAILED, null, "+CMS ERROR: 10",
                null, "10", "SIM card may not be inserted.", 1, LocalDateTime.of(2026, 6, 6, 10, 0),
                null, LocalDateTime.of(2026, 6, 6, 10, 0));

        assertEquals(SmsSendStatus.FAILED.name(), dataSource.valueAt(9));
        assertEquals(SmsDeliveryStatus.FAILED.name(), dataSource.valueAt(10));
        assertEquals("10", dataSource.valueAt(13));
    }

    @Test
    void saveDeliveryStatusUnknown() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);

        repository.insertSmsLog(1L, 2L, "+94771234567", "Hello", "Mock",
                SmsSendStatus.SENT, SmsDeliveryStatus.UNKNOWN, null, null, null, null,
                null, 1, LocalDateTime.of(2026, 6, 6, 10, 0),
                LocalDateTime.of(2026, 6, 6, 10, 0), LocalDateTime.of(2026, 6, 6, 10, 0));

        assertEquals(SmsDeliveryStatus.UNKNOWN.name(), dataSource.valueAt(10));
    }

    @Test
    void searchBySendStatus() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);
        SmsLogSearchCriteria criteria = new SmsLogSearchCriteria();
        criteria.setSendStatus(SmsSendStatus.FAILED);

        repository.searchSmsLogs(criteria);

        assertTrue(dataSource.sql.contains("AND sl.status = ?"));
        assertEquals(SmsSendStatus.FAILED.name(), dataSource.valueAt(1));
    }

    @Test
    void searchByDeliveryStatus() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);
        SmsLogSearchCriteria criteria = new SmsLogSearchCriteria();
        criteria.setDeliveryStatus(SmsDeliveryStatus.UNKNOWN);

        repository.searchSmsLogs(criteria);

        assertTrue(dataSource.sql.contains("AND sl.delivery_status = ?"));
        assertEquals(SmsDeliveryStatus.UNKNOWN.name(), dataSource.valueAt(1));
    }

    @Test
    void enqueueInsertsQueuedRowWithZeroAttempts() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);

        repository.enqueue(1L, 2L, "+94771234567", "Hello", 7L, LocalDateTime.of(2026, 6, 6, 10, 0));

        assertTrue(dataSource.sql.contains("INSERT INTO sms_logs"));
        assertEquals(SmsSendStatus.QUEUED.name(), dataSource.valueAt(6));
        assertEquals(SmsDeliveryStatus.UNKNOWN.name(), dataSource.valueAt(7));
        assertEquals(0, dataSource.valueAt(8));
        assertEquals(7L, dataSource.valueAt(9));
    }

    @Test
    void enqueueResendCarriesPriorAttemptCountAndLinkage() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);

        repository.enqueueResend(1L, 2L, "+94771234567", "Hello", 100L, 7L, "Asked by church office", 2,
                LocalDateTime.of(2026, 6, 6, 10, 0));

        assertEquals(2, dataSource.valueAt(8));
        assertEquals(7L, dataSource.valueAt(9));
        assertEquals(100L, dataSource.valueAt(11));
        assertEquals(7L, dataSource.valueAt(12));
        assertEquals("Asked by church office", dataSource.valueAt(13));
    }

    @Test
    void findOldestQueuedFiltersByStatus() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);

        repository.findOldestQueued();

        assertTrue(dataSource.sql.contains("WHERE sl.status = ? ORDER BY sl.created_at ASC LIMIT 1"));
        assertEquals(SmsSendStatus.QUEUED.name(), dataSource.valueAt(1));
    }

    @Test
    void markSendingClaimsQueuedRowOnly() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);

        boolean claimed = repository.markSending(5L, LocalDateTime.of(2026, 6, 6, 10, 0));

        assertTrue(dataSource.sql.contains("WHERE id = ? AND status = ?"));
        assertEquals(SmsSendStatus.SENDING.name(), dataSource.valueAt(1));
        assertEquals(SmsSendStatus.QUEUED.name(), dataSource.valueAt(4));
        assertTrue(claimed);
    }

    @Test
    void updateSendResultWritesFinalOutcome() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);

        repository.updateSendResult(5L, SmsSendStatus.SENT, SmsDeliveryStatus.UNKNOWN, "SIM Dongle", "45",
                "+CMGS: 45\r\nOK", null, null, 1, LocalDateTime.of(2026, 6, 6, 10, 0),
                LocalDateTime.of(2026, 6, 6, 10, 0));

        assertEquals(SmsSendStatus.SENT.name(), dataSource.valueAt(1));
        assertEquals("SIM Dongle", dataSource.valueAt(3));
        assertEquals("45", dataSource.valueAt(4));
    }

    @Test
    void reclaimStaleSendingResetsToQueued() {
        RecordingDataSource dataSource = new RecordingDataSource();
        SmsLogRepository repository = new SmsLogRepository(dataSource);

        repository.reclaimStaleSending(LocalDateTime.of(2026, 6, 6, 10, 0));

        assertTrue(dataSource.sql.contains("status = ? WHERE status = ? AND last_attempt_at < ?"));
        assertEquals(SmsSendStatus.QUEUED.name(), dataSource.valueAt(1));
        assertEquals(SmsSendStatus.SENDING.name(), dataSource.valueAt(2));
    }

    private static class RecordingDataSource implements DataSource {
        private String sql;
        private final List<Object> values = new ArrayList<>();

        private Object valueAt(int index) {
            return values.get(index - 1);
        }

        @Override
        public Connection getConnection() {
            return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if ("prepareStatement".equals(method.getName())) {
                            sql = (String) args[0];
                            return preparedStatementProxy();
                        }
                        if ("close".equals(method.getName())) {
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private PreparedStatement preparedStatementProxy() {
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {PreparedStatement.class}, (proxy, method, args) -> {
                        String methodName = method.getName();
                        if (methodName.startsWith("set") && args != null && args.length >= 2
                                && args[0] instanceof Integer index) {
                            while (values.size() < index) {
                                values.add(null);
                            }
                            values.set(index - 1, args[1]);
                            return null;
                        }
                        if ("executeUpdate".equals(methodName)) {
                            return 1;
                        }
                        if ("executeQuery".equals(methodName)) {
                            return resultSetProxy();
                        }
                        if ("close".equals(methodName)) {
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private ResultSet resultSetProxy() {
            return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ResultSet.class},
                    (proxy, method, args) -> "next".equals(method.getName()) ? false : defaultValue(method.getReturnType()));
        }

        private Object defaultValue(Class<?> type) {
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            return null;
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }
    }
}
