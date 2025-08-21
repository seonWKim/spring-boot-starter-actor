package io.github.seonwkim.example;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.springframework.stereotype.Service;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.SpringShardedActorRef;
import reactor.core.publisher.Mono;

@Service
public class HelloService {

	private final SpringActorSystem springActorSystem;

	public HelloService(SpringActorSystem springActorSystem) {
		this.springActorSystem = springActorSystem;
	}

	public Mono<String> hello(String message, String entityId) {
		// Get a reference to the actor entity
		SpringShardedActorRef<HelloActor.Command> actorRef =
				springActorSystem.sharded(HelloActor.class).withId(entityId).get();

		// Send the message to the actor and get the response
		CompletionStage<String> response =
				actorRef.ask(replyTo -> new HelloActor.SayHello(replyTo, message), Duration.ofSeconds(3));

		// Convert the CompletionStage to a Mono for reactive programming
		return Mono.fromCompletionStage(response);
	}
}
