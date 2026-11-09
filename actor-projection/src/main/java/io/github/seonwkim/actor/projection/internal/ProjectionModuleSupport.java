package io.github.seonwkim.actor.projection.internal;

import io.github.seonwkim.actor.internal.ModuleSupport;
import io.github.seonwkim.actor.persistence.internal.ConfigMaps;
import io.github.seonwkim.actor.persistence.internal.JdbcDatabase;
import java.util.Map;
import javax.annotation.Nullable;
import javax.sql.DataSource;
import org.springframework.beans.factory.BeanFactory;

/**
 * The projection module of the engine: Pekko JDBC projections store their offsets on the
 * application's {@code DataSource}, with the SQL dialect of its database and as many threads as it
 * has connections.
 */
public final class ProjectionModuleSupport implements ModuleSupport {

    /** The offset tables (pekko_projection_offset_store, pekko_projection_management), per database. */
    public static final String SCHEMA_LOCATION =
            "classpath:io/github/seonwkim/actor/projection/schema-@@platform@@.sql";

    @Override
    public Map<String, Object> withDefaults(Map<String, Object> config, @Nullable BeanFactory beans) {
        DataSource dataSource =
                beans == null ? null : beans.getBeanProvider(DataSource.class).getIfUnique();
        if (dataSource == null) {
            return config;
        }
        Map<String, Object> result = ConfigMaps.copy(config);
        Map<String, Object> jdbc =
                ConfigMaps.child(ConfigMaps.child(ConfigMaps.child(result, "pekko"), "projection"), "jdbc");
        if (!jdbc.containsKey("dialect")) {
            jdbc.put("dialect", dialect(JdbcDatabase.of(dataSource)));
        }
        ConfigMaps.child(ConfigMaps.child(jdbc, "blocking-jdbc-dispatcher"), "thread-pool-executor")
                .putIfAbsent("fixed-pool-size", JdbcDatabase.poolSize(dataSource));
        return result;
    }

    private static String dialect(JdbcDatabase database) {
        return switch (database) {
            case H2 -> "h2-dialect";
            case POSTGRES -> "postgres-dialect";
            case MYSQL -> "mysql-dialect";
            case ORACLE -> "oracle-dialect";
            case SQL_SERVER -> "mssql-dialect";
        };
    }
}
