package io.github.seonwkim.actor.delivery;

import org.springframework.lang.Nullable;

/**
 * A reliable send gave up: the receiver did not acknowledge the message within the
 * {@link RetryPolicy}'s attempts or timeout. The message may still have been handled, for example
 * when only the acks were lost; sending it again with the same idempotency key is safe within the
 * receiver's idempotency TTL.
 */
public class ReliableDeliveryException extends RuntimeException {

    private final String idempotencyKey;
    private final int attempts;

    public ReliableDeliveryException(String message, String idempotencyKey, int attempts, @Nullable Throwable cause) {
        super(message, cause);
        this.idempotencyKey = idempotencyKey;
        this.attempts = attempts;
    }

    /** The key the message was sent with. */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** How many attempts were made. */
    public int attempts() {
        return attempts;
    }
}
