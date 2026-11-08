package io.github.seonwkim.actor.shard;

import java.time.Duration;

/**
 * Builds a {@link SpringShardedActorHandle}. Get one from {@code SpringActorSystem.sharded(...)}.
 *
 * <pre>{@code
 * SpringShardedActorHandle<Command> room = actorSystem.sharded(ChatRoom.class).withId("room-1").get();
 * }</pre>
 *
 * @param <T> the entity's message type
 */
public interface SpringShardedActorBuilder<T> {

    /** The id of the entity to reach. Required. */
    SpringShardedActorBuilder<T> withId(String entityId);

    /** The default timeout for {@code ask} on the handle. Defaults to 3 seconds. */
    SpringShardedActorBuilder<T> withTimeout(Duration timeout);

    /**
     * Returns a handle to the entity. Doesn't start it: the entity starts on its first message.
     *
     * @throws IllegalStateException if no id was set or the system isn't in cluster mode
     */
    SpringShardedActorHandle<T> get();
}
