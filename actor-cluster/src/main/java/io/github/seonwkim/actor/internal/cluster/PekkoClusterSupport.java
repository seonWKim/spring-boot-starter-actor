package io.github.seonwkim.actor.internal.cluster;

import io.github.seonwkim.actor.internal.ClusterSupport;
import io.github.seonwkim.actor.internal.shard.PekkoShardedActorBuilder;
import io.github.seonwkim.actor.internal.shard.PekkoSharding;
import io.github.seonwkim.actor.internal.shard.ShardedActorRegistry;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorBuilder;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.cluster.ClusterEvent.ClusterDomainEvent;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.ClusterSingleton;
import org.apache.pekko.cluster.typed.SingletonActor;
import org.apache.pekko.cluster.typed.Subscribe;
import org.springframework.context.ApplicationEventPublisher;

/** {@link ClusterSupport} on Pekko Cluster, Cluster Sharding and Cluster Singleton. */
public final class PekkoClusterSupport implements ClusterSupport {

    @Override
    public ClusterRuntime start(ActorSystem<?> system, ApplicationEventPublisher publisher) {
        Cluster cluster = Cluster.get(system);
        ActorRef<ClusterDomainEvent> listener =
                system.systemActorOf(PekkoClusterEvents.publisher(publisher), "cluster-event-listener", Props.empty());
        cluster.subscriptions().tell(new Subscribe<>(listener, ClusterDomainEvent.class));
        return new Runtime(system, cluster, ClusterSharding.get(system), ClusterSingleton.get(system));
    }

    @Override
    public <T> ActorRef<T> spawnSingleton(ActorSystem<?> system, Behavior<T> behavior, String name, Props props) {
        return ClusterSingleton.get(system)
                .init(SingletonActor.of(behavior, name).withProps(props));
    }

    private record Runtime(ActorSystem<?> system, Cluster cluster, ClusterSharding sharding, ClusterSingleton singleton)
            implements ClusterRuntime {

        @Override
        public List<Object> engineObjects() {
            return List.of(cluster, sharding, singleton);
        }

        @Override
        public <T> SpringShardedActorBuilder<T> sharded(Class<? extends SpringShardedActor<T>> actorClass) {
            SpringShardedActor<T> actor = ShardedActorRegistry.getByClass(actorClass);
            if (actor == null) {
                throw new IllegalStateException("SpringShardedActor " + actorClass.getName()
                        + " not found in registry. "
                        + "Ensure the actor is annotated with @Component and implements SpringShardedActor.");
            }
            return new PekkoShardedActorBuilder<>(actor.entityType(), sharding, system.scheduler());
        }

        @Override
        public void initSharding() {
            for (SpringShardedActor<?> actor : ShardedActorRegistry.getAll()) {
                PekkoSharding.init(sharding, actor);
            }
        }
    }
}
