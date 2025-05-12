package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.example.HelloActor.Command;
import java.time.Duration;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service that handles interactions with the HelloActor. It serves as an intermediary between the
 * REST API and the actor system.
 */
@Service
public class HelloService {

	private final SpringActorRef<Command> helloActor;

	/**
	 * Creates a new HelloService with the given actor system. Initializes a single HelloActor
	 * instance that will be used for all requests.
	 *
	 * @param springActorSystem The Spring actor system
	 */
	public HelloService(SpringActorSystem springActorSystem) {
		// Spawn a single actor with the name "default"
		// Note: In a production environment, consider using a non-blocking approach
		// instead of join() which blocks the current thread
		this.helloActor =
				springActorSystem
						.spawn(HelloActor.Command.class, "default", Duration.ofSeconds(3))
						.toCompletableFuture()
						.join();
	}

	/**
	 * Sends a hello message to the actor and returns the response.
	 *
	 * @return A Mono containing the response from the actor
	 */
	public Mono<String> hello() {
		// Send a SayHello message to the actor and convert the response to a Mono
		return Mono.fromCompletionStage(
				helloActor.ask(HelloActor.SayHello::new, Duration.ofSeconds(3)));
	}
}
