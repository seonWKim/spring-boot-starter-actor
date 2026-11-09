package io.github.seonwkim.actor.persistence.internal;

import com.typesafe.config.Config;
import io.github.seonwkim.actor.internal.SpringBeansSetup;
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
        int threads = JdbcDatabase.poolSize(dataSource);
        AsyncExecutor executor =
                AsyncExecutor$.MODULE$.apply("spring-actor-persistence", threads, threads, 1000, threads);
        JdbcDatabaseDef db =
                JdbcBackend$.MODULE$.Database().forDataSource(dataSource, Option.apply(threads), executor, false);
        // The DataSource is the application's: never close it, only the threads.
        system.registerOnTermination(executor::close);
        return new ApplicationDatabase(db, profile(dataSource));
    }

    private static JdbcProfile profile(DataSource dataSource) {
        return switch (JdbcDatabase.of(dataSource)) {
            case H2 -> H2Profile$.MODULE$;
            case POSTGRES -> PostgresProfile$.MODULE$;
            case MYSQL -> MySQLProfile$.MODULE$;
            case ORACLE -> OracleProfile$.MODULE$;
            case SQL_SERVER -> SQLServerProfile$.MODULE$;
        };
    }

    private record ApplicationDatabase(JdbcDatabaseDef database, JdbcProfile profile) implements SlickDatabase {
        @Override
        public boolean allowShutdown() {
            return false;
        }
    }
}
