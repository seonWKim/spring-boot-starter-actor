package io.github.seonwkim.actor.delivery;

import java.util.concurrent.CompletionStage;

/**
 * Sends messages to the entities of one sharded actor class at least once: each send is retried
 * until the entity acknowledges it (see {@link RetryPolicy}). A retry starts the entity again if it
 * stopped or moved. The entities wrap their behavior with {@link ReliableDelivery#consumer}.
 * Thread-safe; holds no resources.
 */
public interface ShardedReliableSender<T> {

    /** Sends {@code message} to entity {@code entityId} with a new idempotency key. */
    CompletionStage<Void> send(String entityId, T message);

    /**
     * Sends {@code message} to entity {@code entityId} with {@code idempotencyKey}. Any send with the
     * same key to that entity runs the handler once while the entity remembers the key.
     */
    CompletionStage<Void> send(String entityId, T message, String idempotencyKey);

    /** Configures a sender. */
    interface Builder<T> {

        /** How sends are retried. Defaults to {@link RetryPolicy#defaults()}. */
        Builder<T> withRetry(RetryPolicy retryPolicy);

        ShardedReliableSender<T> build();
    }
}
