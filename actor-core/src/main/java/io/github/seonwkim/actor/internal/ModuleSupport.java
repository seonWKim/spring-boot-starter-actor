package io.github.seonwkim.actor.internal;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.Behavior;

/**
 * A module of the starter that adds to the engine, such as {@code actor-persistence}, found through
 * {@link ServiceLoader}. The cluster module has its own {@link ClusterSupport}.
 */
public interface ModuleSupport {

    /** {@code config} plus this module's defaults where the application hasn't set them. */
    default Map<String, Object> withDefaults(Map<String, Object> config) {
        return config;
    }

    /**
     * The engine behavior for a definition passed to {@code SpringActorBehavior.ofModule}, or null
     * when the definition is not this module's.
     */
    default <C> @Nullable Behavior<C> toBehavior(Object moduleDefinition) {
        return null;
    }

    /** The modules on the classpath. */
    static List<ModuleSupport> all() {
        return Loaded.MODULES;
    }

    /** Loaded once: the classpath doesn't change. */
    final class Loaded {
        private static final List<ModuleSupport> MODULES =
                ServiceLoader.load(ModuleSupport.class, ModuleSupport.class.getClassLoader()).stream()
                        .map(ServiceLoader.Provider::get)
                        .toList();

        private Loaded() {}
    }
}
