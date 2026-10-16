package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.CollectionCategory;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CollectionCategoryRepository {
    private static final String SELECT_COLUMNS = """
            SELECT id, code, name, description, status, created_at, updated_at
            FROM collection_categories
            """;

    private final DataSource dataSource;

    public CollectionCategoryRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public CollectionCategoryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<CollectionCategory> findAll() {
        return query(SELECT_COLUMNS + " ORDER BY code", List.of(), "Unable to load categories.");
    }

    public List<CollectionCategory> findActive() {
        return query(SELECT_COLUMNS + " WHERE status = 'ACTIVE' ORDER BY code", List.of(),
                "Unable to load categories.");
    }

    public List<CollectionCategory> search(String searchText) {
        String term = "%" + searchText + "%";
        return query(SELECT_COLUMNS + " WHERE code LIKE ? OR name LIKE ? ORDER BY code", List.of(term, term),
                "Unable to search categories.");
    }

    public Optional<CollectionCategory> findById(long id) {
        return query(SELECT_COLUMNS + " WHERE id = ?", List.of(id), "Unable to load category.").stream().findFirst();
    }

    public Optional<CollectionCategory> findByCode(String code) {
        return query(SELECT_COLUMNS + " WHERE code = ?", List.of(code), "Unable to load category.").stream().findFirst();
    }

    public boolean existsByCode(String code) {
        return exists("SELECT 1 FROM collection_categories WHERE code = ?", List.of(code));
    }

    public boolean existsByCodeAndIdNot(String code, long id) {
        return exists("SELECT 1 FROM collection_categories WHERE code = ? AND id <> ?", List.of(code, id));
    }

    /** True when any assignment or receipt line references the category. */
    public boolean isInUse(long id) {
        return exists("""
                SELECT 1 FROM church_category_assignments WHERE category_id = ?
                UNION ALL
                SELECT 1 FROM receipt_items WHERE category_id = ?
                LIMIT 1
                """, List.of(id, id));
    }

    /** category id -> number of churches assigned to it (any collection type) in {@code year}. */
    public Map<Long, Integer> countChurchesByCategory(int year) {
        String sql = """
                SELECT category_id, COUNT(DISTINCT church_id) church_count
                FROM church_category_assignments
                WHERE accounting_year = ?
                GROUP BY category_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, year);
            Map<Long, Integer> counts = new HashMap<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    counts.put(resultSet.getLong("category_id"), resultSet.getInt("church_count"));
                }
            }
            return counts;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to count category usage.", exception);
        }
    }

    public CollectionCategory save(CollectionCategory category) {
        String sql = """
                INSERT INTO collection_categories (code, name, description, status, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, category.getCode());
            statement.setString(2, category.getName());
            setNullableString(statement, 3, category.getDescription());
            statement.setString(4, category.getStatus().name());
            statement.setTimestamp(5, Timestamp.valueOf(category.getCreatedAt()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys != null && keys.next()) {
                    category.setId(keys.getLong(1));
                }
            }
            return category;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save category.", exception);
        }
    }

    public CollectionCategory update(CollectionCategory category) {
        String sql = """
                UPDATE collection_categories
                SET code = ?, name = ?, description = ?, status = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category.getCode());
            statement.setString(2, category.getName());
            setNullableString(statement, 3, category.getDescription());
            statement.setString(4, category.getStatus().name());
            statement.setTimestamp(5, Timestamp.valueOf(category.getUpdatedAt()));
            statement.setLong(6, category.getId());
            statement.executeUpdate();
            return category;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update category.", exception);
        }
    }

    public void updateStatus(long id, CollectionCategory.Status status, LocalDateTime updatedAt) {
        String sql = "UPDATE collection_categories SET status = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setTimestamp(2, Timestamp.valueOf(updatedAt));
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update category status.", exception);
        }
    }

    public void delete(long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM collection_categories WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to delete category.", exception);
        }
    }

    private List<CollectionCategory> query(String sql, List<Object> parameters, String errorMessage) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            List<CollectionCategory> categories = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    categories.add(map(resultSet));
                }
            }
            return categories;
        } catch (SQLException exception) {
            throw new DatabaseException(errorMessage, exception);
        }
    }

    private boolean exists(String sql, List<Object> parameters) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check category.", exception);
        }
    }

    private void bind(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
        }
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private CollectionCategory map(ResultSet resultSet) throws SQLException {
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new CollectionCategory(
                resultSet.getLong("id"),
                resultSet.getString("code"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                CollectionCategory.Status.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                updatedAt == null ? null : updatedAt.toLocalDateTime());
    }
}
