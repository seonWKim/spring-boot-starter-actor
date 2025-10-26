package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service that handles interactions with the HelloActor. It serves as an intermediary between the
 * REST API and the actor system. Uses lazy initialization to avoid blocking application startup.
 */
@Service
public class HelloService {
    private final SpringActorSystem actorSystem;

    public HelloService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public Mono<String> hello() {
        return Mono.fromCompletionStage(actorSystem.get(HelloActor.class, "hello-actor")
                .thenCompose(actor -> actor.ask(HelloActor.SayHello::new)));
    }
}
