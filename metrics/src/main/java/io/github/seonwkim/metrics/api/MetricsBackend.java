package io.github.seonwkim.metrics.api;

import io.github.seonwkim.metrics.api.instruments.Counter;
import io.github.seonwkim.metrics.api.instruments.DistributionSummary;
import io.github.seonwkim.metrics.api.instruments.Gauge;
import io.github.seonwkim.metrics.api.instruments.Timer;
import java.util.function.Supplier;

/**
 * Abstraction over metrics collection backends (Micrometer, Prometheus, Dropwizard, etc.).
 * Implementations should be thread-safe.
 */
public interface MetricsBackend {

    /**
     * Get or create a counter with the given name and tags.
     */
    Counter counter(String name, Tags tags);

    /**
     * Get or create a gauge with the given name, tags, and value supplier.
     */
    Gauge gauge(String name, Tags tags, Supplier<Number> valueSupplier);

    /**
     * Get or create a timer with the given name and tags.
     */
    Timer timer(String name, Tags tags);

    /**
     * Get or create a distribution summary with the given name and tags.
     */
    DistributionSummary summary(String name, Tags tags);

    /**
     * Record a value in a distribution summary (convenience method).
     */
    default void recordValue(String name, double value, Tags tags) {
        summary(name, tags).record(value);
    }

    /**
     * Increment a counter by 1 (convenience method).
     */
    default void increment(String name, Tags tags) {
        counter(name, tags).increment();
    }

    /**
     * Get the backend type/name for debugging.
     */
    String getBackendType();
}
