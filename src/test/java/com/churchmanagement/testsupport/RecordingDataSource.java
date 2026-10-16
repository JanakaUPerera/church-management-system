package com.churchmanagement.testsupport;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A DataSource for unit tests. It records each prepared SQL string, the parameters bound to the last
 * statement, and whether the connection was committed or rolled back. Queries return no rows.
 */
public class RecordingDataSource implements DataSource {
    private final List<String> sqls = new ArrayList<>();
    private final List<Object> lastValues = new ArrayList<>();
    private boolean autoCommit = true;
    private boolean committed;
    private boolean rolledBack;

    public List<String> sqls() {
        return sqls;
    }

    public String lastSql() {
        return sqls.isEmpty() ? null : sqls.getLast();
    }

    public List<Object> lastValues() {
        return lastValues;
    }

    public boolean committed() {
        return committed;
    }

    public boolean rolledBack() {
        return rolledBack;
    }

    @Override
    public Connection getConnection() {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "prepareStatement" -> {
                        sqls.add((String) args[0]);
                        lastValues.clear();
                        yield statementProxy();
                    }
                    case "getAutoCommit" -> autoCommit;
                    case "setAutoCommit" -> {
                        autoCommit = (boolean) args[0];
                        yield null;
                    }
                    case "commit" -> {
                        committed = true;
                        yield null;
                    }
                    case "rollback" -> {
                        rolledBack = true;
                        yield null;
                    }
                    default -> defaultValue(method.getReturnType());
                });
    }

    private PreparedStatement statementProxy() {
        return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer index) {
                        while (lastValues.size() < index) {
                            lastValues.add(null);
                        }
                        lastValues.set(index - 1, args[1]);
                        return null;
                    }
                    if ("executeUpdate".equals(name)) {
                        return 1;
                    }
                    if ("executeQuery".equals(name) || "getGeneratedKeys".equals(name)) {
                        return emptyResultSet();
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private ResultSet emptyResultSet() {
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> "next".equals(method.getName()) ? false : defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    @Override
    public Connection getConnection(String username, String password) {
        return getConnection();
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
    }

    @Override
    public void setLoginTimeout(int seconds) {
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() {
        return Logger.getGlobal();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("Not a wrapper");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }
}
