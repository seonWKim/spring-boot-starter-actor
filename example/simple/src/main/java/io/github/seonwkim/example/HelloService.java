package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorSystem;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service that demonstrates best practices for interacting with actors.
 *
 * <p>Key patterns shown:
 * <ul>
 *   <li>Use getOrSpawn() for simplified actor lifecycle - automatically gets existing or creates new
 *   <li>Use ask() for request-response with timeout handling
 *   <li>Use SpringActorSystem as a Spring bean via dependency injection
 *   <li>Integrate actors with reactive programming (returns Mono)
 * </ul>
 *
 * <p>This is the recommended pattern for most use cases. For advanced scenarios
 * (custom supervision, explicit spawn control), see the supervision example.
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
        return Mono.fromCompletionStage(
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor").thenCompose(actor -> actor.ask(
                                new HelloActor.SayHello())
                        .withTimeout(java.time.Duration.ofSeconds(3))
                        .execute()));
    }

    /**
     * Triggers a failure in the actor, causing it to restart.
     * This demonstrates the PreRestart signal handler.
     */
    public Mono<String> triggerRestart() {
        return Mono.fromCompletionStage(
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor").thenCompose(actor -> actor.ask(
                                new HelloActor.TriggerFailure())
                        .withTimeout(java.time.Duration.ofSeconds(3))
                        .execute()));
    }

    /**
     * Stops the actor gracefully using SpringActorHandle.stop().
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
