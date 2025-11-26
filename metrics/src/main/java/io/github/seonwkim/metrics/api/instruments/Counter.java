package io.github.seonwkim.metrics.api.instruments;

/**
 * A counter is a cumulative metric that represents a single monotonically increasing value.
 * Counters are typically used to count events, such as the number of messages processed.
 */
public interface Counter {

    /**
     * Increment the counter by 1.
     */
    default void increment() {
        increment(1.0);
    }

    /**
     * Increment the counter by the given amount.
     * @param amount must be positive
     */
    void increment(double amount);

    /**
     * Get the current count.
     */
    double count();
}
