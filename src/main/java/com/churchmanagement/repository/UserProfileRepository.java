package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.UserProfileDto;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

public class UserProfileRepository {
    private final DataSource dataSource;

    public UserProfileRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public UserProfileRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<UserProfileDto> findById(long userId) {
        String sql = """
                SELECT u.id, u.username, u.full_name, u.email, u.mobile_number, u.profile_picture_path,
                       u.role_id, r.name AS role_name, u.active
                FROM users u
                JOIN roles r ON r.id = u.role_id
                WHERE u.id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapProfile(resultSet));
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load user profile.", exception);
        }
    }

    public void updateProfile(long userId, String fullName, String email, String mobileNumber,
                              String profilePicturePath, LocalDateTime updatedAt) {
        String sql = """
                UPDATE users
                SET full_name = ?, email = ?, mobile_number = ?, profile_picture_path = ?, updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fullName);
            setNullableString(statement, 2, email);
            setNullableString(statement, 3, mobileNumber);
            setNullableString(statement, 4, profilePicturePath);
            statement.setTimestamp(5, Timestamp.valueOf(updatedAt));
            statement.setLong(6, userId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update user profile.", exception);
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
                return Optional.ofNullable(resultSet.getString("password_hash"));
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load user password.", exception);
        }
    }

    public void updatePassword(long userId, String passwordHash, LocalDateTime updatedAt) {
        String sql = "UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setTimestamp(2, Timestamp.valueOf(updatedAt));
            statement.setLong(3, userId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to change password.", exception);
        }
    }

    private UserProfileDto mapProfile(ResultSet resultSet) throws SQLException {
        UserProfileDto profile = new UserProfileDto();
        profile.setId(resultSet.getLong("id"));
        profile.setUsername(resultSet.getString("username"));
        profile.setFullName(resultSet.getString("full_name"));
        profile.setEmail(resultSet.getString("email"));
        profile.setMobileNumber(resultSet.getString("mobile_number"));
        profile.setProfilePicturePath(resultSet.getString("profile_picture_path"));
        profile.setRoleId(resultSet.getLong("role_id"));
        profile.setRoleName(resultSet.getString("role_name"));
        profile.setStatus(resultSet.getBoolean("active") ? "ACTIVE" : "INACTIVE");
        return profile;
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}
