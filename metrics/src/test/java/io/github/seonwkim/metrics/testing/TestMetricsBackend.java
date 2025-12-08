package io.github.seonwkim.metrics.testing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Test utility for metrics testing.
 * Extends SimpleMeterRegistry from Micrometer and adds helper methods for test assertions.
 */
public class TestMetricsBackend extends SimpleMeterRegistry {
    public TestMetricsBackend() {
        super();
    }

    /**
     * Get the total count for all counters with the given name (summed across all tag combinations).
     */
    public double getCounterValue(String name) {
        return getMeters().stream()
                .filter(m -> m instanceof Counter && m.getId().getName().equals(name))
                .mapToDouble(m -> ((Counter) m).count())
                .sum();
    }

    /**
     * Get the value of a gauge with the given name.
     * If multiple gauges exist with different tags, returns the sum.
     */
    public double getGaugeValue(String name) {
        return getMeters().stream()
                .filter(m -> m instanceof Gauge && m.getId().getName().equals(name))
                .mapToDouble(m -> ((Gauge) m).value())
                .sum();
    }

    /**
     * Check if any metric with the given name has a tag with the given key.
     */
    public boolean hasMetricWithTag(String metricName, String tagKey) {
        return getMeters().stream()
                .filter(m -> m.getId().getName().equals(metricName))
                .anyMatch(m -> m.getId().getTags().stream()
                        .anyMatch(tag -> tag.getKey().equals(tagKey)));
    }

    /**
     * Get a specific counter by name and tag key-value pairs.
     */
    public Counter getCounter(String name, String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be pairs of key-value");
        }

        for (Meter meter : getMeters()) {
            if (meter instanceof Counter && meter.getId().getName().equals(name)) {
                boolean allMatch = true;
                for (int i = 0; i < keyValues.length; i += 2) {
                    String key = keyValues[i];
                    String value = keyValues[i + 1];
                    boolean tagExists = meter.getId().getTags().stream()
                            .anyMatch(tag ->
                                    tag.getKey().equals(key) && tag.getValue().equals(value));
                    if (!tagExists) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    return (Counter) meter;
                }
            }
        }
        return null;
    }

    /**
     * Get the count of timers with the given name.
     */
    public long getTimerCount(String name) {
        return getMeters().stream()
                .filter(m -> m instanceof io.micrometer.core.instrument.Timer
                        && m.getId().getName().equals(name))
                .mapToLong(m -> ((io.micrometer.core.instrument.Timer) m).count())
                .sum();
    }

    /**
     * Get the total time for all timers with the given name (in nanoseconds).
     */
    public long getTimerTotalTime(String name) {
        return getMeters().stream()
                .filter(m -> m instanceof io.micrometer.core.instrument.Timer
                        && m.getId().getName().equals(name))
                .mapToLong(m -> (long)
                        ((io.micrometer.core.instrument.Timer) m).totalTime(java.util.concurrent.TimeUnit.NANOSECONDS))
                .sum();
    }

    /**
     * Get the count of all gauges in the registry.
     */
    public int gaugeCount() {
        return (int) getMeters().stream().filter(m -> m instanceof Gauge).count();
    }
}
