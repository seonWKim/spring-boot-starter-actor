package io.github.seonwkim.core.router.strategy;

import io.github.seonwkim.core.router.RoutingStrategy;
import org.apache.pekko.actor.typed.javadsl.PoolRouter;

/**
 * Random routing strategy distributes messages randomly across all workers. No state tracking is
 * required, making it very lightweight.
 *
 * <p>Messages are distributed randomly to any available worker.
 *
 * <p>Best for:
 *
 * <ul>
 *   <li>Simple distribution without state tracking
 *   <li>Non-critical workloads
 *   <li>Avoiding ordering effects
 * </ul>
 *
 * @see RoutingStrategy#random()
 */
public final class RandomRoutingStrategy implements RoutingStrategy {

    @Override
    public String getName() {
        return "Random";
    }

    @Override
    public <T> PoolRouter<T> applyToPool(PoolRouter<T> poolRouter) {
        return poolRouter.withRandomRouting();
    }

    @Override
    public String toString() {
        return "RandomRoutingStrategy";
    }
}
