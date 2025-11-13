package io.github.seonwkim.core.shard;

import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;

/**
 * Default implementation of {@link SpringShardedActorContext}.
 *
 * <p>This class provides a simple wrapper around Pekko's {@link EntityContext} without adding
 * any additional functionality.
 *
 * @param <T> The type of messages that the sharded actor can handle
 * @see SpringShardedActorContext
 */
public final class DefaultSpringShardedActorContext<T> extends SpringShardedActorContext<T> {

    /**
     * Constructs a new DefaultSpringShardedActorContext wrapping the given entity context.
     *
     * @param entityContext The Pekko entity context to wrap
     */
    public DefaultSpringShardedActorContext(EntityContext<T> entityContext) {
        super(entityContext);
    }
}
