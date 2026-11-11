package io.github.seonwkim.actor.delivery;

import java.util.concurrent.CompletionStage;

/**
 * Sends messages to one actor at least once: each send is retried until the actor acknowledges it
 * (see {@link RetryPolicy}). The actor wraps its behavior with {@link ReliableDelivery#consumer}.
 * Thread-safe; holds no resources.
 */
public interface ReliableSender<T> {

    /**
     * Sends {@code message} with a new idempotency key, so its retries run the handler once. The
     * stage completes when the actor acknowledged it and fails with {@link ReliableDeliveryException}
     * when the retry policy runs out or the send can't succeed; its {@link
     * ReliableDeliveryException#reason() reason} says which.
     */
    CompletionStage<Void> send(T message);

    /**
     * Sends {@code message} with {@code idempotencyKey}, such as an order id. Any send with the same
     * key, from any sender, runs the handler once while the actor remembers the key. The same key
     * with a different payload fails with {@link ReliableDeliveryException.Reason#PAYLOAD_CONFLICT}.
     */
    CompletionStage<Void> send(T message, String idempotencyKey);

    /** Configures a sender. */
    interface Builder<T> {

        /**
         * How sends are retried. Defaults to {@code spring.actor.delivery.retry.*}, which defaults
         * to {@link RetryPolicy#defaults()}.
         */
        Builder<T> withRetry(RetryPolicy retryPolicy);

        ReliableSender<T> build();
    }
}
