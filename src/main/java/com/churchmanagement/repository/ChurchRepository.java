package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.Church;
import com.churchmanagement.enums.AuthorizedPersonPosition;
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

public class ChurchRepository {
    private final DataSource dataSource;

    public ChurchRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public ChurchRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Church> findAll() {
        String sql = baseSelect() + " ORDER BY c.church_code";
        return queryChurches(sql);
    }

    public List<Church> search(String searchText) {
        String sql = baseSelect() + """
                WHERE c.church_code LIKE ? OR c.church_name LIKE ? OR r.region_name LIKE ?
                ORDER BY c.church_code
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            String term = "%" + searchText + "%";
            statement.setString(1, term);
            statement.setString(2, term);
            statement.setString(3, term);
            return mapChurches(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to search churches.", exception);
        }
    }

    public Optional<Church> findById(long id) {
        String sql = baseSelect() + " WHERE c.id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return mapChurches(statement).stream().findFirst();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load church.", exception);
        }
    }

    public boolean existsByChurchCode(String churchCode) {
        String sql = "SELECT 1 FROM churches WHERE church_code = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, churchCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check church code.", exception);
        }
    }

    public boolean existsByChurchCodeAndIdNot(String churchCode, long id) {
        String sql = "SELECT 1 FROM churches WHERE church_code = ? AND id <> ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, churchCode);
            statement.setLong(2, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check church code.", exception);
        }
    }

    public Church save(Church church) {
        String sql = """
                INSERT INTO churches (
                    church_code, church_name, region_id, status, authorized_person_name,
                    authorized_person_position, authorized_person_position_other, sms_mobile_number, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, church.getChurchCode());
            statement.setString(2, church.getChurchName());
            statement.setLong(3, church.getRegionId());
            statement.setString(4, church.getStatus().name());
            setNullableString(statement, 5, church.getAuthorizedPersonName());
            setNullableString(statement, 6, church.getAuthorizedPersonPosition() == null
                    ? null : church.getAuthorizedPersonPosition().name());
            setNullableString(statement, 7, church.getAuthorizedPersonPositionOther());
            setNullableString(statement, 8, church.getSmsMobileNumber());
            statement.setTimestamp(9, Timestamp.valueOf(church.getCreatedAt()));
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    church.setId(generatedKeys.getLong(1));
                }
            }

            return church;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save church.", exception);
        }
    }

    public Church update(Church church) {
        String sql = """
                UPDATE churches
                SET church_code = ?, church_name = ?, region_id = ?, status = ?,
                    authorized_person_name = ?, authorized_person_position = ?,
                    authorized_person_position_other = ?, sms_mobile_number = ?, updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, church.getChurchCode());
            statement.setString(2, church.getChurchName());
            statement.setLong(3, church.getRegionId());
            statement.setString(4, church.getStatus().name());
            setNullableString(statement, 5, church.getAuthorizedPersonName());
            setNullableString(statement, 6, church.getAuthorizedPersonPosition() == null
                    ? null : church.getAuthorizedPersonPosition().name());
            setNullableString(statement, 7, church.getAuthorizedPersonPositionOther());
            setNullableString(statement, 8, church.getSmsMobileNumber());
            statement.setTimestamp(9, Timestamp.valueOf(church.getUpdatedAt()));
            statement.setLong(10, church.getId());
            statement.executeUpdate();
            return church;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update church.", exception);
        }
    }

    public void updateStatus(long id, Church.Status status, LocalDateTime updatedAt) {
        String sql = "UPDATE churches SET status = ?, updated_at = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setTimestamp(2, Timestamp.valueOf(updatedAt));
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update church status.", exception);
        }
    }

    private List<Church> queryChurches(String sql) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return mapChurches(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load churches.", exception);
        }
    }

    private List<Church> mapChurches(PreparedStatement statement) throws SQLException {
        List<Church> churches = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                churches.add(mapChurch(resultSet));
            }
        }
        return churches;
    }

    private Church mapChurch(ResultSet resultSet) throws SQLException {
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        Church church = new Church(
                resultSet.getLong("id"),
                resultSet.getString("church_code"),
                resultSet.getString("church_name"),
                resultSet.getLong("region_id"),
                resultSet.getString("region_code"),
                resultSet.getString("region_name"),
                Church.Status.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                updatedAt == null ? null : updatedAt.toLocalDateTime()
        );
        church.setAuthorizedPersonName(resultSet.getString("authorized_person_name"));
        church.setAuthorizedPersonPosition(mapAuthorizedPersonPosition(resultSet.getString("authorized_person_position")));
        church.setAuthorizedPersonPositionOther(resultSet.getString("authorized_person_position_other"));
        church.setSmsMobileNumber(resultSet.getString("sms_mobile_number"));
        return church;
    }

    private String baseSelect() {
        return """
                SELECT c.id, c.church_code, c.church_name, c.region_id, c.status,
                       c.authorized_person_name, c.authorized_person_position,
                       c.authorized_person_position_other, c.sms_mobile_number,
                       c.created_at, c.updated_at,
                       r.region_code, r.region_name
                FROM churches c
                JOIN regions r ON r.id = c.region_id
                """;
    }

    private AuthorizedPersonPosition mapAuthorizedPersonPosition(String position) {
        if (position == null || position.isBlank()) {
            return null;
        }

        return AuthorizedPersonPosition.valueOf(position);
    }

    private void setNullableString(PreparedStatement statement, int parameterIndex, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(parameterIndex, Types.VARCHAR);
        } else {
            statement.setString(parameterIndex, value);
        }
    }
}
