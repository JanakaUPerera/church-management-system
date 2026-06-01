package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
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
import java.util.Optional;

public class UserRepository {
    private final DataSource dataSource;

    public UserRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<UserCredentials> findByUsername(String username) {
        String sql = """
                SELECT u.id, u.username, u.password_hash, u.full_name, u.active,
                       COALESCE(u.force_password_change, FALSE) AS force_password_change,
                       r.id AS role_id, r.name AS role_name
                FROM users u
                JOIN roles r ON r.id = u.role_id
                WHERE u.username = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(new UserCredentials(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("full_name"),
                        resultSet.getLong("role_id"),
                        resultSet.getString("role_name"),
                        resultSet.getBoolean("active"),
                        resultSet.getBoolean("force_password_change")
                ));
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load user account.", exception);
        }
    }

    public void updateLastLoginAt(long userId, LocalDateTime lastLoginAt) {
        String sql = "UPDATE users SET last_login_at = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(lastLoginAt));
            statement.setLong(2, userId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update user login timestamp.", exception);
        }
    }

    public Optional<String> findPasswordHashByUserId(long userId) {
        String sql = "SELECT password_hash FROM users WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(resultSet.getString("password_hash"));
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load user password.", exception);
        }
    }

    public void updatePasswordAndClearForceChange(long userId, String newPasswordHash, LocalDateTime updatedAt) {
        String sql = """
                UPDATE users
                SET password_hash = ?, force_password_change = FALSE, updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newPasswordHash);
            statement.setTimestamp(2, Timestamp.valueOf(updatedAt));
            statement.setLong(3, userId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to change user password.", exception);
        }
    }

    public List<UserSummary> findActiveUserSummaries() {
        String sql = """
                SELECT id, username, full_name
                FROM users
                WHERE active = TRUE
                ORDER BY username
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<UserSummary> users = new ArrayList<>();
            while (resultSet.next()) {
                users.add(new UserSummary(
                        resultSet.getLong("id"),
                        resultSet.getString("username"),
                        resultSet.getString("full_name")
                ));
            }
            return users;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load users.", exception);
        }
    }

    public record UserCredentials(
            long userId,
            String username,
            String passwordHash,
            String fullName,
            long roleId,
            String roleName,
            boolean active,
            boolean forcePasswordChange
    ) {
    }

    public record UserSummary(long userId, String username, String fullName) {
        @Override
        public String toString() {
            return username + " - " + fullName;
        }
    }
}
