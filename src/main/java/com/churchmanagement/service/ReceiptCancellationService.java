package com.churchmanagement.service;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.CancelReceiptRequest;
import com.churchmanagement.dto.ReceiptResponseDto;
import com.churchmanagement.entity.Receipt;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.repository.ReceiptCancellationRepository;
import com.churchmanagement.repository.ReceiptRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

public class ReceiptCancellationService {
    private static final Duration CANCELLATION_WINDOW = Duration.ofDays(7);

    private final ReceiptRepository receiptRepository;
    private final ReceiptCancellationRepository receiptCancellationRepository;
    private final ActivityLogService activityLogService;
    private final Clock clock;
    private final DataSource dataSource;

    public ReceiptCancellationService() {
        this(new ReceiptRepository(), new ReceiptCancellationRepository(), new ActivityLogService(),
                Clock.systemDefaultZone(), DatabaseConfig.getDataSource());
    }

    public ReceiptCancellationService(ReceiptRepository receiptRepository,
                                      ReceiptCancellationRepository receiptCancellationRepository,
                                      ActivityLogService activityLogService, Clock clock, DataSource dataSource) {
        this.receiptRepository = receiptRepository;
        this.receiptCancellationRepository = receiptCancellationRepository;
        this.activityLogService = activityLogService;
        this.clock = clock;
        this.dataSource = dataSource;
    }

    public ReceiptResponseDto cancelReceipt(CancelReceiptRequest request) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new ReceiptCancellationException("Please sign in to cancel receipts."));
        new PermissionGuard(currentUser).require("receipt.cancel");
        validateRequest(request);

        Connection connection = null;
        boolean previousAutoCommit = true;
        try {
            connection = dataSource.getConnection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            Receipt receipt = receiptRepository.findReceiptByIdForUpdate(request.getReceiptId(), connection)
                    .orElseThrow(() -> new ReceiptCancellationException("Receipt could not be found."));
            if (receipt.getStatus() == ReceiptStatus.CANCELLED) {
                throw new ReceiptCancellationException("Receipt is already cancelled.");
            }

            LocalDateTime now = LocalDateTime.now(clock);
            validateCancellationWindow(receipt, now);
            String reason = request.getCancelReason().strip();
            receiptCancellationRepository.insertCancellation(receipt.getId(), currentUser.getUserId(), reason,
                    now, now, connection);
            receiptRepository.updateReceiptStatus(receipt.getId(), ReceiptStatus.CANCELLED, now, connection);

            ReceiptResponseDto response = receiptRepository.findReceiptDetailsById(receipt.getId(), connection)
                    .orElseThrow(() -> new ReceiptCancellationException("Receipt was cancelled but could not be loaded."));
            connection.commit();
            activityLogService.logReceiptCancelled(currentUser.getUserId(), response, reason);
            return response;
        } catch (ReceiptCancellationException exception) {
            rollback(connection);
            throw exception;
        } catch (SQLException | DatabaseException exception) {
            rollback(connection);
            throw new ReceiptCancellationException("Unable to cancel receipt right now. Please try again later.", exception);
        } catch (RuntimeException exception) {
            rollback(connection);
            throw new ReceiptCancellationException("Unable to cancel receipt right now. Please try again later.", exception);
        } finally {
            restoreAutoCommitAndClose(connection, previousAutoCommit);
        }
    }

    private void validateRequest(CancelReceiptRequest request) {
        if (request == null || request.getReceiptId() == null) {
            throw new ReceiptCancellationException("Receipt is required.");
        }
        if (request.getCancelReason() == null || request.getCancelReason().isBlank()) {
            throw new ReceiptCancellationException("Cancellation reason is required.");
        }
    }

    private void validateCancellationWindow(Receipt receipt, LocalDateTime now) {
        LocalDateTime referenceTime = receipt.getReceiptDateTime() == null
                ? receipt.getCreatedAt()
                : receipt.getReceiptDateTime();
        if (referenceTime == null || referenceTime.plus(CANCELLATION_WINDOW).isBefore(now)) {
            throw new ReceiptCancellationException("Receipt can only be cancelled within one week.");
        }
    }

    private void rollback(Connection connection) {
        if (connection == null) {
            return;
        }

        try {
            connection.rollback();
        } catch (SQLException exception) {
            System.err.println("Receipt cancellation rollback failed: " + exception.getMessage());
        }
    }

    private void restoreAutoCommitAndClose(Connection connection, boolean previousAutoCommit) {
        if (connection == null) {
            return;
        }

        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException exception) {
            System.err.println("Unable to restore receipt cancellation connection auto-commit: " + exception.getMessage());
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            System.err.println("Unable to close receipt cancellation connection: " + exception.getMessage());
        }
    }

    public static class ReceiptCancellationException extends RuntimeException {
        public ReceiptCancellationException(String message) {
            super(message);
        }

        public ReceiptCancellationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
