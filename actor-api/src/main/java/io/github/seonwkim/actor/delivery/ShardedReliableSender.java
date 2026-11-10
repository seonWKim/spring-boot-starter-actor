package io.github.seonwkim.actor.delivery;

import java.util.concurrent.CompletionStage;

/**
 * Sends messages to the entities of one sharded actor class at least once. Each message is kept, in
 * this sender's memory, until the entity confirms it, and sent again when the entity was restarted,
 * passivated or moved to another node.
 *
 * <p>Messages that are not confirmed when this node stops are lost: there is no durable queue yet.
 * Thread-safe; {@link #close} it when done.
 */
public interface ShardedReliableSender<T> extends AutoCloseable {

    /**
     * Sends {@code message} to entity {@code entityId}. The stage completes when the entity confirmed
     * it, and fails when this sender was closed first. It has no timeout of its own.
     */
    CompletionStage<Void> send(String entityId, T message);

    /** Stops the sender. Stages of unconfirmed messages fail with {@link IllegalStateException}. */
    @Override
    void close();

    /** Configures a sender. */
    interface Builder<T> {

        /**
         * The sender's name, in logs and in each entity's deduplication. Defaults to a random id.
         * Two running senders must not share one.
         */
        Builder<T> withProducerId(String producerId);

        /** Starts the sender. */
        ShardedReliableSender<T> start();
    }
}
