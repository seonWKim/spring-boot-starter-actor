package io.github.seonwkim.core.router;

import io.github.seonwkim.core.router.strategy.RandomRoutingStrategy;
import io.github.seonwkim.core.router.strategy.RoundRobinRoutingStrategy;

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
     * Convert this strategy to Pekko's router configuration. This method is called internally when
     * creating the router actor.
     *
     * @param poolSize The number of worker actors in the pool
     * @return Pekko RouterConfig for this strategy
     */
    org.apache.pekko.routing.RouterConfig toPekkoRouter(int poolSize);

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
}
