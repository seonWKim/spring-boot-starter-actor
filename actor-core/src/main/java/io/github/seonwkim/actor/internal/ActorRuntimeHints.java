package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.serialization.CborSerializable;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * GraalVM native image hints for what the engine reads by name: Pekko's configuration files and the
 * starter's classes that Pekko creates from configuration. Registered in {@code
 * META-INF/spring/aot.factories}; the other modules register their own. Pekko ships no native image
 * metadata, so an application still needs hints for Pekko's own classes (production guide).
 */
public final class ActorRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Typesafe Config files: every jar's {@code reference.conf}, the {@code version} file pekko-actor's
     * includes, and the application's own {@code application.conf} when it has one.
     */
    static final List<String> CONFIG_RESOURCES = List.of("reference.conf", "version.conf", "application.conf");

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        CONFIG_RESOURCES.forEach(hints.resources()::registerPattern);
        // The executor of a dispatcher backed by a Spring bean (spring.actor.dispatchers.<name>.executor).
        hints.reflection().registerType(SpringExecutorConfigurator.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        // Serialization bindings name these (actor-cluster, actor-persistence); Pekko loads them by name.
        hints.reflection().registerType(JsonSerializable.class);
        hints.reflection().registerType(CborSerializable.class);
    }
}
