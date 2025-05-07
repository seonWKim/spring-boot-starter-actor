package org.github.seonwkim.core;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.RecipientRef;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.japi.function.Function;

public class SpringShardedActorRef<T> {

    private final Scheduler scheduler;
    private final EntityRef<T> entityRef;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    public SpringShardedActorRef(Scheduler scheduler, EntityRef<T> entityRef) {
        this.scheduler = scheduler;
        this.entityRef = entityRef;
    }

    public <REQ extends T, RES> CompletionStage<RES> ask(Function<ActorRef<RES>, REQ> messageFactory, Duration timeout) {
        @SuppressWarnings("unchecked")
        RecipientRef<REQ> recipient = (RecipientRef<REQ>) entityRef;
        return AskPattern.ask(recipient, messageFactory, timeout, scheduler);
    }

    public <REQ extends T, RES> CompletionStage<RES> ask(Function<ActorRef<RES>, REQ> messageFactory) {
        return ask(messageFactory, DEFAULT_TIMEOUT);
    }

    public void tell(T message) {
        entityRef.tell(message);
    }
}
