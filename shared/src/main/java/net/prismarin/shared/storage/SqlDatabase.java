package net.prismarin.shared.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTransactionRollbackException;
import net.prismarin.shared.config.SqlSettings;
import org.mariadb.jdbc.Driver;
import org.sqlite.JDBC;

public final class SqlDatabase implements AutoCloseable {

    private static final int MAX_ATTEMPTS = 3;

    private final HikariDataSource dataSource;
    private final SqlDialect dialect;

    private SqlDatabase(HikariConfig config, SqlDialect dialect) {
        this.dataSource = new HikariDataSource(config);
        this.dialect = dialect;
    }

    public static SqlDatabase mysql(SqlSettings settings) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("prismarin-mysql");
        config.setDriverClassName(Driver.class.getName());
        config.setJdbcUrl("jdbc:mariadb://" + settings.host() + ":" + settings.port() + "/" + settings.database());
        config.setUsername(settings.username());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(settings.poolSize());
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        settings.properties().forEach(config::addDataSourceProperty);
        return new SqlDatabase(config, SqlDialect.MYSQL);
    }

    public static SqlDatabase sqlite(Path file) {
        Path absolute = file.toAbsolutePath();
        try {
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("prismarin-sqlite");
        config.setDriverClassName(JDBC.class.getName());
        config.setJdbcUrl("jdbc:sqlite:" + absolute);
        config.setMaximumPoolSize(1);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");
        config.addDataSourceProperty("busy_timeout", "10000");
        config.addDataSourceProperty("transaction_mode", "IMMEDIATE");
        return new SqlDatabase(config, SqlDialect.SQLITE);
    }

    SqlDialect dialect() {
        return dialect;
    }

    <T> T execute(SqlWork<T> work) {
        return run(work, false);
    }

    <T> T transaction(SqlWork<T> work) {
        return run(work, true);
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private <T> T run(SqlWork<T> work, boolean transactional) {
        for (int attempt = 1; ; attempt++) {
            try (Connection connection = dataSource.getConnection()) {
                return transactional ? inTransaction(connection, work) : work.run(connection);
            } catch (SQLException e) {
                if (attempt >= MAX_ATTEMPTS || !retryable(e)) {
                    throw new StorageException("SQL storage operation failed: " + e.getMessage(), e);
                }
            }
        }
    }

    private static <T> T inTransaction(Connection connection, SqlWork<T> work) throws SQLException {
        connection.setAutoCommit(false);
        try {
            T result = work.run(connection);
            connection.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        }
    }

    private static boolean retryable(SQLException e) {
        String state = e.getSQLState();
        return e instanceof SQLTransactionRollbackException
                || e instanceof SQLIntegrityConstraintViolationException
                || state != null && (state.startsWith("40") || state.startsWith("23"));
    }

    @FunctionalInterface
    interface SqlWork<T> {

        T run(Connection connection) throws SQLException;
    }
}
