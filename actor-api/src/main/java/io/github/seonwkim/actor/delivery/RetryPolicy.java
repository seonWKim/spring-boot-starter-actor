package io.github.seonwkim.actor.delivery;

import java.time.Duration;
import java.util.Objects;

/**
 * How a reliable sender retries a message that was not acknowledged. Each attempt waits up to
 * {@code attemptTimeout} for the ack; the next attempt follows after a backoff that starts at
 * {@code initialBackoff} and doubles up to {@code maxBackoff}, with full jitter: the pause is random,
 * from zero to the backoff. The sender gives up after {@code maxAttempts} attempts or when
 * {@code timeout} has passed since the first, whichever comes first. Every attempt carries the same
 * idempotency key and payload. A {@link ReliableDeliveryException.Reason#PAYLOAD_CONFLICT} is not
 * retried.
 *
 * @param maxAttempts attempts in total, including the first; at least 1
 * @param timeout the time from the first attempt after which the sender gives up
 * @param attemptTimeout how long one attempt waits for the ack
 * @param initialBackoff the pause before the second attempt
 * @param maxBackoff the longest backoff between attempts
 */
public record RetryPolicy(
        int maxAttempts, Duration timeout, Duration attemptTimeout, Duration initialBackoff, Duration maxBackoff) {

    private static final RetryPolicy DEFAULTS = new RetryPolicy(
            5, Duration.ofSeconds(30), Duration.ofSeconds(3), Duration.ofMillis(200), Duration.ofSeconds(5));

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, got " + maxAttempts);
        }
        requirePositive(timeout, "timeout");
        requirePositive(attemptTimeout, "attemptTimeout");
        requirePositive(initialBackoff, "initialBackoff");
        requirePositive(maxBackoff, "maxBackoff");
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must not be shorter than initialBackoff");
        }
    }

    /** 5 attempts within 30 seconds, 3 seconds per attempt, backoff from 200 ms up to 5 seconds. */
    public static RetryPolicy defaults() {
        return DEFAULTS;
    }

    public RetryPolicy withMaxAttempts(int maxAttempts) {
        return new RetryPolicy(maxAttempts, timeout, attemptTimeout, initialBackoff, maxBackoff);
    }

    public RetryPolicy withTimeout(Duration timeout) {
        return new RetryPolicy(maxAttempts, timeout, attemptTimeout, initialBackoff, maxBackoff);
    }

    public RetryPolicy withAttemptTimeout(Duration attemptTimeout) {
        return new RetryPolicy(maxAttempts, timeout, attemptTimeout, initialBackoff, maxBackoff);
    }

    public RetryPolicy withBackoff(Duration initialBackoff, Duration maxBackoff) {
        return new RetryPolicy(maxAttempts, timeout, attemptTimeout, initialBackoff, maxBackoff);
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive, got " + value);
        }
    }
}
