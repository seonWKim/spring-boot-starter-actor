package io.github.seonwkim.core.router;

import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;

import io.github.seonwkim.core.impl.DefaultSpringActorContext;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.PoolRouter;
import org.apache.pekko.actor.typed.javadsl.Routers;

/**
 * Utility class for creating router behaviors that distribute messages across a pool of worker
 * actors. This class provides a Spring-friendly API for Pekko's pool router functionality.
 *
 * <p>Routers can be used in regular SpringActor implementations to create pools of workers:
 *
 * <pre>
 * &#64;Component
 * public class WorkerPoolActor implements SpringActor&lt;Command&gt; {
 *
 *     &#64;Override
 *     public SpringActorBehavior&lt;Command&gt; create(SpringActorContext ctx) {
 *         return SpringRouterBehavior.builder(Command.class)
 *             .withRoutingStrategy(RoutingStrategy.roundRobin())
 *             .withPoolSize(10)
 *             .withWorkerBehavior(workerContext -&gt; {
 *                 // Define worker behavior here
 *                 return Behaviors.receive(Command.class)
 *                     .onMessage(Command.class, (context, msg) -&gt; {
 *                         // Handle message
 *                         return Behaviors.same();
 *                     })
 *                     .build();
 *             })
 *             .build();
 *     }
 * }
 * </pre>
 *
 * @param <C> The command type that worker actors handle
 */
public final class SpringRouterBehavior<C> {

    private final RoutingStrategy routingStrategy;
    private final int poolSize;
    private final Function<SpringActorContext, Behavior<C>> workerBehaviorFactory;
    @Nullable private final SupervisorStrategy supervisionStrategy;

    private SpringRouterBehavior(
            RoutingStrategy routingStrategy,
            int poolSize,
            java.util.function.Function<SpringActorContext, Behavior<C>> workerBehaviorFactory,
            @Nullable SupervisorStrategy supervisionStrategy) {
        this.routingStrategy = routingStrategy;
        this.poolSize = poolSize;
        this.workerBehaviorFactory = workerBehaviorFactory;
        this.supervisionStrategy = supervisionStrategy;
    }

    /**
     * Creates a new builder for constructing a router behavior.
     *
     * @param commandClass The command class that worker actors handle
     * @param <C> The command type that worker actors handle
     * @return A new builder instance
     */
    public static <C> Builder<C> builder(Class<C> commandClass) {
        return new Builder<>(commandClass);
    }

    /**
     * Convert this router configuration to a Spring Actor Behavior. This method creates a Pekko
     * router pool based on the configuration.
     *
     * @return A SpringActorBehavior that implements the configured router
     */
    public SpringActorBehavior<C> toSpringActorBehavior() {
        // Create the Pekko router behavior
        Behavior<C> routerBehavior = Behaviors.setup(ctx -> {
            // Create worker behavior
            SpringActorContext workerContext = new DefaultSpringActorContext("worker-" + UUID.randomUUID());
            Behavior<C> workerBehavior = workerBehaviorFactory.apply(workerContext);

            // Apply supervision strategy if specified
            if (supervisionStrategy != null) {
                workerBehavior = Behaviors.supervise(workerBehavior).onFailure(supervisionStrategy);
            }

            // Create pool router with the configured routing strategy
            PoolRouter<C> poolRouter = Routers.pool(poolSize, workerBehavior);
            // Apply the routing strategy configuration
            poolRouter = routingStrategy.applyToPool(poolRouter);

            return poolRouter.narrow();
        });

        // Wrap in a SpringActorBehavior
        return SpringActorBehavior.wrap(routerBehavior);
    }

    /**
     * Builder for creating router behaviors with a fluent API.
     *
     * @param <C> The command type that worker actors handle
     */
    public static final class Builder<C> {
        private final Class<C> commandClass;
        @Nullable private RoutingStrategy routingStrategy;
        private int poolSize = 5;
        @Nullable private java.util.function.Function<SpringActorContext, Behavior<C>> workerBehaviorFactory;
        @Nullable private SupervisorStrategy supervisionStrategy;

        private Builder(Class<C> commandClass) {
            this.commandClass = Objects.requireNonNull(commandClass, "Command class cannot be null");
        }

        /**
         * Set the routing strategy for message distribution.
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
         */
        public Builder<C> withPoolSize(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("Pool size must be positive, got: " + size);
            }
            this.poolSize = size;
            return this;
        }

        /**
         * Set the worker behavior factory.
         *
         * @param factory The worker behavior factory
         * @return This builder for chaining
         */
        public Builder<C> withWorkerBehavior(java.util.function.Function<SpringActorContext, Behavior<C>> factory) {
            this.workerBehaviorFactory = Objects.requireNonNull(factory, "Worker behavior factory cannot be null");
            return this;
        }

        /**
         * Set the supervision strategy for worker actors.
         *
         * @param strategy The supervision strategy
         * @return This builder for chaining
         */
        public Builder<C> withSupervisionStrategy(SupervisorStrategy strategy) {
            this.supervisionStrategy = Objects.requireNonNull(strategy, "Supervision strategy cannot be null");
            return this;
        }

        /**
         * Build the SpringActorBehavior with the configured router settings.
         *
         * @return A SpringActorBehavior that implements the configured router
         */
        public SpringActorBehavior<C> build() {
            Objects.requireNonNull(routingStrategy, "Routing strategy is required");
            Objects.requireNonNull(workerBehaviorFactory, "Worker behavior factory is required");

            SpringRouterBehavior<C> config =
                    new SpringRouterBehavior<>(routingStrategy, poolSize, workerBehaviorFactory, supervisionStrategy);
            return config.toSpringActorBehavior();
        }
    }
}
