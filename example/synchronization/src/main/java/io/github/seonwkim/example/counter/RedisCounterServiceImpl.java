package io.github.seonwkim.example.counter;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of CounterService that uses Redis locking for synchronization.
 * Uses Redisson distributed locks to ensure that only one thread can increment a counter at a time.
 */
@Service
public class RedisCounterServiceImpl implements RedisCounterService {

    private static final Logger logger = LoggerFactory.getLogger(RedisCounterServiceImpl.class);
    private static final String COUNTER_KEY_PREFIX = "counter:";
    private static final String LOCK_KEY_PREFIX = "counter:lock:";
    private static final long LOCK_WAIT_TIME = 5000; // 5 seconds
    private static final long LOCK_LEASE_TIME = 2000; // 2 seconds
    
    private final ReactiveRedisTemplate<String, Long> redisTemplate;
    private final ReactiveValueOperations<String, Long> valueOps;
    private final RedissonClient redissonClient;
    
    /**
     * Creates a new RedisCounterServiceImpl with the given Redis template and Redisson client.
     *
     * @param redisTemplate The Redis template for accessing Redis
     * @param redissonClient The Redisson client for distributed locks
     */
    public RedisCounterServiceImpl(ReactiveRedisTemplate<String, Long> redisTemplate, RedissonClient redissonClient) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
        this.redissonClient = redissonClient;
    }
    
    /**
     * Increments the counter value by 1 and returns the new value.
     * Uses Redis distributed locking to ensure synchronization.
     *
     * @param counterId The ID of the counter to increment
     * @return A Mono containing the new counter value after increment
     */
    @Override
    public Mono<Long> increment(String counterId) {
        logger.debug("Incrementing counter with ID: {}", counterId);
        
        String counterKey = COUNTER_KEY_PREFIX + counterId;
        String lockKey = LOCK_KEY_PREFIX + counterId;
        
        // Get a distributed lock for this counter
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // Try to acquire the lock
            boolean locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.MILLISECONDS);
            
            if (!locked) {
                logger.warn("Failed to acquire lock for counter: {}", counterId);
                return Mono.error(new RuntimeException("Failed to acquire lock for counter: " + counterId));
            }
            
            // Increment the counter atomically
            return valueOps.increment(counterKey)
                    .doOnSuccess(newValue -> logger.debug("Counter with ID: {} incremented to: {}", counterId, newValue))
                    .doFinally(signalType -> {
                        // Release the lock
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                            logger.debug("Released lock for counter: {}", counterId);
                        }
                    });
        } catch (InterruptedException e) {
            logger.error("Interrupted while trying to acquire lock for counter: {}", counterId, e);
            Thread.currentThread().interrupt();
            return Mono.error(e);
        }
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
        return valueOps.get(counterKey)
                .defaultIfEmpty(0L);
    }
}
