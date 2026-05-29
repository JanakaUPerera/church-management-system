package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

public class ReceiptCancellationRepository {
    private final DataSource dataSource;

    public ReceiptCancellationRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public ReceiptCancellationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insertCancellation(long receiptId, long cancelledByUserId, String cancelReason,
                                   LocalDateTime cancelledAt, LocalDateTime createdAt, Connection connection) {
        String sql = """
                INSERT INTO receipt_cancellations (
                    receipt_id, cancelled_by_user_id, cancel_reason, cancelled_at, created_at
                )
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, receiptId);
            statement.setLong(2, cancelledByUserId);
            statement.setString(3, cancelReason);
            statement.setTimestamp(4, Timestamp.valueOf(cancelledAt));
            statement.setTimestamp(5, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to insert receipt cancellation.", exception);
        }
    }
}
