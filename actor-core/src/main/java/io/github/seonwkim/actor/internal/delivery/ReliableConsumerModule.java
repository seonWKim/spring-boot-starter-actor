package io.github.seonwkim.actor.internal.delivery;

import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.delivery.ReliableDelivery;
import io.github.seonwkim.actor.delivery.StaleGenerationException;
import io.github.seonwkim.actor.internal.ModuleSupport;
import io.github.seonwkim.actor.internal.PekkoBehaviors;
import java.time.Duration;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.BehaviorInterceptor;
import org.apache.pekko.actor.typed.TypedActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * Runs {@link ReliableDelivery#consumer}: the actor's behavior behind an interceptor that unwraps
 * {@link ReliableEnvelope}s, skips keys it remembers (or rejects them when the payload differs), and
 * acks once a handler returned.
 *
 * <p>The actor's own supervision stays inside the behavior, where it may swallow a handler's
 * exception. So the interceptor doesn't ack because the behavior returned, but because a handler
 * did: the behavior is rebuilt with each message handler marking its success.
 */
public final class ReliableConsumerModule implements ModuleSupport {

    /** Read by {@link #toBehavior}; set from spring.actor.delivery.idempotency-ttl. */
    public static final String TTL_KEY = "spring-actor.delivery.idempotency-ttl";

    /** Read by {@link #toBehavior}; set from spring.actor.delivery.max-remembered-keys. */
    public static final String MAX_KEYS_KEY = "spring-actor.delivery.max-remembered-keys";

    static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    static final int DEFAULT_MAX_KEYS = 10_000;

    @Override
    @SuppressWarnings("unchecked")
    public <C> @Nullable Behavior<C> toBehavior(Object moduleDefinition) {
        if (!(moduleDefinition instanceof ReliableDelivery.ConsumerDefinition<?> definition)) {
            return null;
        }
        if (definition.behavior().kind() != SpringActorBehavior.Kind.DEFINED) {
            throw new IllegalArgumentException(
                    "ReliableDelivery.consumer takes a behavior built with SpringActorBehavior.builder(...)");
        }
        HandlerOutcome outcome = new HandlerOutcome();
        Behavior<Object> inner = (Behavior<Object>) (Behavior<?>)
                PekkoBehaviors.toPekko(marking(definition.behavior().definition(), outcome));
        Duration ttlOverride = definition.idempotencyTtl();
        Behavior<Object> behavior = Behaviors.setup(ctx -> {
            var config = ctx.getSystem().settings().config();
            Duration ttl = ttlOverride != null
                    ? ttlOverride
                    : config.hasPath(TTL_KEY) ? config.getDuration(TTL_KEY) : DEFAULT_TTL;
            int maxKeys = config.hasPath(MAX_KEYS_KEY) ? config.getInt(MAX_KEYS_KEY) : DEFAULT_MAX_KEYS;
            return Behaviors.intercept(
                    () -> new DedupInterceptor(new RememberedKeys(ttl, maxKeys, System::nanoTime), outcome), inner);
        });
        return (Behavior<C>) (Behavior<?>) behavior;
    }

    /**
     * The same behavior, with every message handler setting {@code outcome.handled} when it returns,
     * and {@code outcome.stale} when it throws {@link StaleGenerationException} for a reliable
     * message. That exception doesn't reach the actor's supervision, so the actor isn't restarted.
     * Rebuilt through the public builder, field by field of {@link SpringActorBehavior.Definition};
     * {@code ReliableConsumerRebuildTest} fails when the definition gains a field this doesn't copy.
     */
    static <C, S> SpringActorBehavior<C> marking(
            SpringActorBehavior.Definition<C, S> definition, HandlerOutcome outcome) {
        SpringActorBehavior.Builder<C, S> builder = SpringActorBehavior.builder(
                        definition.commandClass(), definition.actorContext())
                .withState(definition.stateFactory());
        for (SpringActorBehavior.MessageHandler<C, S, ?> handler : definition.messageHandlers()) {
            addMarking(builder, handler, outcome);
        }
        for (SpringActorBehavior.SignalHandler<C, S, ?> handler : definition.signalHandlers()) {
            addSignal(builder, handler);
        }
        if (definition.supervisionStrategy() != null) {
            builder.withSupervisionStrategy(definition.supervisionStrategy());
        }
        if (definition.mdcForMessage() != null) {
            builder.withMdc(definition.mdcForMessage());
        }
        return builder.build();
    }

    private static <C, S, M extends C> void addMarking(
            SpringActorBehavior.Builder<C, S> builder,
            SpringActorBehavior.MessageHandler<C, S, M> handler,
            HandlerOutcome outcome) {
        builder.onMessage(handler.type(), (state, message) -> {
            SpringActorBehavior<C> next;
            try {
                next = handler.handler().apply(state, message);
            } catch (StaleGenerationException e) {
                if (!outcome.delivering) {
                    throw e;
                }
                outcome.stale = true;
                return SpringActorBehavior.same();
            }
            outcome.handled = true;
            return next;
        });
    }

    private static <C, S, M extends io.github.seonwkim.actor.ActorSignal> void addSignal(
            SpringActorBehavior.Builder<C, S> builder, SpringActorBehavior.SignalHandler<C, S, M> handler) {
        builder.onSignal(handler.type(), handler.handler());
    }

    /**
     * What the handlers did with the reliable message being delivered. Only the actor touches it.
     */
    static final class HandlerOutcome {
        /** A reliable message is being delivered. */
        boolean delivering;
        /** A message handler returned. */
        boolean handled;
        /** A message handler threw {@link StaleGenerationException}. */
        boolean stale;
    }

    /**
     * The payload's fingerprint, compared when a key comes again: its {@code hashCode}, or none when
     * its class keeps {@code Object}'s identity hash code, which differs for every copy (a message
     * from another node, or rebuilt by the caller), so it can't tell payloads apart.
     */
    static @Nullable Integer fingerprint(Object message) {
        return VALUE_HASH_CODE.get(message.getClass()) ? message.hashCode() : null;
    }

    private static final ClassValue<Boolean> VALUE_HASH_CODE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("hashCode").getDeclaringClass() != Object.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    };

    private static final class DedupInterceptor extends BehaviorInterceptor<Object, Object> {

        private final RememberedKeys keys;
        private final HandlerOutcome outcome;

        DedupInterceptor(RememberedKeys keys, HandlerOutcome outcome) {
            super(Object.class);
            this.keys = keys;
            this.outcome = outcome;
        }

        @Override
        public Behavior<Object> aroundReceive(
                TypedActorContext<Object> ctx, Object msg, BehaviorInterceptor.ReceiveTarget<Object> target) {
            if (!(msg instanceof ReliableEnvelope envelope)) {
                return target.apply(ctx, msg);
            }
            Integer fingerprint = fingerprint(envelope.message());
            switch (keys.check(envelope.idempotencyKey(), fingerprint)) {
                case DUPLICATE -> {
                    envelope.replyTo().tell(ReliableAck.ACK);
                    return Behaviors.same();
                }
                case CONFLICT -> {
                    envelope.replyTo().tell(ReliableAck.PAYLOAD_CONFLICT);
                    return Behaviors.same();
                }
                case NEW -> {}
            }
            outcome.delivering = true;
            outcome.handled = false;
            outcome.stale = false;
            Behavior<Object> next;
            try {
                next = target.apply(ctx, envelope.message()); // throws: no ack, the sender retries
            } finally {
                outcome.delivering = false;
            }
            if (outcome.handled) {
                keys.remember(envelope.idempotencyKey(), fingerprint);
                envelope.replyTo().tell(ReliableAck.ACK);
            } else if (outcome.stale) {
                envelope.replyTo().tell(ReliableAck.STALE_GENERATION);
            }
            return next;
        }
    }
}
