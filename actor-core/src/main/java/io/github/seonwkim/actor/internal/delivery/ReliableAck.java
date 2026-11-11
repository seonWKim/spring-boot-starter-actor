package io.github.seonwkim.actor.internal.delivery;

/** A consumer's reply to a {@link ReliableEnvelope}: handled, or why not. */
public enum ReliableAck {
    /** A handler returned, now or for an earlier message with the same key and payload. */
    ACK,
    /** The handler threw {@code StaleGenerationException}; the sender retries. */
    STALE_GENERATION,
    /** The key was handled with a different payload; the sender gives up. */
    PAYLOAD_CONFLICT
}
