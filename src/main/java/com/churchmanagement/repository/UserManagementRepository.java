package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.User;
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
import java.util.List;
import java.util.Optional;

public class UserManagementRepository {
    private final DataSource dataSource;

    public UserManagementRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public UserManagementRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<User> findAll() {
        return queryUsers(baseSelect() + " ORDER BY u.username");
    }

    public List<User> search(String searchText) {
        String sql = baseSelect() + """
                WHERE u.username LIKE ? OR u.full_name LIKE ? OR r.name LIKE ?
                ORDER BY u.username
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            String term = "%" + searchText + "%";
            statement.setString(1, term);
            statement.setString(2, term);
            statement.setString(3, term);
            return mapUsers(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to search users.", exception);
        }
    }

    public Optional<User> findById(long id) {
        String sql = baseSelect() + " WHERE u.id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return mapUsers(statement).stream().findFirst();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load user.", exception);
        }
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check username.", exception);
        }
    }

    public boolean existsByUsernameAndIdNot(String username, long id) {
        String sql = "SELECT 1 FROM users WHERE username = ? AND id <> ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setLong(2, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check username.", exception);
        }
    }

    public boolean roleExists(long roleId) {
        String sql = "SELECT 1 FROM roles WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, roleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check role.", exception);
        }
    }

    public List<RoleOption> findRoles() {
        String sql = "SELECT id, name FROM roles ORDER BY name";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<RoleOption> roles = new ArrayList<>();
            while (resultSet.next()) {
                roles.add(new RoleOption(resultSet.getLong("id"), resultSet.getString("name")));
            }
            return roles;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load roles.", exception);
        }
    }

    public User save(User user) {
        String sql = """
                INSERT INTO users (
                    username, full_name, role_id, active, password_hash, force_password_change, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getFullName());
            statement.setLong(3, user.getRoleId());
            statement.setBoolean(4, user.getStatus() == User.Status.ACTIVE);
            statement.setString(5, user.getPasswordHash());
            statement.setBoolean(6, user.isForcePasswordChange());
            statement.setTimestamp(7, Timestamp.valueOf(user.getCreatedAt()));
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    user.setId(generatedKeys.getLong(1));
                }
            }
            return findById(user.getId()).orElse(user);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save user.", exception);
        }
    }

    public User update(User user) {
        String sql = """
                UPDATE users
                SET username = ?, full_name = ?, role_id = ?, active = ?, force_password_change = ?, updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getFullName());
            statement.setLong(3, user.getRoleId());
            statement.setBoolean(4, user.getStatus() == User.Status.ACTIVE);
            statement.setBoolean(5, user.isForcePasswordChange());
            statement.setTimestamp(6, Timestamp.valueOf(user.getUpdatedAt()));
            statement.setLong(7, user.getId());
            statement.executeUpdate();
            return findById(user.getId()).orElse(user);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update user.", exception);
        }
    }

    public void updateStatus(long id, User.Status status, LocalDateTime updatedAt) {
        String sql = "UPDATE users SET active = ?, updated_at = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, status == User.Status.ACTIVE);
            statement.setTimestamp(2, Timestamp.valueOf(updatedAt));
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update user status.", exception);
        }
    }

    public void updatePassword(long id, String passwordHash, boolean forcePasswordChange, LocalDateTime updatedAt) {
        String sql = "UPDATE users SET password_hash = ?, force_password_change = ?, updated_at = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setBoolean(2, forcePasswordChange);
            statement.setTimestamp(3, Timestamp.valueOf(updatedAt));
            statement.setLong(4, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to reset user password.", exception);
        }
    }

    private List<User> queryUsers(String sql) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return mapUsers(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load users.", exception);
        }
    }

    private List<User> mapUsers(PreparedStatement statement) throws SQLException {
        List<User> users = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(mapUser(resultSet));
            }
        }
        return users;
    }

    private User mapUser(ResultSet resultSet) throws SQLException {
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new User(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("full_name"),
                resultSet.getLong("role_id"),
                resultSet.getString("role_name"),
                resultSet.getBoolean("active") ? User.Status.ACTIVE : User.Status.INACTIVE,
                resultSet.getString("password_hash"),
                resultSet.getBoolean("force_password_change"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                updatedAt == null ? null : updatedAt.toLocalDateTime()
        );
    }

    private String baseSelect() {
        return """
                SELECT u.id, u.username, u.full_name, u.role_id, r.name AS role_name, u.active,
                       u.password_hash, COALESCE(u.force_password_change, FALSE) AS force_password_change,
                       u.created_at, u.updated_at
                FROM users u
                JOIN roles r ON r.id = u.role_id
                """;
    }

    protected void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    public record RoleOption(long id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
