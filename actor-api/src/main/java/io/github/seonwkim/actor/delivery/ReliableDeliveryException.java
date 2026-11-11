package io.github.seonwkim.actor.delivery;

import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * A reliable send failed. {@link #reason()} says why and whether sending again can succeed:
 *
 * <ul>
 *   <li>{@link Reason#STALE_GENERATION}, {@link Reason#TIMEOUT}, {@link Reason#UNAVAILABLE} are
 *       retryable: the {@link RetryPolicy}'s attempts or timeout ran out. The message may still have
 *       been handled, for example when only the acks were lost; sending it again with the same
 *       idempotency key and payload is safe within the receiver's idempotency TTL.
 *   <li>{@link Reason#PAYLOAD_CONFLICT} is permanent: the receiver already handled a different
 *       payload under the same key. It is not retried, and sending again fails the same way.
 * </ul>
 */
public class ReliableDeliveryException extends RuntimeException {

    /** Why a reliable send failed. */
    public enum Reason {
        /**
         * The receiver's handler threw {@link StaleGenerationException} on every attempt: it was not
         * the active owner of the message's state. Retryable. HTTP: 409 with {@code Retry-After}.
         */
        STALE_GENERATION(true),
        /**
         * The last attempt waited its whole attempt timeout without an ack: the receiver is slow,
         * its handler threw, or the message or its ack was lost. Retryable. HTTP: 504.
         */
        TIMEOUT(true),
        /**
         * The last attempt failed at once without reaching a receiver, for example because the
         * receiving actor is stopped. Retryable. HTTP: 503.
         */
        UNAVAILABLE(true),
        /**
         * The receiver remembers the key with a different payload. Permanent: not retried. HTTP: 409
         * or 422.
         */
        PAYLOAD_CONFLICT(false);

        private final boolean retryable;

        Reason(boolean retryable) {
            this.retryable = retryable;
        }

        /** Whether sending again with the same key and payload can succeed. */
        public boolean isRetryable() {
            return retryable;
        }
    }

    private final String idempotencyKey;
    private final int attempts;
    private final Reason reason;

    public ReliableDeliveryException(
            String message, String idempotencyKey, int attempts, Reason reason, @Nullable Throwable cause) {
        super(message, cause);
        this.idempotencyKey = idempotencyKey;
        this.attempts = attempts;
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /**
     * A failure with reason {@link Reason#TIMEOUT}.
     *
     * @deprecated since 1.1.0: pass the {@link Reason}.
     */
    @Deprecated(since = "1.1.0")
    public ReliableDeliveryException(String message, String idempotencyKey, int attempts, @Nullable Throwable cause) {
        this(message, idempotencyKey, attempts, Reason.TIMEOUT, cause);
    }

    /** The key the message was sent with. */
    public String idempotencyKey() {
        return idempotencyKey;
    }

    /** How many attempts were made. */
    public int attempts() {
        return attempts;
    }

    /** Why the send failed, and whether sending again can succeed. */
    public Reason reason() {
        return reason;
    }
}
