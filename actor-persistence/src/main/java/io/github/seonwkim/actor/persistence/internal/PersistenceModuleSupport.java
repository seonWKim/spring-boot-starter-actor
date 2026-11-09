package io.github.seonwkim.actor.persistence.internal;

import io.github.seonwkim.actor.internal.ModuleSupport;
import io.github.seonwkim.actor.persistence.SpringEventSourcedBehavior;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.Behavior;
import org.springframework.beans.factory.BeanFactory;

/**
 * The persistence module of the engine: runs {@link SpringEventSourcedBehavior}s, with the journal
 * and the snapshots of Pekko Persistence JDBC on the application's {@code DataSource}.
 */
public final class PersistenceModuleSupport implements ModuleSupport {

    /** The tables (event_journal, event_tag, snapshot), per database: h2, postgresql, mysql, mariadb. */
    public static final String SCHEMA_LOCATION =
            "classpath:io/github/seonwkim/actor/persistence/schema-@@platform@@.sql";

    static final String JOURNAL = "jdbc-journal";
    static final String SNAPSHOT_STORE = "jdbc-snapshot-store";

    @Override
    public Map<String, Object> withDefaults(Map<String, Object> config, @Nullable BeanFactory beans) {
        Map<String, Object> result = ConfigMaps.copy(config);
        Map<String, Object> pekko = ConfigMaps.child(result, "pekko");
        Map<String, Object> persistence = ConfigMaps.child(pekko, "persistence");
        ConfigMaps.child(persistence, "journal").putIfAbsent("plugin", JOURNAL);
        ConfigMaps.child(persistence, "snapshot-store").putIfAbsent("plugin", SNAPSHOT_STORE);
        ConfigMaps.child(result, "pekko-persistence-jdbc")
                .putIfAbsent("database-provider-fqcn", DataSourceSlickDatabaseProvider.class.getName());
        // Events and snapshots are serialized also in a local actor system.
        Map<String, Object> bindings = ConfigMaps.child(ConfigMaps.child(pekko, "actor"), "serialization-bindings");
        bindings.putIfAbsent("io.github.seonwkim.actor.serialization.JsonSerializable", "jackson-json");
        bindings.putIfAbsent("io.github.seonwkim.actor.serialization.CborSerializable", "jackson-cbor");
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C> @Nullable Behavior<C> toBehavior(Object moduleDefinition) {
        if (moduleDefinition instanceof SpringEventSourcedBehavior.Definition<?, ?, ?> definition) {
            return PekkoEventSourcedBehavior.create((SpringEventSourcedBehavior.Definition<C, ?, ?>) definition);
        }
        return null;
    }
}
