package org.github.seonwkim.example;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.github.seonwkim.core.SpringActorSystem;
import org.github.seonwkim.core.SpringShardedActorRef;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class HelloService {

    private final SpringActorSystem springActorSystem;

    public HelloService(SpringActorSystem springActorSystem) {
        this.springActorSystem = springActorSystem;
    }

    public Mono<String> hello(
            String message,
            String entityId
    ) {
        SpringShardedActorRef<HelloActor.Command> actorRef = springActorSystem.entityRef(HelloActor.TYPE_KEY,
                                                                                         entityId);
        CompletionStage<String> response = actorRef.ask(
                replyTo -> new HelloActor.SayHello(replyTo, message), Duration.ofSeconds(3));
        return Mono.fromCompletionStage(response);
    }
}
