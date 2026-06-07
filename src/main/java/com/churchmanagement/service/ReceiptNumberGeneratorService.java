package com.churchmanagement.service;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.ReceiptSequence;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.exception.ReceiptSequenceLimitExceededException;
import com.churchmanagement.repository.ReceiptSequenceRepository;
import com.churchmanagement.security.AuthContext;
import com.churchmanagement.util.ReceiptNumberFormatter;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Year;

public class ReceiptNumberGeneratorService {
    private final ReceiptSequenceRepository receiptSequenceRepository;
    private final ActivityLogService activityLogService;
    private final ReceiptNumberFormatter receiptNumberFormatter;
    private final Clock clock;
    private final DataSource dataSource;

    public ReceiptNumberGeneratorService() {
        this(new ReceiptSequenceRepository(), new ActivityLogService(),
                new ReceiptNumberFormatter(SystemConfigurationCache.getInstance()),
                Clock.systemDefaultZone(), DatabaseConfig.getDataSource());
    }

    public ReceiptNumberGeneratorService(ReceiptSequenceRepository receiptSequenceRepository,
                                         ActivityLogService activityLogService,
                                         ReceiptNumberFormatter receiptNumberFormatter,
                                         Clock clock) {
        this(receiptSequenceRepository, activityLogService, receiptNumberFormatter, clock, null);
    }

    public ReceiptNumberGeneratorService(ReceiptSequenceRepository receiptSequenceRepository,
                                         ActivityLogService activityLogService,
                                         ReceiptNumberFormatter receiptNumberFormatter,
                                         Clock clock,
                                         DataSource dataSource) {
        this.receiptSequenceRepository = receiptSequenceRepository;
        this.activityLogService = activityLogService;
        this.receiptNumberFormatter = receiptNumberFormatter;
        this.clock = clock;
        this.dataSource = dataSource;
    }

    public String generateReceiptNumber() {
        if (dataSource == null) {
            return generateWithoutJdbcTransaction();
        }

        Connection connection = null;
        boolean previousAutoCommit = true;

        try {
            connection = dataSource.getConnection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            GeneratedReceiptNumber generated = generate(connection);
            connection.commit();
            logGeneratedReceiptNumber(generated);
            return generated.receiptNumber();
        } catch (ReceiptSequenceLimitExceededException exception) {
            rollback(connection);
            throw exception;
        } catch (SQLException exception) {
            rollback(connection);
            throw new DatabaseException("Unable to generate receipt number.", exception);
        } catch (RuntimeException exception) {
            rollback(connection);
            throw exception;
        } finally {
            restoreAutoCommitAndClose(connection, previousAutoCommit);
        }
    }

    public String generateReceiptNumber(Connection connection) {
        return generate(connection).receiptNumber();
    }

    private synchronized String generateWithoutJdbcTransaction() {
        int year = Year.now(clock).getValue();
        ReceiptSequence sequence = receiptSequenceRepository.lockByYear(year);
        if (sequence == null) {
            sequence = receiptSequenceRepository.createYear(year);
        }

        GeneratedReceiptNumber generated = incrementAndFormat(sequence);
        receiptSequenceRepository.updateLastNumber(sequence.getId(), generated.sequence());
        logGeneratedReceiptNumber(generated);
        return generated.receiptNumber();
    }

    private GeneratedReceiptNumber generate(Connection connection) {
        int year = Year.now(clock).getValue();
        ReceiptSequence sequence = receiptSequenceRepository.lockByYear(connection, year);
        if (sequence == null) {
            receiptSequenceRepository.createYear(connection, year);
            sequence = receiptSequenceRepository.lockByYear(connection, year);
        }

        GeneratedReceiptNumber generated = incrementAndFormat(sequence);
        receiptSequenceRepository.updateLastNumber(connection, sequence.getId(), generated.sequence());
        return generated;
    }

    private GeneratedReceiptNumber incrementAndFormat(ReceiptSequence sequence) {
        long nextNumber = sequence.getLastNumber() + 1;
        if (nextNumber > receiptNumberFormatter.maxSequence()) {
            throw new ReceiptSequenceLimitExceededException(sequence.getSequenceYear(), nextNumber);
        }

        String receiptNumber = receiptNumberFormatter.format(sequence.getSequenceYear(), nextNumber);
        return new GeneratedReceiptNumber(sequence.getSequenceYear(), nextNumber, receiptNumber);
    }

    private void logGeneratedReceiptNumber(GeneratedReceiptNumber generated) {
        Long userId = AuthContext.getCurrentUser()
                .map(user -> user.getUserId())
                .orElse(null);
        activityLogService.logReceiptNumberGenerated(userId, generated.year(), generated.sequence(),
                generated.receiptNumber());
    }

    private void rollback(Connection connection) {
        if (connection == null) {
            return;
        }

        try {
            connection.rollback();
        } catch (SQLException exception) {
            System.err.println("Receipt sequence rollback failed: " + exception.getMessage());
        }
    }

    private void restoreAutoCommitAndClose(Connection connection, boolean previousAutoCommit) {
        if (connection == null) {
            return;
        }

        try {
            connection.setAutoCommit(previousAutoCommit);
        } catch (SQLException exception) {
            System.err.println("Unable to restore connection auto-commit: " + exception.getMessage());
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            System.err.println("Unable to close receipt sequence connection: " + exception.getMessage());
        }
    }

    private record GeneratedReceiptNumber(int year, long sequence, String receiptNumber) {
    }
}
