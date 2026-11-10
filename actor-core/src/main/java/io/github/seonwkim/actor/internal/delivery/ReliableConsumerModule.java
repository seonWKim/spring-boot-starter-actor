package io.github.seonwkim.actor.internal.delivery;

import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.delivery.ReliableDelivery;
import io.github.seonwkim.actor.internal.ModuleSupport;
import io.github.seonwkim.actor.internal.PekkoBehaviors;
import io.github.seonwkim.actor.internal.PekkoProps;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.BehaviorInterceptor;
import org.apache.pekko.actor.typed.TypedActorContext;
import org.apache.pekko.actor.typed.delivery.ConsumerController;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * Runs {@link ReliableDelivery#consumer}: the actor's behavior behind an interceptor that owns one
 * Pekko {@link ConsumerController} per sender and confirms a delivery when its handler returns.
 *
 * <p>When a handler throws, the interceptor stops that sender's controller before the failure goes
 * on to supervision. The sender then registers a new controller (local) or the next message from
 * the sharded producer creates one, and the producer resends what was not confirmed. Supervision
 * sits outside the interceptor, so a restart also starts with no controllers.
 */
public final class ReliableConsumerModule implements ModuleSupport {

    @Override
    @SuppressWarnings("unchecked")
    public <C> @Nullable Behavior<C> toBehavior(Object moduleDefinition) {
        if (!(moduleDefinition instanceof ReliableDelivery.ConsumerDefinition<?> definition)) {
            return null;
        }
        SpringActorBehavior<?> inner = definition.behavior();
        if (inner.kind() == SpringActorBehavior.Kind.DEFINED
                && inner.definition().supervisionStrategy() != null) {
            throw new IllegalArgumentException(
                    "A reliable consumer can't use the builder's withSupervisionStrategy:"
                            + " it hides failed deliveries. Pass the strategy to ReliableDelivery.consumer(behavior, strategy)");
        }
        Behavior<Object> innerBehavior = (Behavior<Object>) PekkoBehaviors.toPekko(inner);
        Behavior<Object> behavior =
                Behaviors.setup(ctx -> Behaviors.intercept(DeliveryInterceptor::new, innerBehavior));
        if (definition.supervision() != null) {
            behavior = Behaviors.supervise(behavior).onFailure(PekkoProps.supervisor(definition.supervision()));
        }
        return (Behavior<C>) (Behavior<?>) behavior;
    }

    /** Asks a reliable consumer for its controller for {@code producerId}, created on first use. */
    public record Connect(String producerId, ActorRef<Connected> replyTo) {}

    /** The reply to {@link Connect}. */
    public record Connected(ActorRef<ConsumerController.Command<Object>> controller) {}

    private static final class DeliveryInterceptor extends BehaviorInterceptor<Object, Object> {

        private final Map<String, ActorRef<ConsumerController.Command<Object>>> controllers = new HashMap<>();

        DeliveryInterceptor() {
            super(Object.class);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Behavior<Object> aroundReceive(
                TypedActorContext<Object> ctx, Object msg, BehaviorInterceptor.ReceiveTarget<Object> target) {
            if (msg instanceof Connect connect) {
                connect.replyTo().tell(new Connected(controller(ctx, connect.producerId())));
                return Behaviors.same();
            }
            if (msg instanceof ConsumerController.SequencedMessage<?> sequenced) {
                // From a sharded producer: it addresses the entity, not a controller.
                controller(ctx, sequenced.producerId()).tell((ConsumerController.SequencedMessage<Object>) sequenced);
                return Behaviors.same();
            }
            if (msg instanceof ConsumerController.Delivery<?> delivery) {
                Behavior<Object> next;
                try {
                    next = target.apply(ctx, delivery.message());
                } catch (RuntimeException | Error e) {
                    // Not confirmed: a new controller makes the producer resend it.
                    controllers.values().remove(delivery.confirmTo());
                    ctx.asJava().stop(delivery.confirmTo());
                    throw e;
                }
                delivery.confirmTo().tell(ConsumerController.confirmed());
                return next;
            }
            return target.apply(ctx, msg);
        }

        private ActorRef<ConsumerController.Command<Object>> controller(
                TypedActorContext<Object> ctx, String producerId) {
            return controllers.computeIfAbsent(producerId, id -> {
                ActorRef<ConsumerController.Command<Object>> controller =
                        ctx.asJava().spawnAnonymous(ConsumerController.create());
                ActorRef<ConsumerController.Delivery<Object>> deliverTo =
                        ctx.asJava().getSelf().narrow();
                controller.tell(new ConsumerController.Start<>(deliverTo));
                return controller;
            });
        }
    }
}
