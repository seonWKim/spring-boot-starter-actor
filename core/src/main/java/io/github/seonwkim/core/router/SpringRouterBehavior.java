package io.github.seonwkim.core.router;

import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.PoolRouter;
import org.apache.pekko.actor.typed.javadsl.Routers;

/**
 * A behavior configuration for router actors that provides Spring-friendly configuration for Pekko
 * routers. This class holds the configuration for a router pool including the routing strategy,
 * pool size, worker class, and supervision strategy.
 *
 * <p>This is a pure configuration object that describes how to create and configure a router pool.
 * The actual message routing is handled by Pekko's proven router implementations.
 *
 * <p>Example usage:
 *
 * <pre>
 * &#64;Component
 * public class WorkerPoolRouter implements SpringRouterActor&lt;Command&gt; {
 *
 *     &#64;Override
 *     public SpringRouterBehavior&lt;Command&gt; create(SpringActorContext ctx) {
 *         return SpringRouterBehavior.&lt;Command&gt;builder()
 *             .withRoutingStrategy(RoutingStrategy.roundRobin())
 *             .withPoolSize(10)
 *             .withWorkerClass(WorkerActor.class)
 *             .withSupervisionStrategy(SupervisorStrategy.restart())
 *             .build();
 *     }
 * }
 * </pre>
 *
 * @param <C> The command type that worker actors handle
 * @see SpringRouterActor
 * @see RoutingStrategy
 */
public final class SpringRouterBehavior<C> {

    private final RoutingStrategy routingStrategy;
    private final int poolSize;
    private final Class<? extends SpringActor<C>> workerClass;
    @Nullable private final SupervisorStrategy supervisionStrategy;

    private SpringRouterBehavior(
            RoutingStrategy routingStrategy,
            int poolSize,
            Class<? extends SpringActor<C>> workerClass,
            @Nullable SupervisorStrategy supervisionStrategy) {
        this.routingStrategy = routingStrategy;
        this.poolSize = poolSize;
        this.workerClass = workerClass;
        this.supervisionStrategy = supervisionStrategy;
    }

    /**
     * Creates a new builder for constructing a SpringRouterBehavior.
     *
     * @param <C> The command type that worker actors handle
     * @return A new builder instance
     */
    public static <C> Builder<C> builder() {
        return new Builder<>();
    }

    /**
     * Get the routing strategy for this router.
     *
     * @return The routing strategy
     */
    public RoutingStrategy getRoutingStrategy() {
        return routingStrategy;
    }

    /**
     * Get the pool size (number of worker actors).
     *
     * @return The pool size
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Get the worker actor class.
     *
     * @return The worker actor class
     */
    public Class<? extends SpringActor<C>> getWorkerClass() {
        return workerClass;
    }

    /**
     * Get the supervision strategy for worker actors.
     *
     * @return The supervision strategy, or null if not specified
     */
    @Nullable public SupervisorStrategy getSupervisionStrategy() {
        return supervisionStrategy;
    }

    /**
     * Convert this router configuration to a Spring Actor Behavior. This method creates a Pekko
     * router pool based on the configuration.
     *
     * <p>This method is called by the framework and should not be called directly by users.
     *
     * @param actorContext The Spring actor context
     * @param workerBehaviorFactory A factory for creating worker behaviors
     * @return A SpringActorBehavior that implements the configured router
     */
    public SpringActorBehavior<C> toSpringActorBehavior(
            SpringActorContext actorContext,
            java.util.function.Function<SpringActorContext, Behavior<C>> workerBehaviorFactory) {

        // Create the Pekko router behavior
        Behavior<C> routerBehavior = Behaviors.setup(ctx -> {
            // Create worker behavior
            Behavior<C> workerBehavior = workerBehaviorFactory.apply(actorContext);

            // Apply supervision strategy if specified
            if (supervisionStrategy != null) {
                workerBehavior = Behaviors.supervise(workerBehavior).onFailure(supervisionStrategy);
            }

            // Create pool router - Pekko will handle the routing logic based on the strategy
            PoolRouter<C> poolRouter = Routers.pool(poolSize, workerBehavior);

            return poolRouter.narrow();
        });

        // Wrap in a SpringActorBehavior using the package-private wrap method
        return SpringActorBehavior.wrap(routerBehavior);
    }

    /**
     * Builder for creating SpringRouterBehavior instances with a fluent API.
     *
     * @param <C> The command type that worker actors handle
     */
    public static final class Builder<C> {
        @Nullable private RoutingStrategy routingStrategy;
        private int poolSize = 5;
        @Nullable private Class<? extends SpringActor<C>> workerClass;
        @Nullable private SupervisorStrategy supervisionStrategy;

        private Builder() {}

        /**
         * Set the routing strategy for message distribution.
         *
         * <p>Available strategies:
         *
         * <ul>
         *   <li>{@link RoutingStrategy#roundRobin()} - Distribute evenly in circular fashion
         *   <li>{@link RoutingStrategy#random()} - Distribute randomly
         * </ul>
         *
         * @param strategy The routing strategy
         * @return This builder for chaining
         */
        public Builder<C> withRoutingStrategy(RoutingStrategy strategy) {
            this.routingStrategy = Objects.requireNonNull(strategy, "Routing strategy cannot be null");
            return this;
        }

        /**
         * Set the pool size (number of worker actors). Default is 5.
         *
         * @param size The number of worker actors
         * @return This builder for chaining
         * @throws IllegalArgumentException if size is not positive
         */
        public Builder<C> withPoolSize(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("Pool size must be positive, got: " + size);
            }
            this.poolSize = size;
            return this;
        }

        /**
         * Set the worker actor class. Worker actors will be instantiated from this class using Spring
         * DI.
         *
         * @param workerClass The worker actor class
         * @return This builder for chaining
         */
        public Builder<C> withWorkerClass(Class<? extends SpringActor<C>> workerClass) {
            this.workerClass = Objects.requireNonNull(workerClass, "Worker class cannot be null");
            return this;
        }

        /**
         * Set the supervision strategy for worker actors. This determines how worker failures are
         * handled.
         *
         * <p>Common strategies:
         *
         * <ul>
         *   <li>{@code SupervisorStrategy.restart()} - Restart failed workers
         *   <li>{@code SupervisorStrategy.stop()} - Stop failed workers
         *   <li>{@code SupervisorStrategy.resume()} - Resume failed workers
         * </ul>
         *
         * @param strategy The supervision strategy
         * @return This builder for chaining
         */
        public Builder<C> withSupervisionStrategy(SupervisorStrategy strategy) {
            this.supervisionStrategy = Objects.requireNonNull(strategy, "Supervision strategy cannot be null");
            return this;
        }

        /**
         * Build the SpringRouterBehavior with the configured settings.
         *
         * @return A new SpringRouterBehavior instance
         * @throws NullPointerException if routing strategy or worker class is not set
         */
        public SpringRouterBehavior<C> build() {
            Objects.requireNonNull(routingStrategy, "Routing strategy is required");
            Objects.requireNonNull(workerClass, "Worker class is required");

            return new SpringRouterBehavior<>(routingStrategy, poolSize, workerClass, supervisionStrategy);
        }
    }
}
