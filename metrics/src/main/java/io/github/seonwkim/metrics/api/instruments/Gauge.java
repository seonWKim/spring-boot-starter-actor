package io.github.seonwkim.metrics.api.instruments;

import java.util.function.Supplier;

/**
 * A gauge is a metric that represents a single numerical value that can arbitrarily go up and down.
 * Gauges are typically used for measured values like temperatures or current memory usage.
 */
public interface Gauge {

    /**
     * Get the current value of the gauge.
     */
    double value();

    /**
     * Get the value supplier used by this gauge.
     */
    Supplier<Number> valueSupplier();
}
