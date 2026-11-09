package io.github.seonwkim.actor.persistence.internal;

import com.typesafe.config.Config;
import com.zaxxer.hikari.HikariConfigMXBean;
import io.github.seonwkim.actor.internal.SpringBeansSetup;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import javax.annotation.Nullable;
import javax.sql.DataSource;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.jdbc.db.SlickDatabase;
import org.apache.pekko.persistence.jdbc.db.SlickDatabaseProvider;
import scala.Option;
import slick.jdbc.H2Profile$;
import slick.jdbc.JdbcBackend$;
import slick.jdbc.JdbcBackend.JdbcDatabaseDef;
import slick.jdbc.JdbcProfile;
import slick.jdbc.MySQLProfile$;
import slick.jdbc.OracleProfile$;
import slick.jdbc.PostgresProfile$;
import slick.jdbc.SQLServerProfile$;
import slick.util.AsyncExecutor;
import slick.util.AsyncExecutor$;

/**
 * Gives the journal, the snapshot store and the read journal the application's {@code DataSource}
 * bean instead of a connection pool of their own. Pekko creates it by reflection, once per actor
 * system, from {@code pekko-persistence-jdbc.database-provider-fqcn}.
 */
public final class DataSourceSlickDatabaseProvider implements SlickDatabaseProvider {

    /** Threads that run the JDBC calls, when the pool size is unknown. */
    private static final int DEFAULT_THREADS = 10;

    private final ActorSystem system;

    @Nullable private SlickDatabase database;

    public DataSourceSlickDatabaseProvider(ActorSystem system) {
        this.system = system;
    }

    @Override
    public synchronized SlickDatabase database(Config config) {
        // The journal, the snapshot store and the read journal share one database.
        SlickDatabase result = database;
        if (result == null) {
            result = create();
            database = result;
        }
        return result;
    }

    private SlickDatabase create() {
        DataSource dataSource = system.settings()
                .setup()
                .get(SpringBeansSetup.class)
                .orElseThrow(() -> new IllegalStateException(
                        "actor-persistence needs an actor system built by the starter (it reads the DataSource bean)"))
                .beanFactory()
                .getBean(DataSource.class);
        int threads = dataSource instanceof HikariConfigMXBean hikari ? hikari.getMaximumPoolSize() : DEFAULT_THREADS;
        AsyncExecutor executor =
                AsyncExecutor$.MODULE$.apply("spring-actor-persistence", threads, threads, 1000, threads);
        JdbcDatabaseDef db =
                JdbcBackend$.MODULE$.Database().forDataSource(dataSource, Option.apply(threads), executor, false);
        // The DataSource is the application's: never close it, only the threads.
        system.registerOnTermination(executor::close);
        return new ApplicationDatabase(db, profile(dataSource));
    }

    static JdbcProfile profile(DataSource dataSource) {
        String product;
        try (Connection connection = dataSource.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the database type of the DataSource", e);
        }
        String name = product.toLowerCase(Locale.ROOT);
        if (name.contains("h2")) {
            return H2Profile$.MODULE$;
        }
        if (name.contains("postgres")) {
            return PostgresProfile$.MODULE$;
        }
        if (name.contains("mysql") || name.contains("mariadb")) {
            return MySQLProfile$.MODULE$;
        }
        if (name.contains("oracle")) {
            return OracleProfile$.MODULE$;
        }
        if (name.contains("sql server")) {
            return SQLServerProfile$.MODULE$;
        }
        throw new IllegalStateException("actor-persistence supports H2, PostgreSQL, MySQL, MariaDB, Oracle and SQL"
                + " Server; the DataSource is " + product);
    }

    private record ApplicationDatabase(JdbcDatabaseDef database, JdbcProfile profile) implements SlickDatabase {
        @Override
        public boolean allowShutdown() {
            return false;
        }
    }
}
