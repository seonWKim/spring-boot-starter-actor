package io.github.seonwkim.actor.delivery;

import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.shard.SpringShardedActor;

/**
 * Starts reliable senders. Inject it as a bean. The receiving actor wraps its behavior with
 * {@link ReliableDelivery#consumer}.
 *
 * <pre>{@code
 * ReliableSender<OrderActor.Command> orders = reliableDelivery.to(orderActor).start();
 * orders.send(new PlaceOrder("o-1")).toCompletableFuture().join(); // returns once confirmed
 *
 * ShardedReliableSender<OrderEntity.Command> entities = reliableDelivery.toSharded(OrderEntity.class).start();
 * entities.send("order-1", new PlaceOrder("o-1"));
 * }</pre>
 */
public interface SpringReliableDelivery {

    /** A sender to one actor of this node, such as a handle from {@code actorSystem.actor(...)}. */
    <T> ReliableSender.Builder<T> to(SpringActorRef<T> consumer);

    /**
     * A sender to the entities of a sharded actor class, on any node.
     *
     * @throws IllegalStateException on {@code start()}, when the actor system doesn't run in cluster mode
     */
    <T> ShardedReliableSender.Builder<T> toSharded(Class<? extends SpringShardedActor<T>> actorClass);
}
