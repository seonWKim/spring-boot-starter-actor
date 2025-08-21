package io.github.seonwkim.example.counter;

import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementation of CounterService that uses actor-based locking for synchronization. Uses sharded
 * actors to ensure that only one thread can increment a counter at a time.
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
	 * Increments the counter value by 1 and returns the new value. Uses actor-based locking to ensure
	 * synchronization.
	 *
	 * @param counterId The ID of the counter to increment
	 */
	@Override
	public void increment(String counterId) {
		logger.debug("Incrementing counter with ID: {}", counterId);

		// Get a reference to the sharded actor for this counter using the new simplified API
		var actorRef = springActorSystem.sharded(CounterActor.class)
				.withId(counterId)
				.get();

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
		var actorRef = springActorSystem.sharded(CounterActor.class)
				.withId(counterId)
				.get();

		// Send a get value message to the actor and get the response
		CompletionStage<Long> response =
				actorRef.ask(replyTo -> new CounterActor.GetValue(replyTo), TIMEOUT);

		return Mono.fromCompletionStage(response);
	}
}
