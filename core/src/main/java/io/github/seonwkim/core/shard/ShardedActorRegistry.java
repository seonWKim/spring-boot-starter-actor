package io.github.seonwkim.core.shard;

import io.github.seonwkim.core.ActorConfiguration;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * Static registry for sharded actors. Maintains thread-safe mappings between entity type keys
 * and sharded actor instances. All operations are thread-safe via {@link ConcurrentHashMap}.
 *
 * <p>Pure utility class - not managed by Spring. Actors are automatically registered by
 * {@link ActorConfiguration#actorRegistrationBeanPostProcessor()}.
 */
public final class ShardedActorRegistry {

    private static final ConcurrentMap<EntityTypeKey<?>, SpringShardedActor<?>> registry = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, SpringShardedActor<?>> classIndex = new ConcurrentHashMap<>();

    // Prevent instantiation
    private ShardedActorRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers a sharded actor, indexed by both type key and class.
     *
     * <p><b>Note:</b> In most cases, you don't need to call this method directly.
     * Sharded actors implementing {@link SpringShardedActor} and annotated with
     * {@code @Component} are automatically registered by the framework.
     *
     * <p>Manual registration may be useful for:
     * <ul>
     *   <li>Registering actors programmatically outside of Spring's component scanning</li>
     *   <li>Advanced testing scenarios where you need fine-grained control</li>
     *   <li>Dynamic actor types generated at runtime</li>
     * </ul>
     *
     * @param <T> The command type that the sharded actor handles
     * @param actor Sharded actor to register
     */
    public static <T> void register(SpringShardedActor<T> actor) {
        registry.put(actor.typeKey(), actor);
        classIndex.put(actor.getClass(), actor);
    }

    /**
     * Retrieves a sharded actor by its entity type key.
     *
     * @param <T> The command type that the sharded actor handles
     * @param typeKey Entity type key of the actor
     * @return Sharded actor, or null if not found
     */
    @Nullable @SuppressWarnings("unchecked")
    public static <T> SpringShardedActor<T> get(EntityTypeKey<T> typeKey) {
        // Safe cast: registry maintains T type consistency between key and value
        return (SpringShardedActor<T>) registry.get(typeKey);
    }

    /**
     * Retrieves a sharded actor by its class.
     *
     * @param <T> The command type that the sharded actor handles
     * @param actorClass Class of the sharded actor
     * @return Sharded actor, or null if not found
     */
    @Nullable @SuppressWarnings("unchecked")
    public static <T> SpringShardedActor<T> getByClass(Class<? extends SpringShardedActor<T>> actorClass) {
        return (SpringShardedActor<T>) classIndex.get(actorClass);
    }

    /**
     * Returns all registered sharded actors.
     */
    public static Collection<SpringShardedActor<?>> getAll() {
        return registry.values();
    }

    /**
     * Clears all registrations. <b>For testing use only.</b>
     *
     * <p>This method is provided to ensure test isolation by clearing the static registry
     * between test runs. It should not be used in production code.
     *
     * <p>Typical usage in tests:
     * <pre>{@code
     * @BeforeEach
     * public void setUp() {
     *     ShardedActorRegistry.clear();
     * }
     * }</pre>
     */
    public static void clear() {
        registry.clear();
        classIndex.clear();
    }
}
