package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.entity.FiscalYear;
import com.churchmanagement.exception.DatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class FiscalYearRepository {
    private final DataSource dataSource;

    public FiscalYearRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public FiscalYearRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<FiscalYear> findByYear(int year) {
        String sql = "SELECT year, start_date, end_date, status FROM fiscal_years WHERE year = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, year);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new FiscalYear(resultSet.getInt("year"),
                        resultSet.getDate("start_date").toLocalDate(),
                        resultSet.getDate("end_date").toLocalDate(),
                        FiscalYear.Status.valueOf(resultSet.getString("status"))));
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load the accounting year.", exception);
        }
    }

    public void insertIfMissing(FiscalYear fiscalYear) {
        String sql = "INSERT IGNORE INTO fiscal_years (year, start_date, end_date, status) VALUES (?, ?, ?, ?)";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, fiscalYear.year());
            statement.setDate(2, Date.valueOf(fiscalYear.startDate()));
            statement.setDate(3, Date.valueOf(fiscalYear.endDate()));
            statement.setString(4, fiscalYear.status().name());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to create the accounting year.", exception);
        }
    }
}
