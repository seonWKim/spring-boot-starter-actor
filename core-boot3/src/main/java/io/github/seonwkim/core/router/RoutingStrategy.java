package io.github.seonwkim.core.router;

import io.github.seonwkim.core.router.strategy.BroadcastRoutingStrategy;
import io.github.seonwkim.core.router.strategy.ConsistentHashingRoutingStrategy;
import io.github.seonwkim.core.router.strategy.RandomRoutingStrategy;
import io.github.seonwkim.core.router.strategy.RoundRobinRoutingStrategy;
import org.apache.pekko.actor.typed.javadsl.PoolRouter;

/**
 * Defines the routing strategy for distributing messages across worker actors. Routing strategies
 * determine how incoming messages are distributed to workers in a router pool.
 *
 * <p>This interface provides a Spring-friendly abstraction over Pekko's routing logic. Each
 * strategy wraps a corresponding Pekko router implementation.
 *
 * <p>Available strategies:
 *
 * <ul>
 *   <li>{@link #roundRobin()} - Distribute messages evenly in circular fashion
 *   <li>{@link #random()} - Distribute messages randomly
 *   <li>{@link #broadcast()} - Send all messages to all workers
 *   <li>{@link #consistentHashing()} - Route messages by hash key for session affinity
 * </ul>
 *
 * @see SpringRouterBehavior
 */
public interface RoutingStrategy {

    /**
     * Get the name of this routing strategy for logging and metrics.
     *
     * @return The strategy name (e.g., "RoundRobin", "Random")
     */
    String getName();

    /**
     * Apply this routing strategy to a Pekko pool router. This method is called internally when
     * creating the router actor.
     *
     * @param poolRouter The pool router to configure
     * @param <T> The message type
     * @return The configured pool router with this routing strategy applied
     */
    <T> PoolRouter<T> applyToPool(PoolRouter<T> poolRouter);

    /**
     * Round Robin routing strategy distributes messages evenly across all workers in a circular
     * fashion. This is the default and most commonly used strategy.
     *
     * <p>Message distribution pattern: Worker 1 → Worker 2 → Worker 3 → Worker 1 → ...
     *
     * <p>Best for:
     *
     * <ul>
     *   <li>Equal distribution of work
     *   <li>Predictable load balancing
     *   <li>Tasks with similar processing time
     * </ul>
     *
     * @return A Round Robin routing strategy
     */
    static RoutingStrategy roundRobin() {
        return new RoundRobinRoutingStrategy();
    }

    /**
     * Random routing strategy distributes messages randomly across all workers. No state tracking is
     * required, making it very lightweight.
     *
     * <p>Message distribution pattern: Random selection from available workers
     *
     * <p>Best for:
     *
     * <ul>
     *   <li>Simple distribution without state tracking
     *   <li>Non-critical workloads
     *   <li>Avoiding ordering effects
     * </ul>
     *
     * @return A Random routing strategy
     */
    static RoutingStrategy random() {
        return new RandomRoutingStrategy();
    }

    /**
     * Broadcast routing strategy sends all messages to all workers in the pool.
     *
     * <p>Message distribution pattern: Every message goes to ALL workers
     *
     * <p><strong>Note:</strong> Each worker receives every message, so this increases message volume
     * by the pool size factor. Use sparingly for high-volume systems.
     *
     * <p>Best for:
     *
     * <ul>
     *   <li>Cache invalidation across all workers
     *   <li>Configuration updates
     *   <li>Notifications that all workers need to receive
     *   <li>Coordinated state updates
     * </ul>
     *
     * @return A Broadcast routing strategy
     */
    static RoutingStrategy broadcast() {
        return new BroadcastRoutingStrategy();
    }

    /**
     * Consistent Hashing routing strategy ensures messages with the same hash key always route to
     * the same worker, enabling session affinity and stateful processing.
     *
     * <p>Message distribution pattern: Messages with same hash key → Same worker
     *
     * <p>Messages implementing {@link ConsistentHashable} provide explicit hash keys. Other messages
     * use {@code toString()} as the hash key.
     *
     * <p>Best for:
     *
     * <ul>
     *   <li>User session management (same userId → same worker)
     *   <li>Entity-based processing (same orderId → same worker)
     *   <li>Stateful message processing
     *   <li>Cache locality optimization
     * </ul>
     *
     * @return A Consistent Hashing routing strategy with default virtual nodes factor (10)
     * @see ConsistentHashable
     */
    static RoutingStrategy consistentHashing() {
        return new ConsistentHashingRoutingStrategy();
    }

    /**
     * Consistent Hashing routing strategy with custom virtual nodes factor.
     *
     * <p>Virtual nodes factor affects distribution quality:
     *
     * <ul>
     *   <li>Higher values (e.g., 10-20) = better distribution, more memory
     *   <li>Lower values (e.g., 1-5) = less memory, potential hotspots
     * </ul>
     *
     * @param virtualNodesFactor Number of virtual nodes per worker (must be >= 1)
     * @return A Consistent Hashing routing strategy
     * @see ConsistentHashable
     */
    static RoutingStrategy consistentHashing(int virtualNodesFactor) {
        return new ConsistentHashingRoutingStrategy(virtualNodesFactor);
    }
}
