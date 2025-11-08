package io.github.seonwkim.core.router;

import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.SpringActorWithContext;

/**
 * Interface for Spring-managed router actors. Classes implementing this interface will be
 * automatically registered with the actor system and can spawn pools of worker actors with various
 * routing strategies.
 *
 * <p>Router actors distribute incoming messages across a pool of worker actors using a specified
 * routing strategy. This provides load balancing and parallel processing capabilities.
 *
 * <p>Router actors should return a {@link SpringRouterBehavior} from the {@link #create(SpringActorContext)}
 * method, which will be automatically converted to a Pekko router pool by the framework. Note that
 * while this interface extends SpringActorWithContext, the create method returns SpringRouterBehavior
 * instead of SpringActorBehavior. The framework handles this conversion automatically during
 * actor registration.
 *
 * <p>Example usage:
 *
 * <pre>
 * &#64;Component
 * public class WorkerPoolRouter implements SpringRouterActor&lt;WorkerActor.Command&gt; {
 *
 *     &#64;Override
 *     public SpringRouterBehavior&lt;Command&gt; create(SpringActorContext ctx) {
 *         return SpringRouterBehavior.&lt;Command&gt;builder()
 *             .withRoutingStrategy(RoutingStrategy.roundRobin())
 *             .withPoolSize(10)
 *             .withWorkerClass(WorkerActor.class)
 *             .build();
 *     }
 * }
 * </pre>
 *
 * @param <C> The command type that worker actors handle
 * @see SpringRouterBehavior
 * @see RoutingStrategy
 */
public interface SpringRouterActor<C> extends SpringActorWithContext<C, SpringActorContext> {

    /**
     * Create the router behavior configuration. This method is called when the router actor is
     * spawned and should return a SpringRouterBehavior that defines the routing strategy, pool size,
     * worker class, and other router settings.
     *
     * <p>The framework will automatically convert the SpringRouterBehavior to a Pekko router pool.
     *
     * <p>Note: While this interface extends SpringActorWithContext which expects SpringActorBehavior,
     * router actors return SpringRouterBehavior. The framework handles this conversion during
     * actor registration in ActorConfiguration.
     *
     * @param context The Spring actor context
     * @return SpringRouterBehavior configuration for this router
     */
    SpringRouterBehavior<C> create(SpringActorContext context);
}

