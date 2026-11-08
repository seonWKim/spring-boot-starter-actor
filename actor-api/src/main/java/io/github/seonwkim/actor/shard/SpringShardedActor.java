package io.github.seonwkim.actor.shard;

import io.github.seonwkim.actor.SpringActorBehavior;
import java.util.Optional;

/**
 * An entity spread over the cluster: one instance per entity id, created on demand on one node and
 * moved when nodes join or leave. Declare it as a Spring {@code @Component}; the starter registers it
 * with sharding when the actor system starts.
 *
 * <pre>{@code
 * @Component
 * public class ChatRoom implements SpringShardedActor<ChatRoom.Command> {
 *     public static final ShardedEntityType<Command> TYPE = ShardedEntityType.of(Command.class, "chat-room");
 *
 *     public ShardedEntityType<Command> entityType() { return TYPE; }
 *
 *     public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
 *         return SpringActorBehavior.builder(Command.class, ctx)
 *                 .onMessage(Join.class, (context, msg) -> SpringActorBehavior.same())
 *                 .build();
 *     }
 * }
 * }</pre>
 *
 * @param <T> the entity's message type
 */
public interface SpringShardedActor<T> {

    /** The entity type: its message class and its cluster-wide name. */
    ShardedEntityType<T> entityType();

    /** Creates the behavior of one entity. Called on the node that hosts it. */
    SpringActorBehavior<T> create(SpringShardedActorContext<T> ctx);

    /** How entity ids map to shards. Every node must return the same strategy. */
    default ShardingStrategy sharding() {
        return ShardingStrategy.defaults();
    }

    /**
     * The cluster role whose nodes host the entities. Other nodes only route messages to them.
     * Empty means every node hosts entities.
     */
    default Optional<String> role() {
        return Optional.empty();
    }
}
