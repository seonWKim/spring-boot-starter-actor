package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorSystem;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service that handles interactions with the HelloActor. Demonstrates the simplified getOrSpawn
 * pattern for actor lifecycle management.
 */
@Service
public class HelloService {
    private final SpringActorSystem actorSystem;

    public HelloService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Best practice: Use getOrSpawn for simple cases where you don't need caching.
     * It automatically handles the exists -> get -> spawn logic in a single call.
     */
    public Mono<String> hello() {
        return Mono.fromCompletionStage(actorSystem
                .getOrSpawn(HelloActor.class, "hello-actor")
                .thenCompose(actor -> actor.ask(HelloActor.SayHello::new)));
    }

    /**
     * Triggers a failure in the actor, causing it to restart.
     * This demonstrates the PreRestart signal handler.
     */
    public Mono<String> triggerRestart() {
        return Mono.fromCompletionStage(actorSystem
                .getOrSpawn(HelloActor.class, "hello-actor")
                .thenCompose(actor -> actor.ask(HelloActor.TriggerFailure::new)));
    }

    /**
     * Stops the actor gracefully using SpringActorRef.stop().
     * This demonstrates the PostStop signal handler.
     */
    public Mono<String> stopActor() {
        return Mono.fromCompletionStage(
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor").thenCompose(actor -> {
                    actor.stop();
                    return Mono.just("Actor stopped - PostStop signal will be triggered")
                            .toFuture();
                }));
    }
}
