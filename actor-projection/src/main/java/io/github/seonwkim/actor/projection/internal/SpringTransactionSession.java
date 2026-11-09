package io.github.seonwkim.actor.projection.internal;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.projection.jdbc.JdbcSession;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * A projection's session as a Spring transaction. Pekko creates it, runs the handler and saves the
 * offset, then commits and closes it, all on one thread: repositories and {@code JdbcTemplate} in
 * the handler join the transaction, and the offset is written on its connection.
 */
final class SpringTransactionSession implements JdbcSession {

    private static final TransactionDefinition NEW_TRANSACTION =
            new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final TransactionStatus transaction;

    SpringTransactionSession(PlatformTransactionManager transactionManager, DataSource dataSource) {
        this.transactionManager = transactionManager;
        this.dataSource = dataSource;
        this.transaction = transactionManager.getTransaction(NEW_TRANSACTION);
    }

    @Override
    public <R> R withConnection(Function<Connection, R> func) throws Exception {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return func.apply(connection);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    @Override
    public void commit() throws SQLException {
        if (!transaction.isCompleted()) {
            transactionManager.commit(transaction);
        }
    }

    @Override
    public void rollback() throws SQLException {
        if (!transaction.isCompleted()) {
            transactionManager.rollback(transaction);
        }
    }

    /** Rolls back what wasn't committed. */
    @Override
    public void close() throws SQLException {
        rollback();
    }
}
