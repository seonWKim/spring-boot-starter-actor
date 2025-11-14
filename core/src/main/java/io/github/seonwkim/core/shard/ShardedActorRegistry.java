package io.github.seonwkim.core.shard;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

import io.github.seonwkim.core.ActorConfiguration;
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
     * Clears all registrations. Primarily for testing.
     */
    public static void clear() {
        registry.clear();
        classIndex.clear();
    }
}
