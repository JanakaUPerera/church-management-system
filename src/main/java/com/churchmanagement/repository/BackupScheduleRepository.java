package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.BackupScheduleDto;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BackupScheduleRepository {
    private final DataSource dataSource;

    public BackupScheduleRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public BackupScheduleRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<BackupScheduleDto> findAll() {
        return findBySql("""
                SELECT id, schedule_name, backup_time, enabled, created_at, updated_at
                FROM backup_schedules
                ORDER BY backup_time, schedule_name, id
                """);
    }

    public List<BackupScheduleDto> findEnabled() {
        return findBySql("""
                SELECT id, schedule_name, backup_time, enabled, created_at, updated_at
                FROM backup_schedules
                WHERE enabled = TRUE
                ORDER BY backup_time, schedule_name, id
                """);
    }

    public BackupScheduleDto insert(BackupScheduleDto schedule) {
        String sql = """
                INSERT INTO backup_schedules (schedule_name, backup_time, enabled, created_at)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, schedule.getScheduleName().strip());
            statement.setTime(2, Time.valueOf(schedule.getBackupTime()));
            statement.setBoolean(3, schedule.isEnabled());
            statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            statement.executeUpdate();
            return findById(generatedId(statement));
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save backup schedule.", exception);
        }
    }

    public BackupScheduleDto update(BackupScheduleDto schedule) {
        String sql = """
                UPDATE backup_schedules
                SET schedule_name = ?, backup_time = ?, enabled = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schedule.getScheduleName().strip());
            statement.setTime(2, Time.valueOf(schedule.getBackupTime()));
            statement.setBoolean(3, schedule.isEnabled());
            statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            statement.setLong(5, schedule.getId());
            statement.executeUpdate();
            return findById(schedule.getId());
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update backup schedule.", exception);
        }
    }

    public BackupScheduleDto setEnabled(long id, boolean enabled) {
        String sql = "UPDATE backup_schedules SET enabled = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, enabled);
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setLong(3, id);
            statement.executeUpdate();
            return findById(id);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update backup schedule status.", exception);
        }
    }

    public void deleteById(long id) {
        String sql = "DELETE FROM backup_schedules WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to delete backup schedule.", exception);
        }
    }

    private BackupScheduleDto findById(long id) throws SQLException {
        String sql = """
                SELECT id, schedule_name, backup_time, enabled, created_at, updated_at
                FROM backup_schedules
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            List<BackupScheduleDto> schedules = mapSchedules(statement);
            if (schedules.isEmpty()) {
                throw new SQLException("Missing backup schedule.");
            }
            return schedules.getFirst();
        }
    }

    private List<BackupScheduleDto> findBySql(String sql) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return mapSchedules(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load backup schedules.", exception);
        }
    }

    private List<BackupScheduleDto> mapSchedules(PreparedStatement statement) throws SQLException {
        List<BackupScheduleDto> schedules = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                BackupScheduleDto schedule = new BackupScheduleDto();
                schedule.setId(resultSet.getLong("id"));
                schedule.setScheduleName(resultSet.getString("schedule_name"));
                schedule.setBackupTime(resultSet.getTime("backup_time").toLocalTime());
                schedule.setEnabled(resultSet.getBoolean("enabled"));
                Timestamp createdAt = resultSet.getTimestamp("created_at");
                Timestamp updatedAt = resultSet.getTimestamp("updated_at");
                schedule.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                schedule.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
                schedules.add(schedule);
            }
        }
        return schedules;
    }

    private long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.getGeneratedKeys()) {
            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
        }
        throw new SQLException("Missing generated key.");
    }
}
