package io.github.seonwkim.actor.internal;

import javax.annotation.Nullable;
import org.apache.pekko.actor.setup.Setup;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.BehaviorInterceptor;
import org.apache.pekko.actor.typed.TypedActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/** The actor system's {@link MessageTracing}, present only while tracing is on. */
public final class MessageTracingSetup extends Setup {

    private final MessageTracing tracing;

    public MessageTracingSetup(MessageTracing tracing) {
        this.tracing = tracing;
    }

    /** The tracing of {@code system}, or null when tracing is off. */
    @Nullable public static MessageTracing of(ActorSystem<?> system) {
        return system.settings()
                .setup()
                .get(MessageTracingSetup.class)
                .map(setup -> setup.tracing)
                .orElse(null);
    }

    /**
     * {@code behavior} receiving {@link TracedMessage}s: it gets the message inside, handled in the
     * sender's trace. Unchanged when tracing is off. Applied to the behavior an actor is spawned with.
     */
    public static <T> Behavior<T> receiving(ActorSystem<?> system, Behavior<T> behavior) {
        MessageTracing tracing = of(system);
        if (tracing == null) {
            return behavior;
        }
        @SuppressWarnings("unchecked")
        Behavior<T> traced =
                (Behavior<T>) Behaviors.intercept(() -> new Unwrap(tracing), (Behavior<Object>) (Behavior<?>) behavior);
        return traced;
    }

    /** Sees only {@link TracedMessage}s; every other message passes through untouched. */
    private static final class Unwrap extends BehaviorInterceptor<Object, Object> {
        private final MessageTracing tracing;

        @SuppressWarnings("unchecked")
        Unwrap(MessageTracing tracing) {
            super((Class<Object>) (Class<?>) TracedMessage.class);
            this.tracing = tracing;
        }

        @Override
        public Behavior<Object> aroundReceive(TypedActorContext<Object> ctx, Object msg, ReceiveTarget<Object> target) {
            TracedMessage traced = (TracedMessage) msg;
            return tracing.receive(
                    traced, ctx.asJava().getSelf().path().toString(), () -> target.apply(ctx, traced.message()));
        }
    }
}
