package io.github.seonwkim.actor.shard;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActorHandle;

/**
 * A reference to one sharded entity, wherever it runs. Messages reach the entity on the node that
 * hosts it; the entity is started if it isn't running.
 *
 * @param <T> the entity's message type
 */
public interface SpringShardedActorHandle<T> {

    /** Sends a message without waiting for a reply. */
    void tell(T message);

    /** Sends a request and returns a builder for how to wait for the reply. */
    <RES> SpringActorHandle.AskBuilder<RES> ask(AskCommand<RES> command);

    /** The entity id. */
    String entityId();

    /** The entity type. */
    ShardedEntityType<T> entityType();

    /**
     * The engine's own reference, for features the starter doesn't wrap. Pass the engine's entity
     * reference class; throws {@link IllegalArgumentException} for any other type.
     */
    <X> X unwrap(Class<X> type);
}
