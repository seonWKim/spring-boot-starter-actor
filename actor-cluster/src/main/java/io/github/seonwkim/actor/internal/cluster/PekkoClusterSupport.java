package io.github.seonwkim.actor.internal.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.seonwkim.actor.internal.ClusterSupport;
import io.github.seonwkim.actor.internal.shard.PekkoShardedActorBuilder;
import io.github.seonwkim.actor.internal.shard.PekkoSharding;
import io.github.seonwkim.actor.internal.shard.ShardedActorRegistry;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorBuilder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.apache.pekko.actor.setup.ActorSystemSetup;
import org.apache.pekko.actor.setup.Setup;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.cluster.ClusterEvent.ClusterDomainEvent;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.ClusterSingleton;
import org.apache.pekko.cluster.typed.Join;
import org.apache.pekko.cluster.typed.SingletonActor;
import org.apache.pekko.cluster.typed.Subscribe;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationEventPublisher;

/** {@link ClusterSupport} on Pekko Cluster, Cluster Sharding and Cluster Singleton. */
public final class PekkoClusterSupport implements ClusterSupport {

    /** Set by spring.actor.cluster.join-self (ActorProperties): form a one-node cluster. */
    private static final String JOIN_SELF_KEY = "spring-actor.cluster.join-self";

    @Override
    public Map<String, Object> withDefaults(Map<String, Object> config) {
        return PekkoJackson.withSerializers(config);
    }

    @Override
    public ActorSystemSetup withSetup(ActorSystemSetup setup, @Nullable BeanFactory beans) {
        ObjectMapper applicationMapper =
                beans == null ? null : beans.getBeanProvider(ObjectMapper.class).getIfUnique();
        ShardedActorRegistry sharded = beans == null
                ? null
                : beans.getBeanProvider(ShardedActorRegistry.class).getIfAvailable();
        return setup.withSetup(PekkoJackson.setup(applicationMapper))
                .withSetup(new ShardedActorsSetup(sharded == null ? new ShardedActorRegistry() : sharded));
    }

    /** Carries the Spring context's sharded actors to the cluster runtime of its actor system. */
    private static final class ShardedActorsSetup extends Setup {
        private final ShardedActorRegistry registry;

        ShardedActorsSetup(ShardedActorRegistry registry) {
            this.registry = registry;
        }
    }

    @Override
    public ClusterRuntime start(ActorSystem<?> system, ApplicationEventPublisher publisher) {
        Cluster cluster = Cluster.get(system);
        if (system.settings().config().hasPath(JOIN_SELF_KEY)
                && system.settings().config().getBoolean(JOIN_SELF_KEY)) {
            cluster.manager().tell(Join.create(cluster.selfMember().address()));
        }
        ShardedActorRegistry sharded = system.settings()
                .setup()
                .get(ShardedActorsSetup.class)
                .map(found -> found.registry)
                .orElseGet(ShardedActorRegistry::new);
        return new Runtime(
                system,
                cluster,
                ClusterSharding.get(system),
                ClusterSingleton.get(system),
                sharded,
                publisher,
                new AtomicBoolean());
    }

    @Override
    public <T> ActorRef<T> spawnSingleton(ActorSystem<?> system, Behavior<T> behavior, String name, Props props) {
        return ClusterSingleton.get(system)
                .init(SingletonActor.of(behavior, name).withProps(props));
    }

    private record Runtime(
            ActorSystem<?> system,
            Cluster cluster,
            ClusterSharding sharding,
            ClusterSingleton singleton,
            ShardedActorRegistry shardedActors,
            ApplicationEventPublisher publisher,
            AtomicBoolean publishing)
            implements ClusterRuntime {

        @Override
        public List<Object> engineObjects() {
            return List.of(cluster, sharding, singleton);
        }

        @Override
        public <T> SpringShardedActorBuilder<T> sharded(Class<? extends SpringShardedActor<T>> actorClass) {
            SpringShardedActor<T> actor = shardedActors.getByClass(actorClass);
            if (actor == null) {
                throw new IllegalStateException("SpringShardedActor " + actorClass.getName()
                        + " not found in registry. "
                        + "Ensure the actor is annotated with @Component and implements SpringShardedActor.");
            }
            return new PekkoShardedActorBuilder<>(actor.entityType(), sharding, system);
        }

        @Override
        public void publishEvents() {
            if (publishing.compareAndSet(false, true)) {
                ActorRef<ClusterDomainEvent> listener = system.systemActorOf(
                        PekkoClusterEvents.publisher(publisher), "cluster-event-listener", Props.empty());
                // A typed subscription starts with the current state as events (InitialStateAsEvents).
                cluster.subscriptions().tell(new Subscribe<>(listener, ClusterDomainEvent.class));
            }
        }

        @Override
        public void initSharding() {
            for (SpringShardedActor<?> actor : shardedActors.getAll()) {
                PekkoSharding.init(system, sharding, actor);
            }
        }
    }
}
