package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.ActorConstants;
import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.SpringChildActorBuilder;
import io.github.seonwkim.actor.SpringChildActorReference;
import java.time.Duration;
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Scheduler;

/** A {@link SpringActorHandle} backed by a Pekko {@link ActorRef}. */
public final class PekkoActorHandle<T> extends PekkoActorRef<T> implements SpringActorHandle<T> {

    private final Scheduler scheduler;
    private final Duration defaultTimeout;

    public PekkoActorHandle(Scheduler scheduler, ActorRef<T> actorRef) {
        this(scheduler, actorRef, ActorConstants.DEFAULT_TIMEOUT);
    }

    public PekkoActorHandle(Scheduler scheduler, ActorRef<T> actorRef, Duration defaultTimeout) {
        super(actorRef);
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        if (defaultTimeout == null) {
            throw new IllegalArgumentException("defaultTimeout must not be null");
        }
        this.scheduler = scheduler;
        this.defaultTimeout = defaultTimeout;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <RES> AskBuilder<RES> ask(AskCommand<RES> command) {
        return new PekkoAskBuilder<T, RES>(
                ref, scheduler, defaultTimeout, replyTo -> (T) command.withReplyTo(new PekkoActorRef<>(replyTo)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void stop() {
        ref.tell((T) PoisonPill.getInstance());
    }

    @Override
    public <CC> SpringChildActorReference<T, CC> child(
            Class<? extends SpringActorWithContext<CC, ?>> childActorClass, String childId) {
        return new PekkoChildActorReference<>(ref, scheduler, childActorClass, childId, defaultTimeout);
    }

    @Override
    public <CC> SpringChildActorBuilder<T, CC> child(Class<? extends SpringActorWithContext<CC, ?>> childActorClass) {
        return new PekkoChildActorBuilder<>(ref, scheduler, childActorClass, defaultTimeout);
    }
}
