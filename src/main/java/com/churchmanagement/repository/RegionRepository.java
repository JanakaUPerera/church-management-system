package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.Region;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RegionRepository {
    private final DataSource dataSource;

    public RegionRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public RegionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Region> findAll() {
        String sql = """
                SELECT id, region_code, region_name, status, created_at, updated_at
                FROM regions
                ORDER BY region_code
                """;
        return queryRegions(sql);
    }

    public List<Region> findActiveRegions() {
        String sql = """
                SELECT id, region_code, region_name, status, created_at, updated_at
                FROM regions
                WHERE status = 'ACTIVE'
                ORDER BY region_code
                """;
        return queryRegions(sql);
    }

    public List<Region> search(String searchText) {
        String sql = """
                SELECT id, region_code, region_name, status, created_at, updated_at
                FROM regions
                WHERE region_code LIKE ? OR region_name LIKE ?
                ORDER BY region_code
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            String term = "%" + searchText + "%";
            statement.setString(1, term);
            statement.setString(2, term);
            return mapRegions(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to search regions.", exception);
        }
    }

    public Optional<Region> findById(long id) {
        String sql = """
                SELECT id, region_code, region_name, status, created_at, updated_at
                FROM regions
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            List<Region> regions = mapRegions(statement);
            return regions.stream().findFirst();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load region.", exception);
        }
    }

    public boolean existsByRegionCode(String regionCode) {
        String sql = "SELECT 1 FROM regions WHERE region_code = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, regionCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check region code.", exception);
        }
    }

    public boolean existsByRegionCodeAndIdNot(String regionCode, long id) {
        String sql = "SELECT 1 FROM regions WHERE region_code = ? AND id <> ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, regionCode);
            statement.setLong(2, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check region code.", exception);
        }
    }

    public Region save(Region region) {
        String sql = """
                INSERT INTO regions (region_code, region_name, status, created_at)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, region.getRegionCode());
            statement.setString(2, region.getRegionName());
            statement.setString(3, region.getStatus().name());
            statement.setTimestamp(4, Timestamp.valueOf(region.getCreatedAt()));
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    region.setId(generatedKeys.getLong(1));
                }
            }

            return region;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save region.", exception);
        }
    }

    public Region update(Region region) {
        String sql = """
                UPDATE regions
                SET region_code = ?, region_name = ?, status = ?, updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, region.getRegionCode());
            statement.setString(2, region.getRegionName());
            statement.setString(3, region.getStatus().name());
            statement.setTimestamp(4, Timestamp.valueOf(region.getUpdatedAt()));
            statement.setLong(5, region.getId());
            statement.executeUpdate();
            return region;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update region.", exception);
        }
    }

    public void updateStatus(long id, Region.Status status, LocalDateTime updatedAt) {
        String sql = "UPDATE regions SET status = ?, updated_at = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setTimestamp(2, Timestamp.valueOf(updatedAt));
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update region status.", exception);
        }
    }

    private List<Region> queryRegions(String sql) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            return mapRegions(statement);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load regions.", exception);
        }
    }

    private List<Region> mapRegions(PreparedStatement statement) throws SQLException {
        List<Region> regions = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                regions.add(mapRegion(resultSet));
            }
        }
        return regions;
    }

    private Region mapRegion(ResultSet resultSet) throws SQLException {
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new Region(
                resultSet.getLong("id"),
                resultSet.getString("region_code"),
                resultSet.getString("region_name"),
                Region.Status.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                updatedAt == null ? null : updatedAt.toLocalDateTime()
        );
    }
}
