package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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
}
