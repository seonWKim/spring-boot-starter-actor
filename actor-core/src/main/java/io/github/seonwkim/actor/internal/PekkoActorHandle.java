package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.ActorConstants;
import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.SpringChildActorBuilder;
import io.github.seonwkim.actor.SpringChildActorReference;
import java.time.Duration;
import javax.annotation.Nullable;
import org.apache.pekko.actor.PoisonPill;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;

/** A {@link SpringActorHandle} backed by a Pekko {@link ActorRef}. */
public final class PekkoActorHandle<T> extends PekkoActorRef<T> implements SpringActorHandle<T> {

    private final ActorSystem<?> system;
    private final Duration defaultTimeout;

    @Nullable private final MessageTracing tracing;

    /** Whether {@code ref} is this node's proxy to a cluster singleton. */
    private final boolean singletonProxy;

    public PekkoActorHandle(ActorSystem<?> system, ActorRef<T> actorRef) {
        this(system, actorRef, ActorConstants.DEFAULT_TIMEOUT);
    }

    public PekkoActorHandle(ActorSystem<?> system, ActorRef<T> actorRef, Duration defaultTimeout) {
        this(system, actorRef, defaultTimeout, false);
    }

    /** A handle to a cluster singleton through this node's proxy ({@code proxy}). */
    public static <T> PekkoActorHandle<T> singleton(ActorSystem<?> system, ActorRef<T> proxy, Duration defaultTimeout) {
        return new PekkoActorHandle<>(system, proxy, defaultTimeout, true);
    }

    private PekkoActorHandle(
            ActorSystem<?> system, ActorRef<T> actorRef, Duration defaultTimeout, boolean singletonProxy) {
        super(actorRef);
        if (system == null) {
            throw new IllegalArgumentException("system must not be null");
        }
        if (defaultTimeout == null) {
            throw new IllegalArgumentException("defaultTimeout must not be null");
        }
        this.system = system;
        this.defaultTimeout = defaultTimeout;
        this.tracing = MessageTracingSetup.of(system);
        this.singletonProxy = singletonProxy;
    }

    /** Tells the actor, with the current trace when tracing is on. */
    @Override
    @SuppressWarnings("unchecked")
    public void tell(T message) {
        ref.tell(tracing == null ? message : (T) tracing.tell(message));
    }

    @Override
    public <RES> AskBuilder<RES> ask(AskCommand<RES> command) {
        return new PekkoAskBuilder<T, RES>(ref, system.scheduler(), tracing, defaultTimeout, command);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void stop() {
        if (singletonProxy) {
            // A PoisonPill would stop this node's proxy, not the singleton, and Pekko hands the same
            // stopped proxy to every later spawn on this node: its messages would be lost.
            throw new UnsupportedOperationException("stop() is not supported on a cluster singleton's handle: the "
                    + "handle is this node's proxy, and stopping it would cut this node off from the singleton, "
                    + "which keeps running on its host.");
        }
        ref.tell((T) PoisonPill.getInstance());
    }

    @Override
    public <CC> SpringChildActorReference<T, CC> child(
            Class<? extends SpringActorWithContext<CC, ?>> childActorClass, String childId) {
        return new PekkoChildActorReference<>(ref, system, childActorClass, childId, defaultTimeout);
    }

    @Override
    public <CC> SpringChildActorBuilder<T, CC> child(Class<? extends SpringActorWithContext<CC, ?>> childActorClass) {
        return new PekkoChildActorBuilder<>(ref, system, childActorClass, defaultTimeout);
    }
}
