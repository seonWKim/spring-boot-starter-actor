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

	/**
	 * Singleton instance of the registry. This instance can be used when a shared registry is needed.
	 */
	public static ShardedActorRegistry INSTANCE = new ShardedActorRegistry();

	/**
	 * Registers a sharded actor with the registry. The actor is indexed by its entity type key.
	 *
	 * @param actor The sharded actor to register
	 * @param <T> The type of messages that the actor can handle
	 */
	public <T> void register(ShardedActor<T> actor) {
		registry.put(actor.typeKey(), actor);
	}

	/**
	 * Retrieves a sharded actor by its entity type key.
	 *
	 * @param typeKey The entity type key of the actor
	 * @param <T> The type of messages that the actor can handle
	 * @return The sharded actor with the given entity type key, or null if not found
	 */
	@SuppressWarnings("unchecked")
	public <T> ShardedActor<T> get(EntityTypeKey<T> typeKey) {
		return (ShardedActor<T>) registry.get(typeKey);
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
