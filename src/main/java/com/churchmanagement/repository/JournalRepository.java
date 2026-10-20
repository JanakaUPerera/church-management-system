package com.churchmanagement.repository;

import com.churchmanagement.config.DatabaseConfig;
import com.churchmanagement.dto.finance.JournalEntrySummary;
import com.churchmanagement.dto.finance.JournalSearchCriteria;
import com.churchmanagement.dto.finance.LedgerLine;
import com.churchmanagement.dto.finance.PostedEntry;
import com.churchmanagement.entity.JournalEntry;
import com.churchmanagement.entity.JournalLine;
import com.churchmanagement.enums.JournalSourceType;
import com.churchmanagement.enums.JournalStatus;
import com.churchmanagement.exception.DatabaseException;
import com.churchmanagement.exception.JournalStateException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Journal entries and lines. Posting and numbering happen inside one transaction. */
public class JournalRepository {
    private final DataSource dataSource;

    public JournalRepository() {
        this(DatabaseConfig.getDataSource());
    }

    public JournalRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static String formatEntryNo(int year, int number) {
        return String.format("JE-%d-%05d", year, number);
    }

    public Optional<JournalEntry> findById(long id) {
        String sql = """
                SELECT id, entry_no, entry_date, fiscal_year, narration, source_type, source_id, status,
                       reversal_of_id, prepared_by, prepared_at, posted_by, posted_at
                FROM journal_entries WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new JournalEntry(resultSet.getLong("id"), resultSet.getString("entry_no"),
                        resultSet.getDate("entry_date").toLocalDate(), resultSet.getInt("fiscal_year"),
                        resultSet.getString("narration"), JournalSourceType.valueOf(resultSet.getString("source_type")),
                        nullableLong(resultSet, "source_id"), JournalStatus.valueOf(resultSet.getString("status")),
                        nullableLong(resultSet, "reversal_of_id"), nullableLong(resultSet, "prepared_by"),
                        toDateTime(resultSet.getTimestamp("prepared_at")), nullableLong(resultSet, "posted_by"),
                        toDateTime(resultSet.getTimestamp("posted_at")), findLines(connection, id)));
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load the journal entry.", exception);
        }
    }

    public List<JournalEntrySummary> search(JournalSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("""
                SELECT je.id, je.entry_no, je.entry_date, je.narration, je.source_type, je.status,
                       COALESCE((SELECT SUM(jl.debit) FROM journal_lines jl WHERE jl.entry_id = je.id), 0) AS total,
                       prepared.full_name AS prepared_by_name, posted.full_name AS posted_by_name
                FROM journal_entries je
                LEFT JOIN users prepared ON prepared.id = je.prepared_by
                LEFT JOIN users posted ON posted.id = je.posted_by
                WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        if (criteria.dateFrom() != null) {
            sql.append(" AND je.entry_date >= ?");
            parameters.add(Date.valueOf(criteria.dateFrom()));
        }
        if (criteria.dateTo() != null) {
            sql.append(" AND je.entry_date <= ?");
            parameters.add(Date.valueOf(criteria.dateTo()));
        }
        if (criteria.status() != null) {
            sql.append(" AND je.status = ?");
            parameters.add(criteria.status().name());
        }
        if (criteria.sourceType() != null) {
            sql.append(" AND je.source_type = ?");
            parameters.add(criteria.sourceType().name());
        }
        if (criteria.text() != null && !criteria.text().isBlank()) {
            sql.append(" AND (je.narration LIKE ? OR je.entry_no LIKE ?)");
            String term = "%" + criteria.text().strip() + "%";
            parameters.add(term);
            parameters.add(term);
        }
        sql.append(" ORDER BY je.entry_date DESC, je.id DESC LIMIT 1000");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            List<JournalEntrySummary> summaries = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    summaries.add(new JournalEntrySummary(resultSet.getLong("id"), resultSet.getString("entry_no"),
                            resultSet.getDate("entry_date").toLocalDate(), resultSet.getString("narration"),
                            JournalSourceType.valueOf(resultSet.getString("source_type")),
                            JournalStatus.valueOf(resultSet.getString("status")), resultSet.getBigDecimal("total"),
                            resultSet.getString("prepared_by_name"), resultSet.getString("posted_by_name")));
                }
            }
            return summaries;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to search journal entries.", exception);
        }
    }

    public long insertDraft(JournalEntry entry) {
        return inTransaction(connection -> {
            long id = insertHeader(connection, entry, JournalStatus.DRAFT, null, null, null);
            insertLines(connection, id, entry.lines());
            return id;
        }, "Unable to save the journal entry.");
    }

    public void updateDraft(JournalEntry entry) {
        inTransaction(connection -> {
            String sql = """
                    UPDATE journal_entries
                    SET entry_date = ?, fiscal_year = ?, narration = ?, updated_at = ?
                    WHERE id = ? AND status = 'DRAFT'
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setDate(1, Date.valueOf(entry.entryDate()));
                statement.setInt(2, entry.fiscalYear());
                statement.setString(3, entry.narration());
                statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                statement.setLong(5, entry.id());
                if (statement.executeUpdate() != 1) {
                    throw new JournalStateException("Only draft entries can be edited.");
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM journal_lines WHERE entry_id = ?")) {
                statement.setLong(1, entry.id());
                statement.executeUpdate();
            }
            insertLines(connection, entry.id(), entry.lines());
            return null;
        }, "Unable to save the journal entry.");
    }

    public void deleteDraft(long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM journal_entries WHERE id = ? AND status = 'DRAFT'")) {
            statement.setLong(1, id);
            if (statement.executeUpdate() != 1) {
                throw new JournalStateException("Only draft entries can be deleted.");
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to delete the journal entry.", exception);
        }
    }

    /** Re-checks the draft under a row lock, numbers it and marks it POSTED. Returns the entry number. */
    public String post(long id, long postedBy, LocalDateTime postedAt) {
        return inTransaction(connection -> {
            int fiscalYear;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT status, fiscal_year FROM journal_entries WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next() || !JournalStatus.DRAFT.name().equals(resultSet.getString("status"))) {
                        throw new JournalStateException("Only draft entries can be posted.");
                    }
                    fiscalYear = resultSet.getInt("fiscal_year");
                }
            }
            requireBalanced(connection, id);
            String entryNo = formatEntryNo(fiscalYear, nextNumber(connection, fiscalYear));
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE journal_entries
                    SET entry_no = ?, status = 'POSTED', posted_by = ?, posted_at = ?, updated_at = ?
                    WHERE id = ?
                    """)) {
                statement.setString(1, entryNo);
                statement.setLong(2, postedBy);
                statement.setTimestamp(3, Timestamp.valueOf(postedAt));
                statement.setTimestamp(4, Timestamp.valueOf(postedAt));
                statement.setLong(5, id);
                statement.executeUpdate();
            }
            return entryNo;
        }, "Unable to post the journal entry.");
    }

    /** Inserts an already-validated entry and posts it in the same transaction (reversals, documents). */
    public PostedEntry insertPosted(JournalEntry entry, long postedBy, LocalDateTime postedAt) {
        return inTransaction(connection -> {
            String entryNo = formatEntryNo(entry.fiscalYear(), nextNumber(connection, entry.fiscalYear()));
            long id = insertHeader(connection, entry, JournalStatus.POSTED, entryNo, postedBy, postedAt);
            insertLines(connection, id, entry.lines());
            requireBalanced(connection, id);
            return new PostedEntry(id, entryNo);
        }, "Unable to post the journal entry.");
    }

    public boolean isReversed(long id) {
        return exists("SELECT 1 FROM journal_entries WHERE reversal_of_id = ? LIMIT 1", id);
    }

    public boolean hasPostedEntries() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM journal_entries WHERE status = 'POSTED' LIMIT 1");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next();
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check posted entries.", exception);
        }
    }

    /** The newest OPENING entry that has not been reversed (draft or posted), if any. */
    public Optional<JournalEntry> findLiveOpeningEntry() {
        String sql = """
                SELECT je.id FROM journal_entries je
                WHERE je.source_type = 'OPENING'
                  AND NOT EXISTS (SELECT 1 FROM journal_entries r WHERE r.reversal_of_id = je.id)
                ORDER BY je.id DESC LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            long id = resultSet.getLong("id");
            return findById(id);
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load the opening balances.", exception);
        }
    }

    public List<LedgerLine> ledgerLines(long accountId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT je.id AS entry_id, je.entry_no, je.entry_date, je.narration, jl.memo,
                       cc.code AS category_code, c.church_code, jl.debit, jl.credit
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entry_id
                LEFT JOIN collection_categories cc ON cc.id = jl.category_id
                LEFT JOIN churches c ON c.id = jl.church_id
                WHERE jl.account_id = ? AND je.status = 'POSTED' AND je.entry_date >= ? AND je.entry_date <= ?
                ORDER BY je.entry_date, je.posted_at, je.id, jl.line_no
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, accountId);
            statement.setDate(2, Date.valueOf(from));
            statement.setDate(3, Date.valueOf(to));
            List<LedgerLine> lines = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    lines.add(new LedgerLine(resultSet.getLong("entry_id"), resultSet.getString("entry_no"),
                            resultSet.getDate("entry_date").toLocalDate(), resultSet.getString("narration"),
                            resultSet.getString("memo"), resultSet.getString("category_code"),
                            resultSet.getString("church_code"), resultSet.getBigDecimal("debit"),
                            resultSet.getBigDecimal("credit")));
                }
            }
            return lines;
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load the account ledger.", exception);
        }
    }

    public BigDecimal netDebitBefore(long accountId, LocalDate date) {
        String sql = """
                SELECT COALESCE(SUM(jl.debit) - SUM(jl.credit), 0) AS net_debit
                FROM journal_lines jl
                JOIN journal_entries je ON je.id = jl.entry_id
                WHERE jl.account_id = ? AND je.status = 'POSTED' AND je.entry_date < ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, accountId);
            statement.setDate(2, Date.valueOf(date));
            try (ResultSet resultSet = statement.executeQuery()) {
                BigDecimal value = resultSet.next() ? resultSet.getBigDecimal("net_debit") : null;
                return value == null ? BigDecimal.ZERO : value;
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to load the opening balance.", exception);
        }
    }

    private long insertHeader(Connection connection, JournalEntry entry, JournalStatus status, String entryNo,
                              Long postedBy, LocalDateTime postedAt) throws SQLException {
        String sql = """
                INSERT INTO journal_entries (entry_no, entry_date, fiscal_year, narration, source_type, source_id,
                                             status, reversal_of_id, prepared_by, prepared_at, posted_by, posted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, entryNo);
            statement.setDate(2, Date.valueOf(entry.entryDate()));
            statement.setInt(3, entry.fiscalYear());
            statement.setString(4, entry.narration());
            statement.setString(5, entry.sourceType().name());
            setNullableLong(statement, 6, entry.sourceId());
            statement.setString(7, status.name());
            setNullableLong(statement, 8, entry.reversalOfId());
            statement.setLong(9, entry.preparedBy());
            statement.setTimestamp(10, Timestamp.valueOf(entry.preparedAt()));
            setNullableLong(statement, 11, postedBy);
            if (postedAt == null) {
                statement.setNull(12, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(12, Timestamp.valueOf(postedAt));
            }
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new DatabaseException("Journal entry id was not generated.");
        }
    }

    private void insertLines(Connection connection, long entryId, List<JournalLine> lines) throws SQLException {
        String sql = """
                INSERT INTO journal_lines (entry_id, line_no, account_id, debit, credit, category_id, church_id, memo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (JournalLine line : lines) {
                statement.setLong(1, entryId);
                statement.setInt(2, line.lineNo());
                statement.setLong(3, line.accountId());
                statement.setBigDecimal(4, line.debit());
                statement.setBigDecimal(5, line.credit());
                setNullableLong(statement, 6, line.categoryId());
                setNullableLong(statement, 7, line.churchId());
                statement.setString(8, line.memo());
                statement.executeUpdate();
            }
        }
    }

    private List<JournalLine> findLines(Connection connection, long entryId) throws SQLException {
        String sql = """
                SELECT id, line_no, account_id, debit, credit, category_id, church_id, memo
                FROM journal_lines WHERE entry_id = ? ORDER BY line_no
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, entryId);
            List<JournalLine> lines = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    lines.add(new JournalLine(resultSet.getLong("id"), resultSet.getInt("line_no"),
                            resultSet.getLong("account_id"), resultSet.getBigDecimal("debit"),
                            resultSet.getBigDecimal("credit"), nullableLong(resultSet, "category_id"),
                            nullableLong(resultSet, "church_id"), resultSet.getString("memo")));
                }
            }
            return lines;
        }
    }

    private void requireBalanced(Connection connection, long entryId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) AS line_count, COALESCE(SUM(debit), 0) AS debits, COALESCE(SUM(credit), 0) AS credits
                FROM journal_lines WHERE entry_id = ?
                """)) {
            statement.setLong(1, entryId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getInt("line_count") < 2
                        || resultSet.getBigDecimal("debits").signum() == 0
                        || resultSet.getBigDecimal("debits").compareTo(resultSet.getBigDecimal("credits")) != 0) {
                    throw new JournalStateException("The entry is not balanced.");
                }
            }
        }
    }

    private int nextNumber(Connection connection, int fiscalYear) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO journal_entry_sequences (fiscal_year, last_number) VALUES (?, 0)")) {
            statement.setInt(1, fiscalYear);
            statement.executeUpdate();
        }
        int next;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT last_number FROM journal_entry_sequences WHERE fiscal_year = ? FOR UPDATE")) {
            statement.setInt(1, fiscalYear);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new DatabaseException("Journal entry sequence is missing.");
                }
                next = resultSet.getInt("last_number") + 1;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE journal_entry_sequences SET last_number = ? WHERE fiscal_year = ?")) {
            statement.setInt(1, next);
            statement.setInt(2, fiscalYear);
            statement.executeUpdate();
        }
        return next;
    }

    private boolean exists(String sql, long id) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Unable to check the journal entry.", exception);
        }
    }

    private <T> T inTransaction(SqlWork<T> work, String failureMessage) {
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new DatabaseException(failureMessage, exception);
        }
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static LocalDateTime toDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }
}
