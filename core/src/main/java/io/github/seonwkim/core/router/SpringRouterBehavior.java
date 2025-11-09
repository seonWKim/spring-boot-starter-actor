package io.github.seonwkim.core.router;

import io.github.seonwkim.core.ActorTypeRegistry;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorWithContext;
import io.github.seonwkim.core.impl.DefaultSpringActorContext;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
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
 *         return SpringRouterBehavior.builder(Command.class, ctx)
 *             .withRoutingStrategy(RoutingStrategy.roundRobin())
 *             .withPoolSize(10)
 *             .withWorkerActors(WorkerActor.class)
 *             .build();
 *     }
 * }
 *
 * &#64;Component
 * class WorkerActor implements SpringActor&lt;Command&gt; {
 *     &#64;Autowired private SomeService service; // Spring DI works!
 *
 *     &#64;Override
 *     public SpringActorBehavior&lt;Command&gt; create(SpringActorContext ctx) {
 *         return SpringActorBehavior.builder(Command.class, ctx)
 *             .onMessage(Command.class, (context, msg) -&gt; {
 *                 service.doSomething(); // Use injected service
 *                 return Behaviors.same();
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
    private final Class<? extends SpringActorWithContext<C, ?>> workerActorClass;
    private final SpringActorContext actorContext;
    @Nullable private final SupervisorStrategy supervisionStrategy;

    private SpringRouterBehavior(
            RoutingStrategy routingStrategy,
            int poolSize,
            Class<? extends SpringActorWithContext<C, ?>> workerActorClass,
            SpringActorContext actorContext,
            @Nullable SupervisorStrategy supervisionStrategy) {
        this.routingStrategy = routingStrategy;
        this.poolSize = poolSize;
        this.workerActorClass = workerActorClass;
        this.actorContext = actorContext;
        this.supervisionStrategy = supervisionStrategy;
    }

    /**
     * Creates a new builder for constructing a router behavior.
     *
     * @param commandClass The command class that worker actors handle
     * @param actorContext The Spring actor context for accessing the actor system
     * @param <C> The command type that worker actors handle
     * @return A new builder instance
     */
    public static <C> Builder<C> builder(Class<C> commandClass, SpringActorContext actorContext) {
        return new Builder<>(commandClass, actorContext);
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
            // Get the actor type registry from context
            ActorTypeRegistry registry = actorContext.registry();
            if (registry == null) {
                throw new IllegalStateException(
                        "ActorTypeRegistry is null. Cannot spawn worker actors without registry.");
            }

            // Create worker context
            SpringActorContext workerContext =
                    new DefaultSpringActorContext("worker-" + UUID.randomUUID());

            // Create worker behavior using the registry
            @SuppressWarnings("unchecked")
            SpringActorBehavior<C> workerSpringBehavior =
                    (SpringActorBehavior<C>) registry.createBehavior(workerActorClass, workerContext);
            Behavior<C> workerBehavior = workerSpringBehavior.asBehavior();

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
        private final SpringActorContext actorContext;
        @Nullable private RoutingStrategy routingStrategy;
        private int poolSize = 5;
        @Nullable private Class<? extends SpringActorWithContext<C, ?>> workerActorClass;
        @Nullable private SupervisorStrategy supervisionStrategy;

        private Builder(Class<C> commandClass, SpringActorContext actorContext) {
            this.commandClass = Objects.requireNonNull(commandClass, "Command class cannot be null");
            this.actorContext = Objects.requireNonNull(actorContext, "Actor context cannot be null");
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
         * Set the worker actor class. Workers will be Spring-managed components with full dependency
         * injection support.
         *
         * @param workerClass The Spring actor class for workers
         * @return This builder for chaining
         */
        public Builder<C> withWorkerActors(Class<? extends SpringActor<C>> workerClass) {
            this.workerActorClass = Objects.requireNonNull(workerClass, "Worker actor class cannot be null");
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
            Objects.requireNonNull(workerActorClass, "Worker actor class is required");

            SpringRouterBehavior<C> config =
                    new SpringRouterBehavior<>(
                            routingStrategy, poolSize, workerActorClass, actorContext, supervisionStrategy);
            return config.toSpringActorBehavior();
        }
    }
}
