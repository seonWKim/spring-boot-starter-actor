package io.github.seonwkim.metrics.api.instruments;

/**
 * Track the distribution of events. For example, response sizes for requests hitting an HTTP server.
 */
public interface DistributionSummary {

    /**
     * Record a value.
     */
    void record(double amount);

    /**
     * The number of times that record has been called.
     */
    long count();

    /**
     * The total amount of all recorded events.
     */
    double totalAmount();

    /**
     * The maximum recorded amount.
     */
    double max();

    /**
     * The mean (average) of all recorded amounts.
     */
    double mean();
}
