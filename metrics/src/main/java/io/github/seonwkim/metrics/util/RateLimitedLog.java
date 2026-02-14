package io.github.seonwkim.metrics.util;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

/**
 * Rate-limited logging to avoid unbounded log output from hot-path advice code.
 * Logs at most once per configured interval (e.g. every 5 seconds) per instance.
 * Uses lock-free compare-and-swap for minimal contention on the hot path.
 *
 * TODO: Allow users to set custom intervalMs
 */
public final class RateLimitedLog {

    private final long intervalNs;
    private final AtomicLong lastLogTime = new AtomicLong(0);

    /**
     * @param intervalMs minimum milliseconds between log output for this instance
     */
    public RateLimitedLog(long intervalMs) {
        this.intervalNs = intervalMs * 1_000_000;
    }

    /**
     * Log error if the rate limit permits.
     */
    public void error(Logger logger, String message, Throwable throwable) {
        if (!logger.isErrorEnabled()) {
            return;
        }
        long now = System.nanoTime();
        long prev = lastLogTime.get();
        if (now - prev < intervalNs) {
            return;
        }
        // Try to publish this timestamp; only one thread will succeed
        if (lastLogTime.compareAndSet(prev, now)) {
            logger.error(message, throwable);
        }
    }
}
