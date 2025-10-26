package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.SpringShardedActorRef;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service that handles interactions with sharded HelloActor instances. Demonstrates best practices
 * for working with sharded actors in a cluster environment.
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
     * Sends a hello message to a sharded actor entity. Best practice for sharded actors: - Get
     * reference on each request (references are lightweight) - No need to cache (entities are
     * managed by cluster sharding) - No need to check existence (entities are created on-demand) -
     * Use askBuilder for timeout and error handling
     *
     * @param message The message to send
     * @param entityId The ID of the entity to send the message to
     * @return A Mono containing the response from the actor
     */
    public Mono<String> hello(String message, String entityId) {
        // Get a reference to the actor entity
        SpringShardedActorRef<HelloActor.Command> actorRef =
                springActorSystem.sharded(HelloActor.class).withId(entityId).get();

        // Send the message using the fluent ask builder with timeout and error handling
        CompletionStage<String> response = actorRef.<HelloActor.SayHello, String>askBuilder(
                        replyTo -> new HelloActor.SayHello(replyTo, message))
                .withTimeout(Duration.ofSeconds(3))
                .onTimeout(() -> "Request timed out for entity: " + entityId)
                .execute();

        // Convert the CompletionStage to a Mono for reactive programming
        return Mono.fromCompletionStage(response);
    }
}
