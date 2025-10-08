package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.SpringShardedActorRef;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service that handles interactions with the HelloActor. It serves as an intermediary between the
 * REST API and the actor system.
 */
@Service
public class HelloService {

    private final SpringActorSystem springActorSystem;

    /**
     * Creates a new HelloService with the given actor system.
     *
     * @param springActorSystem The Spring actor system
     */
    public HelloService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    /**
     * Sends a hello message to an actor entity and returns the response.
     *
     * @param message The message to send
     * @param entityId The ID of the entity to send the message to
     * @return A Mono containing the response from the actor
     */
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
