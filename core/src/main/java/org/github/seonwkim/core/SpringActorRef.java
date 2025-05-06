package org.github.seonwkim.core;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.RecipientRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.japi.function.Function;

public class SpringActorRef<T> {

    private final Scheduler scheduler;
    private final ActorRef<T> actorRef;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    public SpringActorRef(Scheduler scheduler, ActorRef<T> actorRef) {
        this.scheduler = scheduler;
        this.actorRef = actorRef;
    }

    public <REQ extends T, RES> CompletionStage<RES> ask(Function<ActorRef<RES>, REQ> messageFactory, Duration timeout) {
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
