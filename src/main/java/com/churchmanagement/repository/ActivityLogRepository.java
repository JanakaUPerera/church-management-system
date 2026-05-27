package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ActivityLogRepository {
    private final DataSource dataSource;

    public ActivityLogRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public ActivityLogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void save(Long userId, String action, String details) {
        String sql = """
                INSERT INTO activity_logs (user_id, action, entity_name, details)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (userId == null) {
                statement.setNull(1, java.sql.Types.BIGINT);
            } else {
                statement.setLong(1, userId);
            }
            statement.setString(2, action);
            statement.setString(3, "AUTH");
            statement.setString(4, details);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save activity log.", exception);
        }
    }
}
