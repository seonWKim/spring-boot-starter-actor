package io.github.seonwkim.metrics.api.instruments;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Timer is used to measure short-duration latencies and the frequency of such events.
 * All implementations of Timer report at least the total time and count of events.
 */
public interface Timer {

    /**
     * Record a duration.
     */
    void record(Duration duration);

    /**
     * Record a duration in the given time unit.
     */
    void record(long amount, TimeUnit unit);

    /**
     * Record a duration in nanoseconds.
     */
    default void recordNanos(long nanos) {
        record(nanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Execute the Runnable and record the execution time.
     */
    default void record(Runnable f) {
        long start = System.nanoTime();
        try {
            f.run();
        } finally {
            recordNanos(System.nanoTime() - start);
        }
    }

    /**
     * Execute the Callable and record the execution time.
     */
    default <T> T recordCallable(Callable<T> f) throws Exception {
        long start = System.nanoTime();
        try {
            return f.call();
        } finally {
            recordNanos(System.nanoTime() - start);
        }
    }

    /**
     * Execute the Supplier and record the execution time.
     */
    default <T> T record(Supplier<T> f) {
        long start = System.nanoTime();
        try {
            return f.get();
        } finally {
            recordNanos(System.nanoTime() - start);
        }
    }

    /**
     * Get the total number of times that record has been called.
     */
    long count();

    /**
     * Get the total time in nanoseconds of all recorded events.
     */
    long totalTime(TimeUnit unit);

    /**
     * Get the maximum recorded duration.
     */
    double max(TimeUnit unit);

    /**
     * Get the mean (average) duration.
     */
    double mean(TimeUnit unit);
}
