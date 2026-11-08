package io.github.seonwkim.actor.router;

import io.github.seonwkim.actor.SpringActor;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.SpringActorWithContext;
import io.github.seonwkim.actor.SupervisionStrategy;
import java.util.Objects;
import javax.annotation.Nullable;

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

    private SpringRouterBehavior() {}

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

        @Nullable private SupervisionStrategy supervisionStrategy;

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
        public Builder<C> withSupervisionStrategy(SupervisionStrategy strategy) {
            this.supervisionStrategy = Objects.requireNonNull(strategy, "Supervision strategy cannot be null");
            return this;
        }

        /**
         * Build the SpringActorBehavior with the configured router settings.
         *
         * @return A SpringActorBehavior that implements the configured router
         * @throws NullPointerException if routing strategy or worker actor class is not set
         */
        public SpringActorBehavior<C> build() {
            if (routingStrategy == null) {
                throw new IllegalStateException("Routing strategy is required. "
                        + "Call withRoutingStrategy() before building. "
                        + "Available strategies: RoutingStrategy.roundRobin(), "
                        + "RoutingStrategy.random(), RoutingStrategy.broadcast(), "
                        + "or RoutingStrategy.consistentHashing()");
            }
            if (workerActorClass == null) {
                throw new IllegalStateException("Worker actor class is required. "
                        + "Call withWorkerActors(YourWorkerActor.class) before building.");
            }

            return SpringActorBehavior.router(
                    new RouterPool<>(routingStrategy, poolSize, workerActorClass, supervisionStrategy));
        }
    }
}
