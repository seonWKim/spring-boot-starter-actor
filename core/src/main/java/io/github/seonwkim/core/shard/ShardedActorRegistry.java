package io.github.seonwkim.core.shard;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * Registry for sharded actors. This class maintains a mapping between entity type keys and their
 * corresponding sharded actors. It provides methods for registering, retrieving, and listing
 * sharded actors.
 */
public class ShardedActorRegistry {
    private final Map<EntityTypeKey<?>, ShardedActor<?>> registry = new HashMap<>();
    private final Map<Class<?>, ShardedActor<?>> classIndex = new HashMap<>();

    /**
     * Singleton instance of the registry. This instance can be used when a shared registry is needed.
     */
    public static final ShardedActorRegistry INSTANCE = new ShardedActorRegistry();

    /**
     * Registers a sharded actor with the registry. The actor is indexed by both its entity type key
     * and its class for fast lookups.
     *
     * @param actor The sharded actor to register
     * @param <T> The type of messages that the actor can handle
     */
    public <T> void register(ShardedActor<T> actor) {
        registry.put(actor.typeKey(), actor);
        classIndex.put(actor.getClass(), actor);
    }

    /**
     * Retrieves a sharded actor by its entity type key.
     *
     * @param typeKey The entity type key of the actor
     * @param <T> The type of messages that the actor can handle
     * @return The sharded actor with the given entity type key, or null if not found
     */
    public <T> ShardedActor<T> get(EntityTypeKey<T> typeKey) {
        // Safe cast: registry maintains T type consistency between key and value
        @SuppressWarnings("unchecked")
        ShardedActor<T> actor = (ShardedActor<T>) registry.get(typeKey);
        return actor;
    }

    /**
     * Retrieves a sharded actor by its class.
     *
     * @param actorClass The class of the sharded actor
     * @param <T> The type of messages that the actor can handle
     * @return The sharded actor with the given class, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> ShardedActor<T> getByClass(Class<? extends ShardedActor<T>> actorClass) {
        return (ShardedActor<T>) classIndex.get(actorClass);
    }

    /**
     * Returns all registered sharded actors.
     *
     * @return A collection of all registered sharded actors
     */
    public Collection<ShardedActor<?>> getAll() {
        return registry.values();
    }
}
