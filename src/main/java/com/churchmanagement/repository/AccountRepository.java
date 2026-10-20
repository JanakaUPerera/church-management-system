package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.Account;
import com.churchmanagement.enums.AccountType;
import com.churchmanagement.enums.CashFlowClass;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AccountRepository {
    private static final String SELECT = """
            SELECT id, code, name, type, parent_id, is_group, cash_flow_class, system_key, description, status
            FROM accounts
            """;

    private final DataSource dataSource;

    public AccountRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public AccountRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<Account> findAll() {
        return query(SELECT + " ORDER BY code", null);
    }

    public Optional<Account> findById(long id) {
        return query(SELECT + " WHERE id = ?", id).stream().findFirst();
    }

    public Optional<Account> findByCode(String code) {
        return query(SELECT + " WHERE code = ?", code).stream().findFirst();
    }

    public Optional<Account> findBySystemKey(String systemKey) {
        return query(SELECT + " WHERE system_key = ?", systemKey).stream().findFirst();
    }

    public Account insert(Account account, LocalDateTime now) {
        String sql = """
                INSERT INTO accounts (code, name, type, parent_id, is_group, cash_flow_class, system_key,
                                      description, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, account.code());
            statement.setString(2, account.name());
            statement.setString(3, account.type().name());
            setNullableLong(statement, 4, account.parentId());
            statement.setBoolean(5, account.group());
            statement.setString(6, account.cashFlowClass().name());
            statement.setString(7, account.systemKey());
            statement.setString(8, account.description());
            statement.setString(9, account.status().name());
            statement.setTimestamp(10, Timestamp.valueOf(now));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return account.withId(keys.getLong(1));
                }
            }
            throw new DatabaseException("Account id was not generated.");
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to save the account.", exception);
        }
    }

    public void update(Account account, LocalDateTime now) {
        String sql = """
                UPDATE accounts
                SET code = ?, name = ?, parent_id = ?, cash_flow_class = ?, description = ?, updated_at = ?
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.code());
            statement.setString(2, account.name());
            setNullableLong(statement, 3, account.parentId());
            statement.setString(4, account.cashFlowClass().name());
            statement.setString(5, account.description());
            statement.setTimestamp(6, Timestamp.valueOf(now));
            statement.setLong(7, account.id());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update the account.", exception);
        }
    }

    public void updateStatus(long id, Account.Status status, LocalDateTime now) {
        String sql = "UPDATE accounts SET status = ?, updated_at = ? WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setTimestamp(2, Timestamp.valueOf(now));
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to update the account status.", exception);
        }
    }

    public void delete(long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE id = ?")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to delete the account.", exception);
        }
    }

    public boolean isUsed(long id) {
        return exists("SELECT 1 FROM journal_lines WHERE account_id = ? LIMIT 1", id);
    }

    public boolean hasChildren(long id) {
        return exists("SELECT 1 FROM accounts WHERE parent_id = ? LIMIT 1", id);
    }

    public boolean hasActiveChildren(long id) {
        return exists("SELECT 1 FROM accounts WHERE parent_id = ? AND status = 'ACTIVE' LIMIT 1", id);
    }

    /** Posted debits minus credits per account id. */
    public Map<Long, BigDecimal> postedNetDebits() {
        String sql = """
                SELECT jl.account_id, SUM(jl.debit) - SUM(jl.credit) AS net_debit
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entry_id
                WHERE je.status = 'POSTED'
                GROUP BY jl.account_id
                """;
        Map<Long, BigDecimal> balances = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                balances.put(resultSet.getLong("account_id"), resultSet.getBigDecimal("net_debit"));
            }
            return balances;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load account balances.", exception);
        }
    }

    private List<Account> query(String sql, Object parameter) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) {
                statement.setObject(1, parameter);
            }
            List<Account> accounts = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    accounts.add(map(resultSet));
                }
            }
            return accounts;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load accounts.", exception);
        }
    }

    private boolean exists(String sql, long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check the account.", exception);
        }
    }

    private Account map(ResultSet resultSet) throws SQLException {
        long parentId = resultSet.getLong("parent_id");
        Long parent = resultSet.wasNull() ? null : parentId;
        return new Account(resultSet.getLong("id"), resultSet.getString("code"), resultSet.getString("name"),
                AccountType.valueOf(resultSet.getString("type")), parent, resultSet.getBoolean("is_group"),
                CashFlowClass.valueOf(resultSet.getString("cash_flow_class")), resultSet.getString("system_key"),
                resultSet.getString("description"), Account.Status.valueOf(resultSet.getString("status")));
    }

    private void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }
}
