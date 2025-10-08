package io.github.seonwkim.example.counter;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Implementation of CounterService that uses Redis locking for synchronization. Uses Lettuce (via
 * Spring Data Redis) to implement distributed locking.
 */
@Service
public class RedisCounterService implements CounterService {

    private static final Logger logger = LoggerFactory.getLogger(RedisCounterService.class);
    private static final String COUNTER_KEY_PREFIX = "counter:";
    private static final String LOCK_KEY_PREFIX = "counter:lock:";
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration RETRY_DELAY = Duration.ofMillis(100);
    private static final int MAX_RETRIES = 50; // 5 seconds total retry time

    private final ReactiveRedisTemplate<String, Long> redisTemplate;
    private final ReactiveValueOperations<String, Long> valueOps;

    /**
     * Creates a new RedisCounterServiceImpl with the given Redis template.
     *
     * @param redisTemplate The Redis template for accessing Redis
     */
    public RedisCounterService(ReactiveRedisTemplate<String, Long> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
    }

    /**
     * Increments the counter value by 1 and returns the new value. Uses Redis distributed locking to
     * ensure synchronization.
     *
     * @param counterId The ID of the counter to increment
     */
    @Override
    public void increment(String counterId) {
        logger.debug("Incrementing counter with ID: {}", counterId);

        String counterKey = COUNTER_KEY_PREFIX + counterId;
        String lockKey = LOCK_KEY_PREFIX + counterId;

        valueOps.setIfAbsent(lockKey, 1L, LOCK_TIMEOUT)
                .flatMap(locked -> {
                    if (!locked) {
                        logger.warn("Failed to acquire lock for counter: {}", counterId);
                        return Mono.error(new RuntimeException("Failed to acquire lock for counter: " + counterId));
                    }

                    return valueOps.increment(counterKey)
                            .doOnSuccess(newValue ->
                                    logger.debug("Counter with ID: {} incremented to: {}", counterId, newValue))
                            .doFinally(
                                    signalType -> redisTemplate.delete(lockKey).subscribe(deleted -> {
                                        if (deleted > 0) {
                                            logger.debug("Released lock for counter: {}", counterId);
                                        } else {
                                            logger.warn("Failed to release lock for counter: {}", counterId);
                                        }
                                    }));
                })
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                        .doBeforeRetry(retrySignal -> logger.debug(
                                "Retrying lock acquisition for counter: {}, attempt: {}",
                                counterId,
                                retrySignal.totalRetries() + 1)))
                .subscribe(
                        null, // onNext is ignored since we don't need the result
                        error -> logger.error("Error while incrementing counter: {}", counterId, error));
    }

    /**
     * Gets the current value of the counter.
     *
     * @param counterId The ID of the counter to get
     * @return A Mono containing the current counter value
     */
    @Override
    public Mono<Long> getValue(String counterId) {
        logger.debug("Getting value for counter with ID: {}", counterId);

        String counterKey = COUNTER_KEY_PREFIX + counterId;

        // Get the counter value or return 0 if it doesn't exist
        return valueOps.get(counterKey).defaultIfEmpty(0L);
    }
}
