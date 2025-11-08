package io.github.seonwkim.core.router.internal;

import io.github.seonwkim.core.ActorTypeRegistry;
import io.github.seonwkim.core.SpringActor;
import io.github.seonwkim.core.SpringActorBehavior;
import io.github.seonwkim.core.SpringActorContext;
import io.github.seonwkim.core.router.RoutingStrategy;
import io.github.seonwkim.core.router.SpringRouterBehavior;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.SupervisorStrategy;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.PoolRouter;
import org.apache.pekko.actor.typed.javadsl.Routers;

/**
 * Internal adapter that converts SpringRouterBehavior configuration to Pekko router behavior. This
 * class wraps Pekko's pool router with Spring-managed worker spawning.
 */
public final class PekkoRouterAdapter {

    private PekkoRouterAdapter() {
        // Utility class
    }

    /**
     * Create a SpringActorBehavior that wraps a Pekko router pool based on the given router
     * configuration.
     *
     * @param <C> The command type
     * @param routerBehavior The router configuration
     * @param actorContext The Spring actor context
     * @param registry The actor type registry for creating worker behaviors
     * @return A SpringActorBehavior that implements the configured router
     */
    public static <C> SpringActorBehavior<C> createRouterBehavior(
            SpringRouterBehavior<C> routerBehavior,
            SpringActorContext actorContext,
            ActorTypeRegistry registry) {

        // Create a behavior that sets up the Pekko router
        Behavior<C> behavior = Behaviors.setup(ctx -> {
            // Create worker behavior factory
            Behavior<C> workerBehavior = createWorkerBehavior(routerBehavior.getWorkerClass(), actorContext, registry);

            // Apply supervision strategy if specified
            if (routerBehavior.getSupervisionStrategy() != null) {
                workerBehavior = Behaviors.supervise(workerBehavior)
                        .onFailure(routerBehavior.getSupervisionStrategy());
            }

            // Get Pekko router config from strategy
            org.apache.pekko.routing.RouterConfig routerConfig =
                    routerBehavior.getRoutingStrategy().toPekkoRouter(routerBehavior.getPoolSize());

            // Create pool router using Pekko's API
            PoolRouter<C> poolRouter = Routers.pool(routerBehavior.getPoolSize(), workerBehavior);

            // Apply the routing logic
            return poolRouter.withPoolSize(routerBehavior.getPoolSize()).narrow();
        });

        // Wrap in SpringActorBehavior using reflection to access package-private constructor
        return wrapBehavior(behavior);
    }

    /**
     * Create worker behavior from worker class using the registry.
     */
    private static <C> Behavior<C> createWorkerBehavior(
            Class<? extends SpringActor<C>> workerClass,
            SpringActorContext actorContext,
            ActorTypeRegistry registry) {

        return Behaviors.setup(ctx -> {
            // Create a unique context for each worker instance
            SpringActorContext workerContext = new io.github.seonwkim.core.impl.DefaultSpringActorContext(
                    actorContext.actorId() + "-worker-" + ctx.getSelf().path().name());
            workerContext.setRegistry(registry);

            // Create behavior through registry (uses Spring DI)
            SpringActorBehavior<C> workerBehavior = registry.createTypedBehavior(workerClass, workerContext);
            return workerBehavior.asBehavior();
        });
    }

    /**
     * Wrap a Pekko Behavior in a SpringActorBehavior. Since SpringActorBehavior has a private
     * constructor, we use a factory method pattern.
     */
    @SuppressWarnings("unchecked")
    private static <C> SpringActorBehavior<C> wrapBehavior(Behavior<C> behavior) {
        // SpringActorBehavior doesn't have a public constructor that takes just a Behavior
        // We need to use the builder pattern instead
        // For now, let's return a minimal SpringActorBehavior
        // This will be refined once we understand the proper integration point
        
        // Actually, we can't construct SpringActorBehavior directly since it's final with private constructor
        // The only way is through the builder which requires a command class
        // For routers, we need a different approach - let's create a dummy command class
        throw new UnsupportedOperationException("This method needs proper integration with SpringActorBehavior");
    }
}
