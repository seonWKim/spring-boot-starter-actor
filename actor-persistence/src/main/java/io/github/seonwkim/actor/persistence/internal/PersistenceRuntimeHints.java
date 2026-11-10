package io.github.seonwkim.actor.persistence.internal;

import javax.annotation.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * GraalVM native image hints for the persistence module: the database provider that Pekko Persistence
 * JDBC creates by name. The schema scripts' hints are in actor-autoconfigure, which runs them.
 */
public final class PersistenceRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        hints.reflection()
                .registerType(DataSourceSlickDatabaseProvider.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
    }
}
