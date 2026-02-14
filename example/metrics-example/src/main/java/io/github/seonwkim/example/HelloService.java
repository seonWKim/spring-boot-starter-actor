package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorSystem;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class HelloService {
    private final SpringActorSystem actorSystem;

    public HelloService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public Mono<String> getActorPath() {
        return Mono.fromCompletionStage(
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor").thenApply(actor -> actor.getPath()));
    }

    public Mono<String> hello() {
        return Mono.fromCompletionStage(
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor").thenCompose(actor -> actor.ask(
                                new HelloActor.SayHello())
                        .withTimeout(java.time.Duration.ofSeconds(3))
                        .execute()));
    }

    public Mono<String> triggerRestart() {
        return Mono.fromCompletionStage(
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor").thenCompose(actor -> actor.ask(
                                new HelloActor.TriggerFailure())
                        .withTimeout(java.time.Duration.ofSeconds(3))
                        .execute()));
    }

    public Mono<String> stopActor() {
        return Mono.fromCompletionStage(
                actorSystem.getOrSpawn(HelloActor.class, "hello-actor").thenCompose(actor -> {
                    actor.stop();
                    return Mono.just("Actor stopped - PostStop signal will be triggered")
                            .toFuture();
                }));
    }
}
