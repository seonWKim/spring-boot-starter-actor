package io.github.seonwkim.example.counter;

import reactor.core.publisher.Mono;

/**
 * Interface for counter services that provide synchronized increment operations. This interface
 * defines the contract for different implementations of counter services.
 */
public interface CounterService {

    /**
     * Increments the counter value by 1 and returns the new value. This operation is synchronized to
     * ensure consistency.
     *
     * @param counterId The ID of the counter to increment
     */
    void increment(String counterId);

    /**
     * Gets the current value of the counter.
     *
     * @param counterId The ID of the counter to get
     * @return A Mono containing the current counter value
     */
    Mono<Long> getValue(String counterId);
}
