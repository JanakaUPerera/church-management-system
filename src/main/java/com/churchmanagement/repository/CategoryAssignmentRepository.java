package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.CategoryAssignment;
import com.churchmanagement.enums.CollectionType;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** Every method runs on the caller's connection so callers control the transaction. */
public class CategoryAssignmentRepository {
    private static final String SELECT_BY_CHURCH_AND_YEAR = """
            SELECT church_id, collection_type, category_id, accounting_year
            FROM church_category_assignments
            WHERE church_id = ? AND accounting_year = ?
            """;

    private final DataSource dataSource;

    public CategoryAssignmentRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public CategoryAssignmentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<CategoryAssignment> findByChurchAndYear(long churchId, int year, Connection connection) {
        return queryAssignments(SELECT_BY_CHURCH_AND_YEAR, churchId, year, connection);
    }

    /** Locks the rows (and, under InnoDB REPEATABLE READ, the gap when there are none). */
    public List<CategoryAssignment> findByChurchAndYearForUpdate(long churchId, int year, Connection connection) {
        return queryAssignments(SELECT_BY_CHURCH_AND_YEAR + " FOR UPDATE", churchId, year, connection);
    }

    public OptionalInt findLatestYearBefore(long churchId, int year, Connection connection) {
        String sql = """
                SELECT MAX(accounting_year) latest_year
                FROM church_category_assignments
                WHERE church_id = ? AND accounting_year < ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, churchId);
            statement.setInt(2, year);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int latest = resultSet.getInt("latest_year");
                    return resultSet.wasNull() ? OptionalInt.empty() : OptionalInt.of(latest);
                }
            }
            return OptionalInt.empty();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load category years.", exception);
        }
    }

    public List<Integer> findYears(long churchId, Connection connection) {
        String sql = """
                SELECT DISTINCT accounting_year
                FROM church_category_assignments
                WHERE church_id = ?
                ORDER BY accounting_year
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, churchId);
            List<Integer> years = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    years.add(resultSet.getInt("accounting_year"));
                }
            }
            return years;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load category years.", exception);
        }
    }

    /** Counts receipts of every status (cancelled ones too): any receipt locks the year. */
    public int countReceiptsForYear(long churchId, int year, Connection connection) {
        String sql = "SELECT COUNT(*) receipt_count FROM receipts WHERE church_id = ? AND accounting_year = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, churchId);
            statement.setInt(2, year);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("receipt_count") : 0;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to count receipts.", exception);
        }
    }

    public void upsert(long churchId, int year, CollectionType type, long categoryId, LocalDateTime now,
                       Connection connection) {
        String sql = """
                INSERT INTO church_category_assignments
                    (church_id, collection_type, category_id, accounting_year, created_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE category_id = VALUES(category_id), updated_at = VALUES(created_at)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, churchId);
            statement.setString(2, type.name());
            statement.setLong(3, categoryId);
            statement.setInt(4, year);
            statement.setTimestamp(5, Timestamp.valueOf(now));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save category assignment.", exception);
        }
    }

    private List<CategoryAssignment> queryAssignments(String sql, long churchId, int year, Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, churchId);
            statement.setInt(2, year);
            List<CategoryAssignment> assignments = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    assignments.add(new CategoryAssignment(
                            resultSet.getLong("church_id"),
                            CollectionType.valueOf(resultSet.getString("collection_type")),
                            resultSet.getLong("category_id"),
                            resultSet.getInt("accounting_year")));
                }
            }
            return assignments;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load category assignments.", exception);
        }
    }
}
