package org.github.seonwkim.example;

import java.time.Duration;

import org.github.seonwkim.core.SpringActorRef;
import org.github.seonwkim.core.SpringActorSystem;
import org.github.seonwkim.example.HelloActor.Command;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class HelloService {

    private final SpringActorRef<Command> helloActor;

    public HelloService(SpringActorSystem springActorSystem) {
        this.helloActor = springActorSystem.spawn(HelloActor.Command.class, "default", Duration.ofSeconds(3))
                                           .toCompletableFuture()
                                           .join();
    }

    public Mono<String> hello() {
        return Mono.fromCompletionStage(helloActor.ask(HelloActor.SayHello::new, Duration.ofSeconds(3)));
    }
}
