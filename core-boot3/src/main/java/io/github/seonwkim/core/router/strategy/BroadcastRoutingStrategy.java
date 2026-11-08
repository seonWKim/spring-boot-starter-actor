package io.github.seonwkim.core.router.strategy;

import io.github.seonwkim.core.router.RoutingStrategy;
import org.apache.pekko.actor.typed.javadsl.PoolRouter;

/**
 * Broadcast routing strategy sends all messages to all workers in the pool.
 *
 * <p>Every message sent to the router is delivered to ALL worker actors, making this useful for:
 *
 * <ul>
 *   <li>Cache invalidation across all workers
 *   <li>Configuration updates
 *   <li>Notifications that all workers need to receive
 *   <li>Coordinated state updates
 * </ul>
 *
 * <p><strong>Note:</strong> Each worker receives every message, so this increases message volume
 * by the pool size factor. Use sparingly for high-volume systems.
 *
 * @see RoutingStrategy#broadcast()
 */
public final class BroadcastRoutingStrategy implements RoutingStrategy {

    @Override
    public String getName() {
        return "Broadcast";
    }

    @Override
    public <T> PoolRouter<T> applyToPool(PoolRouter<T> poolRouter) {
        // Broadcast ALL messages to all workers using a predicate that always returns true
        return poolRouter.withBroadcastPredicate(msg -> true);
    }

    @Override
    public String toString() {
        return "BroadcastRoutingStrategy";
    }
}
