package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.core.shard.SpringShardedActorRef;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.springframework.stereotype.Service;
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

        // Send the message using the fluent ask API with timeout and error handling
        CompletionStage<String> response = actorRef.ask(new HelloActor.SayHello(message))
                .withTimeout(Duration.ofSeconds(3))
                .onTimeout(() -> "Request timed out for entity: " + entityId)
                .execute();

        // Convert the CompletionStage to a Mono for reactive programming
        return Mono.fromCompletionStage(response);
    }
}
