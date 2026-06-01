package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.RestoreLogDto;
import com.churchmanagement.enums.RestoreStatus;
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

public class RestoreRepository {
    private final DataSource dataSource;

    public RestoreRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public RestoreRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public RestoreLogDto insertRestoreLog(String backupFileName, String backupFilePath, Long preRestoreBackupLogId,
                                          RestoreStatus status, String errorMessage, long restoredByUserId,
                                          LocalDateTime restoredAt) {
        String sql = """
                INSERT INTO restore_logs
                    (backup_file_name, backup_file_path, pre_restore_backup_log_id, status, error_message,
                     restored_by_user_id, restored_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, backupFileName);
            statement.setString(2, backupFilePath);
            if (preRestoreBackupLogId == null) {
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(3, preRestoreBackupLogId);
            }
            statement.setString(4, status.name());
            if (errorMessage == null || errorMessage.isBlank()) {
                statement.setNull(5, Types.VARCHAR);
            } else {
                statement.setString(5, errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage);
            }
            statement.setLong(6, restoredByUserId);
            statement.setTimestamp(7, Timestamp.valueOf(restoredAt));
            statement.executeUpdate();
            return findByGeneratedId(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save restore log.", exception);
        }
    }

    public List<RestoreLogDto> searchRestoreLogs(int limit) {
        String sql = """
                SELECT rl.id, rl.backup_file_name, rl.backup_file_path, rl.pre_restore_backup_log_id,
                       rl.status, rl.error_message, rl.restored_at, u.full_name AS restored_by_full_name
                FROM restore_logs rl
                JOIN users u ON u.id = rl.restored_by_user_id
                ORDER BY rl.restored_at DESC
                LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            return mapLogs(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load restore logs.", exception);
        }
    }

    private RestoreLogDto findByGeneratedId(PreparedStatement insertStatement) throws SQLException {
        try (ResultSet keys = insertStatement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("Missing generated key.");
            }
            String sql = """
                    SELECT rl.id, rl.backup_file_name, rl.backup_file_path, rl.pre_restore_backup_log_id,
                           rl.status, rl.error_message, rl.restored_at, u.full_name AS restored_by_full_name
                    FROM restore_logs rl
                    JOIN users u ON u.id = rl.restored_by_user_id
                    WHERE rl.id = ?
                    """;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, keys.getLong(1));
                return mapLogs(statement).getFirst();
            }
        }
    }

    private List<RestoreLogDto> mapLogs(PreparedStatement statement) throws SQLException {
        List<RestoreLogDto> logs = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                RestoreLogDto log = new RestoreLogDto();
                log.setId(resultSet.getLong("id"));
                log.setBackupFileName(resultSet.getString("backup_file_name"));
                log.setBackupFilePath(resultSet.getString("backup_file_path"));
                long preRestoreId = resultSet.getLong("pre_restore_backup_log_id");
                log.setPreRestoreBackupLogId(resultSet.wasNull() ? null : preRestoreId);
                log.setStatus(RestoreStatus.valueOf(resultSet.getString("status")));
                log.setErrorMessage(resultSet.getString("error_message"));
                log.setRestoredByFullName(resultSet.getString("restored_by_full_name"));
                Timestamp restoredAt = resultSet.getTimestamp("restored_at");
                log.setRestoredAt(restoredAt == null ? null : restoredAt.toLocalDateTime());
                logs.add(log);
            }
        }
        return logs;
    }
}
