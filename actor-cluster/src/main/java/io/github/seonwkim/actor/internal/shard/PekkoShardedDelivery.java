package io.github.seonwkim.actor.internal.shard;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.delivery.ShardedReliableSender;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import io.github.seonwkim.actor.internal.delivery.PendingConfirmations;
import io.github.seonwkim.actor.internal.delivery.SenderNames;
import io.github.seonwkim.actor.internal.delivery.ShardedDeliverySupport;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nullable;
import org.apache.pekko.Done;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.delivery.ConsumerController;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingEnvelope;
import org.apache.pekko.cluster.sharding.typed.delivery.ShardingProducerController;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * Reliable senders to sharded entities: a system actor that owns a Pekko
 * {@link ShardingProducerController}. The entity side is the same interceptor as for local actors
 * ({@code ReliableConsumerModule}), which creates a consumer controller per producer.
 *
 * <p>The producer controller resends the first unconfirmed message to an entity that has been
 * quiet for {@code pekko.reliable-delivery.sharding.producer-controller.resend-first-unconfirmed-idle-timeout}
 * (10 seconds by default): that is how an entity that crashed or was passivated gets it again.
 */
public final class PekkoShardedDelivery implements ShardedDeliverySupport {

    @Override
    public <T> ShardedReliableSender<T> start(
            SpringActorSystem system, Class<? extends SpringShardedActor<T>> actorClass, String producerId) {
        // A handle is the public way to the class's entity type; nothing is sent to it.
        ShardedEntityType<T> type =
                system.sharded(actorClass).withId(producerId).get().entityType();
        ClusterSharding sharding = system.unwrap(ClusterSharding.class);
        ActorRef<Command> actor = PekkoActorSystem.raw(system)
                .systemActorOf(
                        Behaviors.setup(
                                ctx -> new State(ctx, sharding, PekkoSharding.typeKey(type), producerId).behavior()),
                        SenderNames.of("reliable-sharded-sender", producerId),
                        Props.empty());
        return new Sender<>(actor);
    }

    private record Sender<T>(ActorRef<Command> actor) implements ShardedReliableSender<T> {
        @Override
        public CompletionStage<Void> send(String entityId, T message) {
            if (entityId == null || entityId.isBlank()) {
                throw new IllegalArgumentException("entityId must not be blank");
            }
            CompletableFuture<Void> confirmed = new CompletableFuture<>();
            actor.tell(new Send(new Addressed(entityId, message), confirmed));
            return confirmed;
        }

        @Override
        public void close() {
            actor.tell(Close.INSTANCE);
        }
    }

    interface Command {}

    private record Addressed(String entityId, Object message) {}

    private record Send(Addressed message, CompletableFuture<Void> confirmed) implements Command {}

    private record Next(ShardingProducerController.RequestNext<Object> requestNext) implements Command {}

    private record ToEntity(ShardingEnvelope<ConsumerController.SequencedMessage<Object>> envelope)
            implements Command {}

    private enum Close implements Command {
        INSTANCE
    }

    private static final class State {
        private final ActorContext<Command> ctx;
        private final ClusterSharding sharding;
        private final EntityTypeKey<?> typeKey;
        private final String producerId;
        private final PendingConfirmations<Addressed> pending = new PendingConfirmations<>();

        @Nullable private ShardingProducerController.RequestNext<Object> demand;

        @SuppressWarnings("unchecked")
        State(ActorContext<Command> ctx, ClusterSharding sharding, EntityTypeKey<?> typeKey, String producerId) {
            this.ctx = ctx;
            this.sharding = sharding;
            this.typeKey = typeKey;
            this.producerId = producerId;
            // The starter's regions take its own envelope, so the producer controller sends here and
            // this actor forwards through an entity ref.
            ActorRef<ShardingEnvelope<ConsumerController.SequencedMessage<Object>>> region = ctx.messageAdapter(
                    (Class<ShardingEnvelope<ConsumerController.SequencedMessage<Object>>>)
                            (Class<?>) ShardingEnvelope.class,
                    ToEntity::new);
            ActorRef<ShardingProducerController.Command<Object>> producer = ctx.spawn(
                    ShardingProducerController.create(Object.class, producerId, region, Optional.empty()), "producer");
            ActorRef<ShardingProducerController.RequestNext<Object>> requestNext =
                    ctx.messageAdapter(ShardingProducerController.requestNextClass(), Next::new);
            producer.tell(new ShardingProducerController.Start<>(requestNext));
        }

        @SuppressWarnings("unchecked")
        Behavior<Command> behavior() {
            return Behaviors.receive(Command.class)
                    .onMessage(Send.class, send -> {
                        pending.add(send.message(), send.confirmed());
                        return sendNext();
                    })
                    .onMessage(Next.class, next -> {
                        demand = next.requestNext();
                        return sendNext();
                    })
                    .onMessage(ToEntity.class, toEntity -> {
                        sharding.entityRefFor(
                                        (EntityTypeKey<Object>) typeKey,
                                        toEntity.envelope().entityId())
                                .tell(toEntity.envelope().message());
                        return Behaviors.same();
                    })
                    .onMessage(Close.class, close -> Behaviors.stopped())
                    .onSignal(PostStop.class, stop -> {
                        pending.failAll("The reliable sender " + producerId + " was closed");
                        return Behaviors.same();
                    })
                    .build();
        }

        private Behavior<Command> sendNext() {
            if (demand == null) {
                return Behaviors.same();
            }
            PendingConfirmations.Waiting<Addressed> next = pending.next();
            if (next != null) {
                CompletableFuture<Void> confirmed = next.confirmed();
                // The reply carries no sequence number, so each message gets its own listener.
                ActorRef<Done> listener = ctx.spawnAnonymous(Behaviors.receiveMessage(done -> {
                    confirmed.complete(null);
                    return Behaviors.stopped();
                }));
                demand.askNextTo()
                        .tell(new ShardingProducerController.MessageWithConfirmation<>(
                                next.message().entityId(), next.message().message(), listener));
                demand = null;
            }
            return Behaviors.same();
        }
    }
}
