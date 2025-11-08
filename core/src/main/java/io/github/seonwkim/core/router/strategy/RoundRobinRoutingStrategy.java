package io.github.seonwkim.core.router.strategy;

import io.github.seonwkim.core.router.RoutingStrategy;
import org.apache.pekko.routing.RoundRobinPool;
import org.apache.pekko.routing.RouterConfig;

/**
 * Round Robin routing strategy distributes messages evenly across all workers in a circular
 * fashion. This is the default and most commonly used routing strategy.
 *
 * <p>Messages are distributed in order: Worker 1 → Worker 2 → Worker 3 → Worker 1 → ...
 *
 * <p>Best for:
 *
 * <ul>
 *   <li>Equal distribution of work
 *   <li>Predictable load balancing
 *   <li>Tasks with similar processing time
 * </ul>
 *
 * @see RoutingStrategy#roundRobin()
 */
public final class RoundRobinRoutingStrategy implements RoutingStrategy {

    @Override
    public String getName() {
        return "RoundRobin";
    }

    @Override
    public RouterConfig toPekkoRouter(int poolSize) {
        return new RoundRobinPool(poolSize);
    }

    @Override
    public String toString() {
        return "RoundRobinRoutingStrategy";
    }
}
