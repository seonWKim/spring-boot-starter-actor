package io.github.seonwkim.core.shard;

import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * Interface for actors that can be sharded across a cluster. Classes implementing this interface
 * can be registered with the ShardedActorRegistry and will be automatically initialized when the
 * actor system starts in cluster mode.
 *
 * <p>This interface provides a default implementation for the {@link #extractor()} method that
 * uses {@link DefaultShardingMessageExtractor} with 100 shards, which is suitable for most use
 * cases. Override this method if you need a different number of shards or custom routing logic.
 *
 * <p>Example usage:
 * <pre>
 * &#64;Component
 * public class MyShardedActor implements ShardedActor&lt;Command&gt; {
 *     &#64;Override
 *     public EntityTypeKey&lt;Command&gt; typeKey() {
 *         return EntityTypeKey.create(Command.class, "MyActor");
 *     }
 *
 *     &#64;Override
 *     public ShardedActorBehavior&lt;Command&gt; create(EntityContext&lt;Command&gt; ctx) {
 *         return ShardedActorBehavior.builder(ctx)
 *             .setup(entityCtx -&gt; ShardedActorBehavior.receive(Command.class)
 *                 .onMessage(MyCommand.class, this::handleCommand)
 *                 .build())
 *             .build();
 *     }
 * }
 * </pre>
 *
 * @param <T> The type of messages that the actor can handle
 * @see ShardedActorBehavior
 */
public interface ShardedActor<T> {
    /**
     * Returns the entity type key for this actor type. The entity type key is used to identify the
     * actor type in the cluster.
     *
     * @return The entity type key for this actor type
     */
    EntityTypeKey<T> typeKey();

    /**
     * Creates a behavior for the actor given an entity context. This method is called when a new
     * instance of the actor is created.
     *
     * @param ctx The entity context for the actor
     * @return A ShardedActorBehavior for the actor
     */
    ShardedActorBehavior<T> create(EntityContext<T> ctx);

    /**
     * Returns a message extractor for this actor type. The message extractor is used to extract
     * entity IDs and shard IDs from messages.
     *
     * <p>The default implementation uses {@link DefaultShardingMessageExtractor} with 100 shards,
     * which provides good distribution for most use cases (up to ~10,000 entities). Override this
     * method if you need:
     * <ul>
     *   <li>A different number of shards (e.g., for very high-traffic actors)</li>
     *   <li>Custom routing logic for messages</li>
     * </ul>
     *
     * <p><b>Important:</b> The number of shards cannot be changed after deployment without data
     * migration. Choose carefully based on expected entity count.
     *
     * @return A message extractor for this actor type
     */
    default ShardingMessageExtractor<ShardEnvelope<T>, T> extractor() {
        return new DefaultShardingMessageExtractor<>();
    }
}
