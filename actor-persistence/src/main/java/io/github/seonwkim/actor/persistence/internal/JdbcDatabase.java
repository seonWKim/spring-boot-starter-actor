package io.github.seonwkim.actor.persistence.internal;

import com.zaxxer.hikari.HikariConfigMXBean;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import javax.sql.DataSource;

/** The databases the journal and the projection offsets run on, read from a DataSource. */
public enum JdbcDatabase {
    H2,
    POSTGRES,
    MYSQL,
    ORACLE,
    SQL_SERVER;

    /** Threads for the JDBC calls when the pool size is unknown. */
    private static final int DEFAULT_POOL_SIZE = 10;

    /** The database behind {@code dataSource}, from its metadata (opens one connection). */
    public static JdbcDatabase of(DataSource dataSource) {
        String product;
        try (Connection connection = dataSource.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the database type of the DataSource", e);
        }
        String name = product.toLowerCase(Locale.ROOT);
        if (name.contains("h2")) {
            return H2;
        }
        if (name.contains("postgres")) {
            return POSTGRES;
        }
        if (name.contains("mysql") || name.contains("mariadb")) {
            return MYSQL;
        }
        if (name.contains("oracle")) {
            return ORACLE;
        }
        if (name.contains("sql server")) {
            return SQL_SERVER;
        }
        throw new IllegalStateException("actor-persistence supports H2, PostgreSQL, MySQL, MariaDB, Oracle and SQL"
                + " Server; the DataSource is " + product);
    }

    /** How many connections {@code dataSource} has: the Hikari pool size, else 10. */
    public static int poolSize(DataSource dataSource) {
        return dataSource instanceof HikariConfigMXBean hikari ? hikari.getMaximumPoolSize() : DEFAULT_POOL_SIZE;
    }
}
