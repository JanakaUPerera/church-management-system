package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.Role;
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

public class RoleRepository {
    private final DataSource dataSource;

    public RoleRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public RoleRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Role> findAll() {
        String sql = """
                SELECT id, name, COALESCE(status, 'ACTIVE') AS status, created_at, updated_at
                FROM roles
                ORDER BY name
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<Role> roles = new ArrayList<>();
            while (resultSet.next()) {
                roles.add(mapRole(resultSet));
            }
            return roles;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load roles.", exception);
        }
    }

    public Optional<Role> findById(long id) {
        String sql = """
                SELECT id, name, COALESCE(status, 'ACTIVE') AS status, created_at, updated_at
                FROM roles
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRole(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load role.", exception);
        }
    }

    public boolean existsByName(String roleName) {
        String sql = "SELECT 1 FROM roles WHERE LOWER(name) = LOWER(?)";
        return exists(sql, statement -> statement.setString(1, roleName));
    }

    public boolean existsByNameAndIdNot(String roleName, long id) {
        String sql = "SELECT 1 FROM roles WHERE LOWER(name) = LOWER(?) AND id <> ?";
        return exists(sql, statement -> {
            statement.setString(1, roleName);
            statement.setLong(2, id);
        });
    }

    public Role save(Role role) {
        String sql = "INSERT INTO roles (name, status, created_at, updated_at) VALUES (?, ?, ?, ?)";
        LocalDateTime now = role.getCreatedAt() == null ? LocalDateTime.now() : role.getCreatedAt();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, role.getRoleName());
            statement.setString(2, role.getStatus().name());
            statement.setTimestamp(3, Timestamp.valueOf(now));
            statement.setTimestamp(4, Timestamp.valueOf(now));
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    role.setId(generatedKeys.getLong(1));
                }
            }
            return findById(role.getId()).orElse(role);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save role.", exception);
        }
    }

    public Role updateName(long id, String roleName, LocalDateTime updatedAt) {
        String sql = "UPDATE roles SET name = ?, updated_at = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, roleName);
            statement.setTimestamp(2, Timestamp.valueOf(updatedAt));
            statement.setLong(3, id);
            statement.executeUpdate();
            return findById(id).orElseThrow(() -> new DatabaseException("Role could not be found."));
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update role.", exception);
        }
    }

    public void updateStatus(long id, Role.Status status, LocalDateTime updatedAt) {
        String sql = "UPDATE roles SET status = ?, updated_at = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setTimestamp(2, Timestamp.valueOf(updatedAt));
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update role status.", exception);
        }
    }

    public int countActiveRolesWithPermission(String permissionCode) {
        String sql = """
                SELECT COUNT(DISTINCT r.id)
                FROM roles r
                JOIN role_permissions rp ON rp.role_id = r.id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE COALESCE(r.status, 'ACTIVE') = 'ACTIVE'
                  AND p.name = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, permissionCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to count role permissions.", exception);
        }
    }

    public int countActiveRolesWithPermissionExcluding(long excludedRoleId, String permissionCode) {
        String sql = """
                SELECT COUNT(DISTINCT r.id)
                FROM roles r
                JOIN role_permissions rp ON rp.role_id = r.id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE COALESCE(r.status, 'ACTIVE') = 'ACTIVE'
                  AND r.id <> ?
                  AND p.name = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, excludedRoleId);
            statement.setString(2, permissionCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to count role permissions.", exception);
        }
    }

    private Role mapRole(ResultSet resultSet) throws SQLException {
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new Role(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                Role.Status.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                updatedAt == null ? null : updatedAt.toLocalDateTime()
        );
    }

    private boolean exists(String sql, SqlBinder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check role.", exception);
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}
