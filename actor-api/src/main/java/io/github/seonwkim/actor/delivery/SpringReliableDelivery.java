package io.github.seonwkim.actor.delivery;

import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.shard.SpringShardedActor;

/**
 * Builds reliable senders. Inject it as a bean. The receiving actor wraps its behavior with
 * {@link ReliableDelivery#consumer}.
 *
 * <pre>{@code
 * ReliableSender<OrderActor.Command> orders = reliableDelivery.to(orderActor).build();
 * orders.send(new PlaceOrder("o-1"), "o-1");   // retried until acked; runs once per key
 *
 * ShardedReliableSender<OrderEntity.Command> entities = reliableDelivery.toSharded(OrderEntity.class).build();
 * entities.send("order-1", new Pay(100), "payment-77");
 * }</pre>
 */
public interface SpringReliableDelivery {

    /** A sender to one actor, such as a handle from {@code actorSystem.actor(...)}. */
    <T> ReliableSender.Builder<T> to(SpringActorRef<T> receiver);

    /** A sender to the entities of a sharded actor class, on any node. Needs cluster mode. */
    <T> ShardedReliableSender.Builder<T> toSharded(Class<? extends SpringShardedActor<T>> actorClass);
}
