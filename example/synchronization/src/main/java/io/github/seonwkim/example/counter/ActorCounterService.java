package io.github.seonwkim.example.counter;

import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementation of CounterService that uses actor-based synchronization.
 *
 * <p>This service demonstrates how sharded actors provide natural synchronization:
 * - Each counter is a separate actor instance (identified by counterId)
 * - Actor message processing is single-threaded by design
 * - No explicit locks needed - the actor model handles it
 * - Actors are automatically distributed across cluster nodes
 *
 * <p>Best practices demonstrated:
 * - Use sharded actors for distributed state management
 * - Use tell() for fire-and-forget operations (increment)
 * - Use ask() with timeout handling for request-response (getValue)
 */
@Service
public class ActorCounterService implements CounterService {

    private static final Logger logger = LoggerFactory.getLogger(ActorCounterService.class);

    private final SpringActorSystem springActorSystem;

    /**
     * Creates a new ActorCounterServiceImpl with the given actor system.
     *
     * @param springActorSystem The Spring actor system
     */
    public ActorCounterService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    /**
     * Increments the counter value by 1 and returns the new value. Uses actor-based locking to ensure
     * synchronization.
     *
     * @param counterId The ID of the counter to increment
     */
    @Override
    public void increment(String counterId) {
        logger.debug("Incrementing counter with ID: {}", counterId);

        // Get a reference to the sharded actor for this counter using the new simplified API
        var actorRef =
                springActorSystem.sharded(CounterActor.class).withId(counterId).get();

        // Send an increment message to the actor and get the response
        actorRef.tell(new CounterActor.Increment());
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

        // Get a reference to the sharded actor for this counter using the new simplified API
        var actorRef =
                springActorSystem.sharded(CounterActor.class).withId(counterId).get();

        // Send a get value message to the actor using the ask() method with error handling
        CompletionStage<Long> response = actorRef.ask(new CounterActor.GetValue())
                .withTimeout(Duration.ofSeconds(3))
                .onTimeout(() -> {
                    logger.warn("Timeout getting value for counter: {}", counterId);
                    return 0L; // Return default value on timeout
                })
                .execute();

        return Mono.fromCompletionStage(response);
    }
}
