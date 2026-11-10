package io.github.seonwkim.actor.delivery;

import io.github.seonwkim.actor.SpringActorBehavior;
import java.time.Duration;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * Makes an actor a receiver of reliable messages. Wrap the behavior the actor's {@code create}
 * returns; this works for actors and for sharded entities.
 *
 * <pre>{@code
 * return ReliableDelivery.consumer(SpringActorBehavior.builder(Command.class, ctx)
 *         .onMessage(PlaceOrder.class, (c, msg) -> {
 *             orders.save(msg);              // throws: no ack, the sender retries
 *             return SpringActorBehavior.same();
 *         })
 *         .build());
 * }</pre>
 *
 * <p><b>Acknowledgement:</b> a reliable message is acknowledged when one of the actor's handlers
 * returned normally. If no handler matched or the handler threw, it is not acknowledged and the
 * sender retries. Supervision is the actor's own, as usual.
 *
 * <p><b>Deduplication:</b> the actor remembers the idempotency keys it handled for the idempotency
 * TTL ({@code spring.actor.delivery.idempotency-ttl}, 10 minutes by default) and at most
 * {@code spring.actor.delivery.max-remembered-keys} keys, oldest dropped first. A message with a
 * remembered key is acknowledged again without calling the handler. The keys live in the actor's
 * memory: after the actor restarted or moved, a retry runs the handler again.
 *
 * <p>Plain {@code tell} and {@code ask} to the same actor keep working, without deduplication.
 */
public final class ReliableDelivery {

    private ReliableDelivery() {}

    /**
     * The behavior, receiving reliable messages, with the global idempotency TTL.
     *
     * @param behavior a behavior built with {@link SpringActorBehavior#builder}
     * @throws IllegalArgumentException when the actor starts, if {@code behavior} was not built with
     *     the builder
     */
    public static <C> SpringActorBehavior<C> consumer(SpringActorBehavior<C> behavior) {
        return SpringActorBehavior.ofModule(new ConsumerDefinition<>(behavior, null));
    }

    /** The behavior, receiving reliable messages, remembering their keys for {@code idempotencyTtl}. */
    public static <C> SpringActorBehavior<C> consumer(SpringActorBehavior<C> behavior, Duration idempotencyTtl) {
        Objects.requireNonNull(idempotencyTtl, "idempotencyTtl");
        if (idempotencyTtl.isNegative() || idempotencyTtl.isZero()) {
            throw new IllegalArgumentException("idempotencyTtl must be positive, got " + idempotencyTtl);
        }
        return SpringActorBehavior.ofModule(new ConsumerDefinition<>(behavior, idempotencyTtl));
    }

    /** What {@link #consumer} hands to the engine. Not for application code. */
    public record ConsumerDefinition<C>(SpringActorBehavior<C> behavior, @Nullable Duration idempotencyTtl) {
        public ConsumerDefinition {
            Objects.requireNonNull(behavior, "behavior");
        }
    }
}
