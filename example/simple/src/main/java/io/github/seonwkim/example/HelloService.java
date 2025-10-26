package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Service that handles interactions with the HelloActor. It serves as an intermediary between the
 * REST API and the actor system.
 */
@Service
public class HelloService {
    private final SpringActorSystem actorSystem;

    public HelloService(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    public Mono<String> hello() {
        return Mono.fromCompletionStage(actorSystem
                .spawn(HelloActor.class)
                .withId("hello-actor")
                .withTimeout(Duration.ofSeconds(3))
                .start()
                .thenCompose(helloActor -> helloActor.ask(HelloActor.SayHello::new)));
    }
}
