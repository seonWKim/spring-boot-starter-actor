package org.github.seonwkim.core;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.RecipientRef;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.japi.function.Function;
import org.github.seonwkim.core.RootGuardian.Command;

public class SpringActorRef<T> {

    private final ActorSystem<Command> actorSystem;
    private final ActorRef<T> actorRef;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    public SpringActorRef(ActorSystem<Command> actorSystem, ActorRef<T> actorRef) {
        this.actorSystem = actorSystem;
        this.actorRef = actorRef;
    }

    public <REQ extends T, RES> CompletionStage<RES> ask(Function<ActorRef<RES>, REQ> messageFactory, Duration timeout) {
        Scheduler scheduler = actorSystem.scheduler();
        @SuppressWarnings("unchecked")
        RecipientRef<REQ> recipient = (RecipientRef<REQ>) actorRef;
        return AskPattern.ask(recipient, messageFactory, timeout, scheduler);
    }

    public <REQ extends T, RES> CompletionStage<RES> ask(Function<ActorRef<RES>, REQ> messageFactory) {
        return ask(messageFactory, DEFAULT_TIMEOUT);
    }

    public void tell(T message) {
        actorRef.tell(message);
    }

    public ActorRef<T> getRef() {
        return actorRef;
    }
}
