package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.BackupLogDto;
import com.churchmanagement.enums.BackupStatus;
import com.churchmanagement.enums.BackupType;
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

public class BackupRepository {
    private final DataSource dataSource;

    public BackupRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public BackupRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public BackupLogDto insertBackupLog(BackupType backupType, String fileName, String filePath, Long fileSizeBytes,
                                        BackupStatus status, String errorMessage, Long createdByUserId,
                                        LocalDateTime createdAt) {
        String sql = """
                INSERT INTO backup_logs
                    (backup_type, file_name, file_path, file_size_bytes, status, error_message,
                     backup_file, message, created_by_user_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, backupType.name());
            statement.setString(2, fileName);
            statement.setString(3, filePath);
            setNullableLong(statement, 4, fileSizeBytes);
            statement.setString(5, status.name());
            setNullableString(statement, 6, truncate(errorMessage, 1000));
            statement.setString(7, truncate(fileName, 255));
            setNullableString(statement, 8, truncate(errorMessage, 500));
            setNullableLong(statement, 9, createdByUserId);
            statement.setTimestamp(10, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
            long id = generatedId(statement);
            return findBackupLogById(id).orElseThrow(() -> new DatabaseException("Unable to load backup log."));
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save backup log.", exception);
        }
    }

    public List<BackupLogDto> searchBackupLogs(int limit) {
        String sql = """
                SELECT bl.id, bl.backup_type, bl.file_name, bl.file_path, bl.file_size_bytes, bl.status,
                       bl.error_message, bl.created_at, u.full_name AS created_by_full_name
                FROM backup_logs bl
                LEFT JOIN users u ON u.id = bl.created_by_user_id
                ORDER BY bl.created_at DESC
                LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            return mapLogs(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load backup logs.", exception);
        }
    }

    public Optional<BackupLogDto> findBackupLogById(long id) {
        String sql = """
                SELECT bl.id, bl.backup_type, bl.file_name, bl.file_path, bl.file_size_bytes, bl.status,
                       bl.error_message, bl.created_at, u.full_name AS created_by_full_name
                FROM backup_logs bl
                LEFT JOIN users u ON u.id = bl.created_by_user_id
                WHERE bl.id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return mapLogs(statement).stream().findFirst();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load backup log.", exception);
        }
    }

    private List<BackupLogDto> mapLogs(PreparedStatement statement) throws SQLException {
        List<BackupLogDto> logs = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                BackupLogDto log = new BackupLogDto();
                log.setId(resultSet.getLong("id"));
                log.setBackupType(BackupType.valueOf(resultSet.getString("backup_type")));
                log.setFileName(resultSet.getString("file_name"));
                log.setFilePath(resultSet.getString("file_path"));
                long fileSize = resultSet.getLong("file_size_bytes");
                log.setFileSizeBytes(resultSet.wasNull() ? null : fileSize);
                log.setStatus(BackupStatus.valueOf(resultSet.getString("status")));
                log.setErrorMessage(resultSet.getString("error_message"));
                log.setCreatedByFullName(resultSet.getString("created_by_full_name"));
                Timestamp createdAt = resultSet.getTimestamp("created_at");
                log.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                logs.add(log);
            }
        }
        return logs;
    }

    private long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.getGeneratedKeys()) {
            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
        }
        throw new SQLException("Missing generated key.");
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
