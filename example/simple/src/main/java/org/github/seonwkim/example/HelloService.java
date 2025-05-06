package org.github.seonwkim.example;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.github.seonwkim.core.ActorSystemInstance;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class HelloService {

    private final ActorSystemInstance actorSystemInstance;
    private final CompletionStage<ActorRef<HelloActor.Command>> helloActor;

    public HelloService(ActorSystemInstance actorSystemInstance)
            throws ExecutionException, InterruptedException {
        this.actorSystemInstance = actorSystemInstance;
        this.helloActor = actorSystemInstance.spawn(HelloActor.Command.class, "hello");
    }

    public Mono<String> hello() {
        return Mono.fromCompletionStage(
                this.helloActor
                        .thenCompose(actorRef ->
                                             AskPattern.ask(actorRef,
                                                            HelloActor.SayHello::new,
                                                            // builds SayHello(replyTo)
                                                            Duration.ofSeconds(3),
                                                            actorSystemInstance.getRaw().scheduler()
                                             )
                        )
        );
    }
}
