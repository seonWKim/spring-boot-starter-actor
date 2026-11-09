package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import io.github.seonwkim.actor.router.ConsistentHashable;
import io.github.seonwkim.actor.router.RouterPool;
import io.github.seonwkim.actor.router.RoutingStrategy;
import java.util.UUID;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.PoolRouter;
import org.apache.pekko.actor.typed.javadsl.Routers;

/** Builds routers as Pekko pool routers. */
public final class PekkoRouters {

    private PekkoRouters() {}

    /** A Pekko pool router over {@code pool.size()} workers, each created from the registry. */
    static <C> Behavior<C> pool(RouterPool<C> pool) {
        return Behaviors.setup(ctx -> {
            SpringActorContext workerContext = new DefaultSpringActorContext("worker-" + UUID.randomUUID());
            @SuppressWarnings("unchecked")
            SpringActorBehavior<C> worker = (SpringActorBehavior<C>)
                    ActorTypeRegistry.of(ctx.getSystem()).createBehavior(pool.workerClass(), workerContext);
            Behavior<C> workerBehavior = PekkoBehaviors.toPekko(worker);
            if (pool.workerSupervision().isPresent()) {
                workerBehavior = Behaviors.supervise(workerBehavior)
                        .onFailure(
                                PekkoProps.supervisor(pool.workerSupervision().get()));
            }
            return withStrategy(Routers.pool(pool.size(), workerBehavior), pool.strategy())
                    .narrow();
        });
    }

    static <C> PoolRouter<C> withStrategy(PoolRouter<C> pool, RoutingStrategy strategy) {
        if (strategy instanceof RoutingStrategy.RoundRobin) {
            return pool.withRoundRobinRouting();
        }
        if (strategy instanceof RoutingStrategy.Random) {
            return pool.withRandomRouting();
        }
        if (strategy instanceof RoutingStrategy.Broadcast) {
            return pool.withBroadcastPredicate(message -> true);
        }
        if (strategy instanceof RoutingStrategy.ConsistentHashing hashing) {
            return pool.withConsistentHashingRouting(hashing.virtualNodesFactor(), PekkoRouters::hashKey);
        }
        throw new IllegalArgumentException("Unknown routing strategy: " + strategy);
    }

    static String hashKey(Object message) {
        if (message instanceof ConsistentHashable hashable) {
            String key = hashable.getConsistentHashKey();
            if (key == null) {
                throw new IllegalStateException(
                        "ConsistentHashable.getConsistentHashKey() returned null for message: " + message);
            }
            return key;
        }
        return message.toString();
    }
}
