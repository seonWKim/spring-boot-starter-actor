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

    /**
     * A Pekko pool router over {@code pool.size()} workers, each created from the registry with its
     * own id. The id is chosen once per worker, outside its supervision, so it survives restarts.
     */
    static <C> Behavior<C> pool(RouterPool<C> pool) {
        return Behaviors.setup(ctx -> {
            Behavior<C> worker = Behaviors.setup(workerCtx -> {
                SpringActorContext workerContext = new DefaultSpringActorContext("worker-" + UUID.randomUUID());
                @SuppressWarnings("unchecked")
                SpringActorBehavior<C> defined = (SpringActorBehavior<C>)
                        ActorTypeRegistry.of(workerCtx.getSystem()).createBehavior(pool.workerClass(), workerContext);
                Behavior<C> workerBehavior = MessageTracingSetup.receiving(
                        workerCtx.getSystem(),
                        PekkoBehaviors.forNewActor(defined, pool.workerClass(), workerCtx.getSystem()));
                if (pool.workerSupervision().isPresent()) {
                    workerBehavior = Behaviors.supervise(workerBehavior)
                            .onFailure(PekkoProps.supervisor(
                                    pool.workerSupervision().get()));
                }
                return workerBehavior;
            });
            return withStrategy(Routers.pool(pool.size(), worker), pool.strategy())
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
        if (message instanceof TracedMessage traced) {
            return hashKey(traced.message());
        }
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
