package io.github.seonwkim.actor.delivery;

import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SupervisionStrategy;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * Makes an actor a receiver of reliable deliveries. Wrap the behavior the actor's {@code create}
 * returns; this works for actors and for sharded entities.
 *
 * <pre>{@code
 * return ReliableDelivery.consumer(
 *         SpringActorBehavior.builder(Command.class, ctx)
 *                 .onMessage(PlaceOrder.class, (c, msg) -> {
 *                     orders.save(msg);              // throws: not confirmed, delivered again
 *                     return SpringActorBehavior.same();
 *                 })
 *                 .build(),
 *         SupervisionStrategy.restart());
 * }</pre>
 *
 * <p><b>Confirmation:</b> a reliably sent message is confirmed when its handler returns. If the
 * handler throws, the message is not confirmed and the sender delivers it again after the actor
 * restarted or resumed. Messages from one sender arrive one at a time, in order. Plain {@code tell}
 * and {@code ask} to the same actor keep working and are not confirmed.
 *
 * <p><b>At least once:</b> a message can arrive twice, for example when the actor crashed after its
 * handler had already written to a database. Make handlers idempotent.
 */
public final class ReliableDelivery {

    private ReliableDelivery() {}

    /**
     * The behavior, receiving reliable deliveries. On a failure the actor follows the supervision of
     * its spawn builder; without one it stops, as any actor does.
     *
     * @throws IllegalArgumentException when the actor starts, if {@code behavior} sets its own
     *     supervision strategy: pass it to {@link #consumer(SpringActorBehavior, SupervisionStrategy)}
     */
    public static <C> SpringActorBehavior<C> consumer(SpringActorBehavior<C> behavior) {
        return SpringActorBehavior.ofModule(new ConsumerDefinition<>(behavior, null));
    }

    /**
     * The behavior, receiving reliable deliveries, supervised with {@code supervision}. Use this
     * instead of the builder's {@code withSupervisionStrategy}, which can't tell a failed delivery
     * from a handled one.
     */
    public static <C> SpringActorBehavior<C> consumer(
            SpringActorBehavior<C> behavior, SupervisionStrategy supervision) {
        return SpringActorBehavior.ofModule(
                new ConsumerDefinition<>(behavior, Objects.requireNonNull(supervision, "supervision")));
    }

    /** What {@link #consumer} hands to the engine. Not for application code. */
    public record ConsumerDefinition<C>(SpringActorBehavior<C> behavior, @Nullable SupervisionStrategy supervision) {
        public ConsumerDefinition {
            Objects.requireNonNull(behavior, "behavior");
        }
    }
}
