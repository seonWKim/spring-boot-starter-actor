package io.github.seonwkim.example.counter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import reactor.core.publisher.Mono;

/**
 * Implementation of CounterService that uses database locking for synchronization.
 * Uses pessimistic locking to ensure that only one thread can increment a counter at a time.
 */
@Service
public class DbCounterService implements CounterService{

    private static final Logger logger = LoggerFactory.getLogger(DbCounterService.class);
    
    private final CounterRepository counterRepository;
    
    /**
     * Creates a new DbCounterServiceImpl with the given repository.
     *
     * @param counterRepository The repository for accessing Counter entities
     */
    public DbCounterService(CounterRepository counterRepository) {
        this.counterRepository = counterRepository;
    }
    
    /**
     * Increments the counter value by 1 and returns the new value.
     * Uses database pessimistic locking to ensure synchronization.
     *
     * @param counterId The ID of the counter to increment
     * @return A Mono containing the new counter value after increment
     */
    @Override
    @Transactional
    public Mono<Long> increment(String counterId) {
        logger.debug("Incrementing counter with ID: {}", counterId);
        
        // Find the counter with a lock or create a new one if it doesn't exist
        Counter counter = counterRepository.findByIdWithLock(counterId)
                .orElseGet(() -> {
                    logger.debug("Counter not found, creating new counter with ID: {}", counterId);
                    Counter newCounter = new Counter(counterId, 0);
                    return counterRepository.save(newCounter);
                });
        
        // Increment the counter and save it
        long newValue = counter.increment();
        counterRepository.save(counter);
        
        logger.debug("Counter with ID: {} incremented to: {}", counterId, newValue);
        
        return Mono.just(newValue);
    }
    
    /**
     * Gets the current value of the counter.
     *
     * @param counterId The ID of the counter to get
     * @return A Mono containing the current counter value
     */
    @Override
    @Transactional(readOnly = true)
    public Mono<Long> getValue(String counterId) {
        logger.debug("Getting value for counter with ID: {}", counterId);
        
        // Find the counter or return 0 if it doesn't exist
        return Mono.just(counterRepository.findById(counterId)
                .map(Counter::getValue)
                .orElse(0L));
    }
}
