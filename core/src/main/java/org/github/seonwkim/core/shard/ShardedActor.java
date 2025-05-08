package org.github.seonwkim.core.shard;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * Interface for actors that can be sharded across a cluster.
 * Classes implementing this interface can be registered with the ShardedActorRegistry
 * and will be automatically initialized when the actor system starts in cluster mode.
 *
 * @param <T> The type of messages that the actor can handle
 */
public interface ShardedActor<T> {
    /**
     * Returns the entity type key for this actor type.
     * The entity type key is used to identify the actor type in the cluster.
     *
     * @return The entity type key for this actor type
     */
    EntityTypeKey<T> typeKey();

    /**
     * Creates a behavior for the actor given an entity context.
     * This method is called when a new instance of the actor is created.
     *
     * @param ctx The entity context for the actor
     * @return A behavior for the actor
     */
    Behavior<T> create(EntityContext<T> ctx);

    /**
     * Returns a message extractor for this actor type.
     * The message extractor is used to extract entity IDs and shard IDs from messages.
     *
     * @return A message extractor for this actor type
     */
    ShardingMessageExtractor<ShardEnvelope<T>, T> extractor();
}
