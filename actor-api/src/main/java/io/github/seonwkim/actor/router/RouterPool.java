package io.github.seonwkim.actor.router;

import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.SupervisionStrategy;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * A router as {@link SpringRouterBehavior.Builder#build()} describes it: the actor engine reads it to
 * start the pool. Application code builds routers with {@link SpringRouterBehavior#builder}.
 *
 * @param <C> the command type the workers handle
 */
public final class RouterPool<C> {
    private final RoutingStrategy strategy;
    private final int size;
    private final Class<? extends SpringActorWithContext<C, ?>> workerClass;

    @Nullable private final SupervisionStrategy workerSupervision;

    RouterPool(
            RoutingStrategy strategy,
            int size,
            Class<? extends SpringActorWithContext<C, ?>> workerClass,
            @Nullable SupervisionStrategy workerSupervision) {
        this.strategy = strategy;
        this.size = size;
        this.workerClass = workerClass;
        this.workerSupervision = workerSupervision;
    }

    public RoutingStrategy strategy() {
        return strategy;
    }

    public int size() {
        return size;
    }

    public Class<? extends SpringActorWithContext<C, ?>> workerClass() {
        return workerClass;
    }

    public Optional<SupervisionStrategy> workerSupervision() {
        return Optional.ofNullable(workerSupervision);
    }
}
