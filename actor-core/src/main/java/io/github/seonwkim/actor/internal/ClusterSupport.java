package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import javax.annotation.Nullable;
import org.apache.pekko.actor.setup.ActorSystemSetup;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Props;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Cluster features, provided by the {@code actor-cluster} module through {@link ServiceLoader}.
 * Without that module on the classpath the actor system runs in local mode only.
 */
public interface ClusterSupport {

    /** {@code config} plus the cluster's defaults (serializers and bindings) where the application hasn't set them. */
    Map<String, Object> withDefaults(Map<String, Object> config);

    /**
     * {@code setup} plus the cluster's engine setups. {@code beans} gives access to application beans
     * such as the {@code ObjectMapper}; null outside a Spring context.
     */
    ActorSystemSetup withSetup(ActorSystemSetup setup, @Nullable BeanFactory beans);

    /** Starts the cluster features of a system whose provider is {@code cluster}. */
    ClusterRuntime start(ActorSystem<?> system, ApplicationEventPublisher publisher);

    /** Starts {@code behavior} as a cluster singleton of {@code system} and returns its proxy. */
    <T> ActorRef<T> spawnSingleton(ActorSystem<?> system, Behavior<T> behavior, String name, Props props);

    /** The cluster support on the classpath, if any. */
    static Optional<ClusterSupport> find() {
        return ServiceLoader.load(ClusterSupport.class, ClusterSupport.class.getClassLoader())
                .findFirst();
    }

    /** The cluster support on the classpath, or a failure that names the missing module. */
    static ClusterSupport require() {
        return find().orElseThrow(
                        () -> new IllegalStateException(
                                "Cluster mode needs the actor-cluster module: depend on io.github.seonwkim:actor-spring-boot-starter-cluster"));
    }

    /** The cluster features of one running actor system. */
    interface ClusterRuntime {

        /** The engine objects {@code SpringActorSystem.unwrap} may return (cluster, sharding, ...). */
        List<Object> engineObjects();

        /** A builder for handles to entities of a registered sharded actor class. */
        <T> SpringShardedActorBuilder<T> sharded(Class<? extends SpringShardedActor<T>> actorClass);

        /** Registers every sharded actor bean with sharding. Called once all beans exist. */
        void initSharding();
    }
}
