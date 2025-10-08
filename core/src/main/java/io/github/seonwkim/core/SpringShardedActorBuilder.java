package io.github.seonwkim.core;

import io.github.seonwkim.core.shard.ShardedActor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * A fluent builder for creating references to sharded actors. This builder simplifies the process
 * of getting a reference to a sharded actor entity by providing a clean, fluent API.
 *
 * <p>Example usage:
 *
 * <pre>
 * var counter = actorSystem.sharded(CounterActor.class)
 *     .withId("counter-123")
 *     .get();
 * </pre>
 *
 * @param <T> The type of commands that the sharded actor can handle
 */
public class SpringShardedActorBuilder<T> {

    private final SpringActorSystem actorSystem;
    private final Class<? extends ShardedActor<T>> actorClass;
    private String entityId;
    private EntityTypeKey<T> typeKey;

    /**
     * Creates a new SpringShardedActorBuilder.
     *
     * @param actorSystem The Spring actor system
     * @param actorClass The class of the sharded actor
     */
    public SpringShardedActorBuilder(SpringActorSystem actorSystem, Class<? extends ShardedActor<T>> actorClass) {
        this.actorSystem = actorSystem;
        this.actorClass = actorClass;
        this.typeKey = resolveTypeKey(actorClass);
    }

    /**
     * Sets the entity ID for the sharded actor reference.
     *
     * @param entityId The entity ID
     * @return This builder for method chaining
     */
    public SpringShardedActorBuilder<T> withId(String entityId) {
        if (entityId == null || entityId.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity ID cannot be null or empty");
        }
        this.entityId = entityId;
        return this;
    }

    /**
     * Optionally sets a custom EntityTypeKey. If not set, the builder will attempt to resolve it from
     * the actor class.
     *
     * @param typeKey The entity type key
     * @return This builder for method chaining
     */
    public SpringShardedActorBuilder<T> withTypeKey(EntityTypeKey<T> typeKey) {
        if (typeKey == null) {
            throw new IllegalArgumentException("Type key cannot be null");
        }
        this.typeKey = typeKey;
        return this;
    }

    /**
     * Builds and returns the SpringShardedActorRef.
     *
     * @return A SpringShardedActorRef to the sharded actor entity
     * @throws IllegalStateException If the entity ID has not been set or if cluster sharding is not
     *     configured
     */
    public SpringShardedActorRef<T> get() {
        if (entityId == null) {
            throw new IllegalStateException("Entity ID must be set using withId() before calling get()");
        }

        if (typeKey == null) {
            throw new IllegalStateException("Unable to resolve EntityTypeKey for "
                    + actorClass.getName()
                    + ". Please ensure the actor has a static TYPE_KEY field or use withTypeKey() to provide one.");
        }

        if (actorSystem.getClusterSharding() == null) {
            throw new IllegalStateException("Cluster sharding not configured");
        }

        final EntityRef<T> entityRef = actorSystem.getClusterSharding().entityRefFor(typeKey, entityId);
        return new SpringShardedActorRef<>(actorSystem.getRaw().scheduler(), entityRef);
    }

    /**
     * Attempts to resolve the EntityTypeKey from the actor class. This method looks for a static
     * field named TYPE_KEY in the actor class.
     *
     * @param actorClass The sharded actor class
     * @return The resolved EntityTypeKey, or null if not found
     */
    // TODO: Use ShardedActorRegistry to retrieve EntityTypeKey based on actorClass
    @SuppressWarnings("unchecked")
    private EntityTypeKey<T> resolveTypeKey(Class<? extends ShardedActor<T>> actorClass) {
        try {
            // First, try to instantiate the actor and call typeKey() method
            ShardedActor<T> instance = actorClass.getDeclaredConstructor().newInstance();
            return instance.typeKey();
        } catch (Exception e) {
            // If instantiation fails, try to find a static TYPE_KEY field
            try {
                java.lang.reflect.Field typeKeyField = actorClass.getField("TYPE_KEY");
                if (EntityTypeKey.class.isAssignableFrom(typeKeyField.getType())) {
                    return (EntityTypeKey<T>) typeKeyField.get(null);
                }
            } catch (Exception ex) {
                // Field not found or not accessible
            }
        }

        return null;
    }
}
