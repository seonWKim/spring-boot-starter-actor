package io.github.seonwkim.actor.metrics.testing;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.awaitility.core.ThrowingRunnable;

/**
 * Polls an assertion until it passes or a timeout runs out.
 *
 * <p>Metrics are recorded on Pekko dispatcher threads, after the test thread has sent its
 * message. Integration tests wrap every assertion on a recorded value in {@link #eventually}
 * instead of sleeping a fixed time and reading once.
 *
 * <p>Polling only fits a condition that cannot be true before the work under test is done, such
 * as {@code count > 0}. For an exact value ({@code == initial + 1}) use {@link #eventuallyStays}:
 * a counter that is about to overshoot passes through the exact value on its way, so a plain poll
 * would accept it. For an upper bound ({@code size <= 1}), first wait for a signal that the work is
 * done, then assert the bound.
 */
public final class Eventually {

    /** Upper bound for an asynchronous metric to appear; a passing assertion returns at once. */
    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * How long an exact value must keep holding in {@link #eventuallyStays}. It matches the
     * shortest wait the tests used before polling, so an extra increment that the old single read
     * would have caught still fails the test.
     */
    public static final Duration HOLD = Duration.ofMillis(500);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(20);

    private Eventually() {}

    /** Re-runs {@code assertion} until it stops throwing, failing with its last error at {@link #TIMEOUT}. */
    public static void eventually(ThrowingRunnable assertion) {
        await().atMost(TIMEOUT)
                .pollDelay(Duration.ZERO)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(assertion);
    }

    /**
     * Re-runs {@code assertion} until it passes, then requires it to keep passing for {@link #HOLD}.
     * Use it for exact values, so a counter that goes past the expected value fails the test.
     */
    public static void eventuallyStays(ThrowingRunnable assertion) {
        await().atMost(TIMEOUT.plus(HOLD))
                .pollDelay(Duration.ZERO)
                .pollInterval(POLL_INTERVAL)
                .during(HOLD)
                .untilAsserted(assertion);
    }
}
