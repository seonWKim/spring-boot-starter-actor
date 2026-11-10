package io.github.seonwkim.actor.autoconfigure;

import io.github.seonwkim.actor.jdbc.JdbcPeerRegistry;
import io.github.seonwkim.actor.persistence.internal.PersistenceModuleSupport;
import io.github.seonwkim.actor.projection.internal.ProjectionModuleSupport;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * GraalVM native image hints for the schema files {@link SchemaScriptInitializer} runs. Registered in
 * {@code META-INF/spring/aot.factories}. The locations are compile-time constants, so this class loads
 * without the modules on the classpath.
 */
public final class SchemaScriptRuntimeHints implements RuntimeHintsRegistrar {

    static final List<String> SCHEMA_LOCATIONS = List.of(
            JdbcPeerRegistry.SCHEMA_LOCATION,
            PersistenceModuleSupport.SCHEMA_LOCATION,
            ProjectionModuleSupport.SCHEMA_LOCATION);

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        // classpath:a/schema-@@platform@@.sql -> a/schema-*.sql, one file per database.
        for (String location : SCHEMA_LOCATIONS) {
            hints.resources().registerPattern(location.replace("classpath:", "").replace("@@platform@@", "*"));
        }
    }
}
