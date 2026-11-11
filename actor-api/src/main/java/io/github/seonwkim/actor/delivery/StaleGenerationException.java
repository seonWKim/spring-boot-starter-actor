package io.github.seonwkim.actor.delivery;

/**
 * Thrown by a {@link ReliableDelivery#consumer}'s handler to say the message reached this actor too
 * early or too late: the actor is not the active owner of the message's state yet (it is still
 * activating) or anymore (a newer generation took over). The message is not acknowledged and its key
 * is not remembered, the actor keeps running without a restart, and the sender retries the same key
 * and payload. When the retries run out, the send fails with {@link ReliableDeliveryException.Reason#STALE_GENERATION}.
 *
 * <p>Thrown for a plain {@code tell}, it is an ordinary exception for the actor's supervision.
 */
public class StaleGenerationException extends RuntimeException {

    public StaleGenerationException(String message) {
        super(message);
    }
}
