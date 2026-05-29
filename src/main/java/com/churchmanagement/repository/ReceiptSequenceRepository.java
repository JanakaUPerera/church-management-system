package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.ReceiptSequence;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

public class ReceiptSequenceRepository {
    private final DataSource dataSource;

    public ReceiptSequenceRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public ReceiptSequenceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<ReceiptSequence> findByYear(int year) {
        try (Connection connection = dataSource.getConnection()) {
            return findByYear(connection, year);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load receipt sequence.", exception);
        }
    }

    public ReceiptSequence createYear(int year) {
        try (Connection connection = dataSource.getConnection()) {
            return createYear(connection, year);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to create receipt sequence.", exception);
        }
    }

    public ReceiptSequence lockByYear(int year) {
        try (Connection connection = dataSource.getConnection()) {
            return lockByYear(connection, year);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to lock receipt sequence.", exception);
        }
    }

    public ReceiptSequence updateLastNumber(long id, long newNumber) {
        try (Connection connection = dataSource.getConnection()) {
            return updateLastNumber(connection, id, newNumber);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update receipt sequence.", exception);
        }
    }

    public Optional<ReceiptSequence> findByYear(Connection connection, int year) {
        String sql = """
                SELECT id, sequence_year, last_number, created_at, updated_at
                FROM receipt_sequences
                WHERE sequence_year = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, year);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapReceiptSequence(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load receipt sequence.", exception);
        }
    }

    public ReceiptSequence createYear(Connection connection, int year) {
        String sql = """
                INSERT INTO receipt_sequences (sequence_year, last_number, created_at)
                VALUES (?, 0, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            LocalDateTime now = LocalDateTime.now();
            statement.setInt(1, year);
            statement.setTimestamp(2, Timestamp.valueOf(now));
            statement.executeUpdate();

            long id = 0L;
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    id = generatedKeys.getLong(1);
                }
            }

            return new ReceiptSequence(id, year, 0L, now, null);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to create receipt sequence.", exception);
        }
    }

    public ReceiptSequence lockByYear(Connection connection, int year) {
        String sql = """
                SELECT id, sequence_year, last_number, created_at, updated_at
                FROM receipt_sequences
                WHERE sequence_year = ?
                FOR UPDATE
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, year);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapReceiptSequence(resultSet);
                }
            }
            return null;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to lock receipt sequence.", exception);
        }
    }

    public ReceiptSequence updateLastNumber(Connection connection, long id, long newNumber) {
        String sql = """
                UPDATE receipt_sequences
                SET last_number = ?, updated_at = ?
                WHERE id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            LocalDateTime now = LocalDateTime.now();
            statement.setLong(1, newNumber);
            statement.setTimestamp(2, Timestamp.valueOf(now));
            statement.setLong(3, id);
            statement.executeUpdate();
            return findById(connection, id).orElseThrow(() ->
                    new DatabaseException("Receipt sequence was not found after update."));
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update receipt sequence.", exception);
        }
    }

    private Optional<ReceiptSequence> findById(Connection connection, long id) {
        String sql = """
                SELECT id, sequence_year, last_number, created_at, updated_at
                FROM receipt_sequences
                WHERE id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapReceiptSequence(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load receipt sequence.", exception);
        }
    }

    private ReceiptSequence mapReceiptSequence(ResultSet resultSet) throws SQLException {
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new ReceiptSequence(
                resultSet.getLong("id"),
                resultSet.getInt("sequence_year"),
                resultSet.getLong("last_number"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                updatedAt == null ? null : updatedAt.toLocalDateTime()
        );
    }
}
