package io.github.seonwkim.actor.delivery;

import java.util.concurrent.CompletionStage;

/**
 * Sends messages to one actor at least once. Each message is kept, in this sender's memory, until
 * the actor confirms it, and sent again after the actor restarted or resumed.
 *
 * <p>Messages that are not confirmed when this node stops are lost: there is no durable queue yet.
 * Thread-safe; {@link #close} it when done.
 */
public interface ReliableSender<T> extends AutoCloseable {

    /**
     * Sends {@code message}. The stage completes when the actor confirmed it, and fails when the
     * actor stopped or this sender was closed first. It has no timeout of its own: add one with
     * {@code toCompletableFuture().orTimeout(...)}; the message is still delivered after it.
     */
    CompletionStage<Void> send(T message);

    /** Stops the sender. Stages of unconfirmed messages fail with {@link IllegalStateException}. */
    @Override
    void close();

    /** Configures a sender. */
    interface Builder<T> {

        /**
         * The sender's name, in logs and in the receiver's deduplication. Defaults to a random id.
         * Two running senders must not share one.
         */
        Builder<T> withProducerId(String producerId);

        /** Starts the sender. */
        ReliableSender<T> start();
    }
}
