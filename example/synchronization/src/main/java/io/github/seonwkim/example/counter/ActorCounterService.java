package io.github.seonwkim.example.counter;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.SpringShardedActorRef;
import reactor.core.publisher.Mono;

/**
 * Implementation of CounterService that uses actor-based locking for synchronization.
 * Uses sharded actors to ensure that only one thread can increment a counter at a time.
 */
@Service
public class ActorCounterService implements CounterService {

    private static final Logger logger = LoggerFactory.getLogger(ActorCounterService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

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
     * Increments the counter value by 1 and returns the new value.
     * Uses actor-based locking to ensure synchronization.
     *
     * @param counterId The ID of the counter to increment
     * @return A Mono containing the new counter value after increment
     */
    @Override
    public Mono<Long> increment(String counterId) {
        logger.debug("Incrementing counter with ID: {}", counterId);

        // Get a reference to the sharded actor for this counter
        SpringShardedActorRef<CounterActor.Command> actorRef = 
                springActorSystem.entityRef(CounterActor.TYPE_KEY, counterId);

        // Send an increment message to the actor and get the response
        CompletionStage<Long> response = actorRef.ask(
                replyTo -> new CounterActor.Increment(replyTo), 
                TIMEOUT
        );

        return Mono.fromCompletionStage(response)
                .doOnSuccess(newValue -> 
                        logger.debug("Counter with ID: {} incremented to: {}", counterId, newValue)
                );
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

        // Get a reference to the sharded actor for this counter
        SpringShardedActorRef<CounterActor.Command> actorRef = 
                springActorSystem.entityRef(CounterActor.TYPE_KEY, counterId);

        // Send a get value message to the actor and get the response
        CompletionStage<Long> response = actorRef.ask(
                replyTo -> new CounterActor.GetValue(replyTo), 
                TIMEOUT
        );

        return Mono.fromCompletionStage(response);
    }
}
