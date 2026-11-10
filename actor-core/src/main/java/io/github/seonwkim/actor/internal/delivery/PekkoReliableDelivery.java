package io.github.seonwkim.actor.internal.delivery;

import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.delivery.ReliableSender;
import io.github.seonwkim.actor.delivery.ShardedReliableSender;
import io.github.seonwkim.actor.delivery.SpringReliableDelivery;
import io.github.seonwkim.actor.internal.PekkoActorRef;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import java.util.Objects;
import java.util.UUID;

/** {@link SpringReliableDelivery} on Pekko's reliable delivery. */
public final class PekkoReliableDelivery implements SpringReliableDelivery {

    private final SpringActorSystem actorSystem;

    public PekkoReliableDelivery(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public <T> ReliableSender.Builder<T> to(SpringActorRef<T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        return new ReliableSender.Builder<>() {
            private String producerId = UUID.randomUUID().toString();

            @Override
            public ReliableSender.Builder<T> withProducerId(String producerId) {
                this.producerId = requireProducerId(producerId);
                return this;
            }

            @Override
            public ReliableSender<T> start() {
                return LocalReliableSender.start(
                        PekkoActorSystem.raw(actorSystem), PekkoActorRef.toPekko(consumer), producerId);
            }
        };
    }

    @Override
    public <T> ShardedReliableSender.Builder<T> toSharded(Class<? extends SpringShardedActor<T>> actorClass) {
        Objects.requireNonNull(actorClass, "actorClass");
        return new ShardedReliableSender.Builder<>() {
            private String producerId = UUID.randomUUID().toString();

            @Override
            public ShardedReliableSender.Builder<T> withProducerId(String producerId) {
                this.producerId = requireProducerId(producerId);
                return this;
            }

            @Override
            public ShardedReliableSender<T> start() {
                return ShardedDeliverySupport.require().start(actorSystem, actorClass, producerId);
            }
        };
    }

    private static String requireProducerId(String producerId) {
        if (producerId == null || producerId.isBlank()) {
            throw new IllegalArgumentException("producerId must not be blank");
        }
        return producerId;
    }
}
