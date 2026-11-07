package io.github.seonwkim.metrics.testing;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.awaitility.core.ThrowingRunnable;

/**
 * Polls an assertion until it passes or a timeout runs out.
 *
 * <p>Metrics are recorded on Pekko dispatcher threads, after the test thread has sent its
 * message. Integration tests wrap every assertion on a recorded value in {@link #eventually}
 * instead of sleeping a fixed time and reading once.
 */
public final class Eventually {

    /** Upper bound for an asynchronous metric to appear; a passing assertion returns at once. */
    public static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(20);

    private Eventually() {}

    /** Re-runs {@code assertion} until it stops throwing, failing with its last error at {@link #TIMEOUT}. */
    public static void eventually(ThrowingRunnable assertion) {
        await().atMost(TIMEOUT)
                .pollDelay(Duration.ZERO)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(assertion);
    }
}
