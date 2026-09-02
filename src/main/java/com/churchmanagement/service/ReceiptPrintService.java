package com.churchmanagement.service;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.PrintResult;
import com.churchmanagement.entity.Receipt;
import com.churchmanagement.enums.ReceiptStatus;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.reports.ReceiptPdfGenerator;
import com.churchmanagement.repository.ReceiptPrintRepository;
import com.churchmanagement.repository.ReceiptPrintRepository.PrintStatus;
import com.churchmanagement.repository.ReceiptPrintRepository.PrintType;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.security.PermissionGuard;
import net.sf.jasperreports.engine.JasperPrint;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;

public class ReceiptPrintService {
    private final ReceiptPdfGenerator receiptPdfGenerator;
    private final ReceiptPrintRepository receiptPrintRepository;
    private final ActivityLogService activityLogService;
    private final ReceiptPrinterService receiptPrinterService;
    private final DataSource dataSource;
    private final Clock clock;

    public ReceiptPrintService() {
        this(new ReceiptPdfGenerator(), new ReceiptPrintRepository(), new ActivityLogService(),
                new DotMatrixReceiptPrinterService(), DatabaseConfig.getDataSource(), Clock.systemDefaultZone());
    }

    public ReceiptPrintService(ReceiptPdfGenerator receiptPdfGenerator, ReceiptPrintRepository receiptPrintRepository,
                               ActivityLogService activityLogService, ReceiptPrinterService receiptPrinterService,
                               DataSource dataSource, Clock clock) {
        this.receiptPdfGenerator = receiptPdfGenerator;
        this.receiptPrintRepository = receiptPrintRepository;
        this.activityLogService = activityLogService;
        this.receiptPrinterService = receiptPrinterService;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    public String generatePdf(long receiptId) {
        try {
            String path = receiptPdfGenerator.generateReceiptPdf(receiptId);
            AuthContext.getCurrentUser()
                    .ifPresent(user -> activityLogService.logReceiptPdfGenerated(user.getUserId(), receiptId, path));
            return path;
        } catch (ReceiptPdfGenerator.ReceiptPdfException | DatabaseException exception) {
            throw new ReceiptPrintException("PDF generation failed.", exception);
        }
    }

    public void printOriginalReceipt(long receiptId) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new ReceiptPrintException("Please sign in to print receipts."));
        PermissionGuard permissionGuard = new PermissionGuard(currentUser);
        if (!permissionGuard.can("receipt.print")) {
            throw new ReceiptPrintException("You do not have permission to print receipts.");
        }

        attemptOriginalPrint(receiptId, currentUser);
    }

    /**
     * Sends the receipt straight to the physical printer for alignment/testing purposes only —
     * bypasses the cancelled/already-printed guard so it can be re-run freely, and makes no
     * database changes at all (no print-attempt count, no "original printed" flag, no print log
     * entry). Never treat this as producing an official original.
     *
     * @return the printer's result message — for {@link DotMatrixReceiptPrinterService} this
     * includes the validated page/imageable-area geometry actually used, so a clipped printout
     * can be diagnosed from what's shown after this action instead of guessing blind.
     */
    public String testPrint(long receiptId) {
        AuthenticatedUser currentUser = AuthContext.getCurrentUser()
                .orElseThrow(() -> new ReceiptPrintException("Please sign in to print receipts."));
        PermissionGuard permissionGuard = new PermissionGuard(currentUser);
        if (!permissionGuard.can("receipt.print")) {
            throw new ReceiptPrintException("You do not have permission to print receipts.");
        }

        JasperPrint printJob = receiptPdfGenerator.renderPrintJasperPrint(receiptId);
        PrintResult printResult = receiptPrinterService.print(printJob);
        if (!printResult.isSuccess()) {
            throw new ReceiptPrintException(printResult.getMessage());
        }
        return printResult.getMessage();
    }

    private void attemptOriginalPrint(long receiptId, AuthenticatedUser currentUser) {
        Connection connection = null;
        boolean previousAutoCommit = true;
        try {
            connection = dataSource.getConnection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            Receipt receipt = receiptPrintRepository.lockReceiptForPrint(receiptId, connection)
                    .orElseThrow(() -> new ReceiptPrintException("Receipt not found."));
            if (receipt.getStatus() == ReceiptStatus.CANCELLED) {
                activityLogService.logReceiptPrintBlockedCancelled(currentUser.getUserId(), receiptId);
                throw new ReceiptPrintException("Cancelled receipts cannot be printed as original.");
            }
            if (receipt.isOriginalPrinted()) {
                activityLogService.logReceiptPrintBlockedAlreadyPrinted(currentUser.getUserId(), receiptId);
                throw new ReceiptPrintException("Original receipt has already been printed.");
            }

            receiptPrintRepository.incrementPrintAttempt(receiptId, connection);
            JasperPrint printJob = receiptPdfGenerator.renderPrintJasperPrint(receiptId);
            PrintResult printResult = receiptPrinterService.print(printJob);
            if (!printResult.isSuccess()) {
                throw new IllegalStateException(printResult.getMessage());
            }
            receiptPrintRepository.markOriginalPrinted(receiptId, currentUser.getUserId(), connection);
            receiptPrintRepository.insertPrintLog(receiptId, currentUser.getUserId(), PrintType.ORIGINAL,
                    PrintStatus.SUCCESS, null, LocalDateTime.now(clock), connection);
            connection.commit();
            activityLogService.logReceiptOriginalPrinted(currentUser.getUserId(), receiptId);
        } catch (ReceiptPrintException exception) {
            rollback(connection);
            throw exception;
        } catch (SQLException | DatabaseException exception) {
            rollback(connection);
            throw new ReceiptPrintException("Unable to print receipt right now. Please try again later.", exception);
        } catch (RuntimeException exception) {
            recordPrintFailure(receiptId, currentUser, exception);
            throw new ReceiptPrintException(friendlyPrintFailure(exception), exception);
        } finally {
            restoreAutoCommitAndClose(connection, previousAutoCommit);
        }
    }

    private void recordPrintFailure(long receiptId, AuthenticatedUser currentUser, Exception printException) {
        try (Connection failureConnection = dataSource.getConnection()) {
            boolean previousAutoCommit = failureConnection.getAutoCommit();
            try {
                failureConnection.setAutoCommit(false);
                receiptPrintRepository.incrementPrintAttempt(receiptId, failureConnection);
                receiptPrintRepository.insertPrintLog(receiptId, currentUser.getUserId(), PrintType.ORIGINAL,
                        PrintStatus.FAILED, printException.getMessage(), LocalDateTime.now(clock), failureConnection);
                failureConnection.commit();
            } catch (RuntimeException | SQLException exception) {
                rollback(failureConnection);
                throw exception;
            } finally {
                failureConnection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception exception) {
            System.err.println("Unable to record receipt print failure: " + exception.getMessage());
        }
        activityLogService.logReceiptPrintFailed(currentUser.getUserId(), receiptId, printException.getMessage());
    }

    private String friendlyPrintFailure(Exception exception) {
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }
        return "Printer is not available.";
    }

    private void rollback(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException exception) {
            System.err.println("Receipt print rollback failed: " + exception.getMessage());
        }
    }

    private void restoreAutoCommitAndClose(Connection connection, boolean previousAutoCommit) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException exception) {
            System.err.println("Unable to restore receipt print connection auto-commit: " + exception.getMessage());
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            System.err.println("Unable to close receipt print connection: " + exception.getMessage());
        }
    }

    public static class ReceiptPrintException extends RuntimeException {
        public ReceiptPrintException(String message) {
            super(message);
        }

        public ReceiptPrintException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
