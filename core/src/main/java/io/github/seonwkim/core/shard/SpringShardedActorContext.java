package io.github.seonwkim.core.shard;

import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;

/**
 * Represents the context for a sharded actor in the Spring Actor system.
 *
 * <p>This abstract class wraps Pekko's {@link EntityContext} to provide a consistent abstraction
 * layer for sharded actors.
 *
 * @param <T> The type of messages that the sharded actor can handle
 * @see DefaultSpringShardedActorContext
 * @see SpringShardedActor
 */
public abstract class SpringShardedActorContext<T> {

    private final EntityContext<T> entityContext;

    /**
     * Constructs a new SpringShardedActorContext wrapping the given entity context.
     *
     * @param entityContext The Pekko entity context to wrap
     */
    protected SpringShardedActorContext(EntityContext<T> entityContext) {
        this.entityContext = entityContext;
    }

    /**
     * Returns the entity ID for this sharded actor instance.
     *
     * <p>The entity ID uniquely identifies this actor instance within its actor type.
     * Multiple actor instances of the same type will have different entity IDs.
     *
     * @return The unique entity identifier
     */
    public String getEntityId() {
        return entityContext.getEntityId();
    }

    /**
     * Returns the underlying Pekko entity context.
     *
     * <p>This method provides direct access to the wrapped entity context for advanced use cases
     * that require Pekko-specific functionality not exposed through this abstraction.
     *
     * @return The underlying entity context
     */
    public EntityContext<T> getEntityContext() {
        return entityContext;
    }
}
