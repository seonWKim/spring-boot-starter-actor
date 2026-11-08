package io.github.seonwkim.core.shard;

import java.util.Optional;
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
 * public class MyShardedActor implements SpringShardedActor&lt;Command&gt; {
 *     &#64;Override
 *     public EntityTypeKey&lt;Command&gt; typeKey() {
 *         return EntityTypeKey.create(Command.class, "MyActor");
 *     }
 *
 *     &#64;Override
 *     public SpringShardedActorBehavior&lt;Command&gt; create(SpringShardedActorContext&lt;Command&gt; ctx) {
 *         return SpringShardedActorBehavior.builder(ctx)
 *             .setup(shardedCtx -&gt; SpringShardedActorBehavior.receive(Command.class)
 *                 .onMessage(MyCommand.class, this::handleCommand)
 *                 .build())
 *             .build();
 *     }
 * }
 * </pre>
 *
 * @param <T> The type of messages that the actor can handle
 * @see SpringShardedActorBehavior
 * @see SpringShardedActorContext
 */
public interface SpringShardedActor<T> {
    /**
     * Returns the entity type key for this actor type. The entity type key is used to identify the
     * actor type in the cluster.
     *
     * @return The entity type key for this actor type
     */
    EntityTypeKey<T> typeKey();

    /**
     * Creates a context for this sharded actor. This method is called when a new instance of the
     * actor is created, before {@link #create(SpringShardedActorContext)}.
     *
     * @param entityContext The Pekko entity context
     * @return A SpringShardedActorContext for this actor instance
     */
    default SpringShardedActorContext<T> createContext(EntityContext<T> entityContext) {
        return new DefaultSpringShardedActorContext<>(entityContext);
    }

    /**
     * Creates a behavior for the actor given a sharded actor context. This method is called when
     * a new instance of the actor is created.
     *
     * @param ctx The sharded actor context for the actor
     * @return A SpringShardedActorBehavior for the actor
     */
    SpringShardedActorBehavior<T> create(SpringShardedActorContext<T> ctx);

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

    /**
     * Returns the cluster role for this actor type. When specified, this actor will only be
     * created on nodes with a matching role.
     *
     * <p>This is useful for:
     * <ul>
     *   <li>Separating compute-intensive actors to specific node types (e.g., "worker" nodes)</li>
     *   <li>Isolating stateful actors to nodes with persistent storage</li>
     *   <li>Creating dedicated actor pools for different workload types</li>
     * </ul>
     *
     * <p>Example usage:
     * <pre>
     * &#64;Component
     * public class HeavyProcessingActor implements SpringShardedActor&lt;Command&gt; {
     *     &#64;Override
     *     public Optional&lt;String&gt; role() {
     *         return Optional.of("worker");
     *     }
     * }
     * </pre>
     *
     * <p><b>Note:</b> Cluster nodes must be configured with roles in application.conf:
     * <pre>
     * pekko.cluster.roles = ["worker"]
     * </pre>
     *
     * @return The cluster role for this actor type, or empty if no specific role is required
     */
    default Optional<String> role() {
        return Optional.empty();
    }
}
