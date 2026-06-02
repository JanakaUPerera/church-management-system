package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.Permission;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PermissionRepository {
    private final DataSource dataSource;

    public PermissionRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public PermissionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<String> findPermissionCodesByRoleId(long roleId) {
        String sql = """
                SELECT p.name
                FROM permissions p
                JOIN role_permissions rp ON rp.permission_id = p.id
                WHERE rp.role_id = ?
                ORDER BY p.name
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, roleId);

            List<String> permissions = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    permissions.add(resultSet.getString("name"));
                }
            }

            return permissions;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load user permissions.", exception);
        }
    }

    public List<Permission> findAll() {
        String sql = """
                SELECT id, name, COALESCE(module, 'General') AS module, description, created_at
                FROM permissions
                ORDER BY module, name
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<Permission> permissions = new ArrayList<>();
            while (resultSet.next()) {
                permissions.add(new Permission(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getString("module"),
                        resultSet.getString("description"),
                        resultSet.getTimestamp("created_at").toLocalDateTime()
                ));
            }
            return permissions;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load permissions.", exception);
        }
    }

    public Set<String> findPermissionCodeSetByRoleId(long roleId) {
        return new LinkedHashSet<>(findPermissionCodesByRoleId(roleId));
    }

    public boolean allPermissionCodesExist(Collection<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return true;
        }

        String placeholders = String.join(",", permissionCodes.stream().map(code -> "?").toList());
        String sql = "SELECT COUNT(*) FROM permissions WHERE name IN (" + placeholders + ")";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String permissionCode : permissionCodes) {
                statement.setString(index++, permissionCode);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == new LinkedHashSet<>(permissionCodes).size();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to validate permissions.", exception);
        }
    }

    public void replaceRolePermissions(long roleId, Collection<String> permissionCodes) {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                deleteRolePermissions(connection, roleId);
                insertRolePermissions(connection, roleId, permissionCodes);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update role permissions.", exception);
        }
    }

    private void deleteRolePermissions(Connection connection, long roleId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM role_permissions WHERE role_id = ?")) {
            statement.setLong(1, roleId);
            statement.executeUpdate();
        }
    }

    private void insertRolePermissions(Connection connection, long roleId, Collection<String> permissionCodes)
            throws SQLException {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO role_permissions (role_id, permission_id)
                SELECT ?, id
                FROM permissions
                WHERE name = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String permissionCode : new LinkedHashSet<>(permissionCodes)) {
                statement.setLong(1, roleId);
                statement.setString(2, permissionCode);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
