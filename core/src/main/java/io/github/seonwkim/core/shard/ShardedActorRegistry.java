package io.github.seonwkim.core.shard;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * Static registry for sharded actors. This class maintains thread-safe mappings between entity type keys
 * and their corresponding sharded actors. All methods are static and use concurrent maps for thread-safety.
 *
 * <p>This is a pure utility class - not managed by Spring. Actors self-register via @PostConstruct.
 */
public final class ShardedActorRegistry {

    private static final ConcurrentMap<EntityTypeKey<?>, SpringShardedActor<?>> registry = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, SpringShardedActor<?>> classIndex = new ConcurrentHashMap<>();

    // Prevent instantiation
    private ShardedActorRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers a sharded actor with the registry. The actor is indexed by both its entity type key
     * and its class for fast lookups. Thread-safe.
     *
     * @param actor The sharded actor to register
     * @param <T> The type of messages that the actor can handle
     */
    public static <T> void register(SpringShardedActor<T> actor) {
        registry.put(actor.typeKey(), actor);
        classIndex.put(actor.getClass(), actor);
    }

    /**
     * Retrieves a sharded actor by its entity type key. Thread-safe.
     *
     * @param typeKey The entity type key of the actor
     * @param <T> The type of messages that the actor can handle
     * @return The sharded actor with the given entity type key, or null if not found
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> SpringShardedActor<T> get(EntityTypeKey<T> typeKey) {
        // Safe cast: registry maintains T type consistency between key and value
        return (SpringShardedActor<T>) registry.get(typeKey);
    }

    /**
     * Retrieves a sharded actor by its class. Thread-safe.
     *
     * @param actorClass The class of the sharded actor
     * @param <T> The type of messages that the actor can handle
     * @return The sharded actor with the given class, or null if not found
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> SpringShardedActor<T> getByClass(Class<? extends SpringShardedActor<T>> actorClass) {
        return (SpringShardedActor<T>) classIndex.get(actorClass);
    }

    /**
     * Returns all registered sharded actors. Thread-safe.
     *
     * @return A collection of all registered sharded actors
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
