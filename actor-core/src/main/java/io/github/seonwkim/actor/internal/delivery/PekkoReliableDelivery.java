package io.github.seonwkim.actor.internal.delivery;

import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.delivery.ReliableSender;
import io.github.seonwkim.actor.delivery.RetryPolicy;
import io.github.seonwkim.actor.delivery.ShardedReliableSender;
import io.github.seonwkim.actor.delivery.SpringReliableDelivery;
import io.github.seonwkim.actor.internal.PekkoActorRef;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.RecipientRef;
import org.apache.pekko.actor.typed.Scheduler;

/**
 * {@link SpringReliableDelivery} on Pekko's ask: a {@link RetryingSend} per message, to an actor ref
 * or to a sharded entity ref (both are Pekko {@link RecipientRef}s).
 */
public final class PekkoReliableDelivery implements SpringReliableDelivery {

    private final SpringActorSystem actorSystem;

    public PekkoReliableDelivery(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public <T> ReliableSender.Builder<T> to(SpringActorRef<T> receiver) {
        Objects.requireNonNull(receiver, "receiver");
        return new ReliableSender.Builder<>() {
            private RetryPolicy retry = RetryPolicy.defaults();

            @Override
            public ReliableSender.Builder<T> withRetry(RetryPolicy retryPolicy) {
                this.retry = Objects.requireNonNull(retryPolicy, "retryPolicy");
                return this;
            }

            @Override
            public ReliableSender<T> build() {
                RetryPolicy policy = retry;
                RecipientRef<T> target = PekkoActorRef.toPekko(receiver);
                Scheduler scheduler = scheduler();
                return new ReliableSender<>() {
                    @Override
                    public CompletionStage<Void> send(T message) {
                        return send(message, newKey());
                    }

                    @Override
                    public CompletionStage<Void> send(T message, String idempotencyKey) {
                        return RetryingSend.start(target, message, idempotencyKey, policy, scheduler);
                    }
                };
            }
        };
    }

    @Override
    public <T> ShardedReliableSender.Builder<T> toSharded(Class<? extends SpringShardedActor<T>> actorClass) {
        Objects.requireNonNull(actorClass, "actorClass");
        return new ShardedReliableSender.Builder<>() {
            private RetryPolicy retry = RetryPolicy.defaults();

            @Override
            public ShardedReliableSender.Builder<T> withRetry(RetryPolicy retryPolicy) {
                this.retry = Objects.requireNonNull(retryPolicy, "retryPolicy");
                return this;
            }

            @Override
            public ShardedReliableSender<T> build() {
                RetryPolicy policy = retry;
                Scheduler scheduler = scheduler();
                return new ShardedReliableSender<>() {
                    @Override
                    public CompletionStage<Void> send(String entityId, T message) {
                        return send(entityId, message, newKey());
                    }

                    @Override
                    public CompletionStage<Void> send(String entityId, T message, String idempotencyKey) {
                        // An entity ref, as a sharded handle wraps it: it reaches the entity on any node.
                        RecipientRef<?> entity = actorSystem
                                .sharded(actorClass)
                                .withId(entityId)
                                .get()
                                .unwrap(RecipientRef.class);
                        return RetryingSend.start(entity, message, idempotencyKey, policy, scheduler);
                    }
                };
            }
        };
    }

    private Scheduler scheduler() {
        return PekkoActorSystem.raw(actorSystem).scheduler();
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }
}
