package io.github.seonwkim.actor.autoconfigure.cluster;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import io.github.seonwkim.actor.autoconfigure.ConditionalOnActorEnabled;
import io.github.seonwkim.actor.ddata.ReplicatedData;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import io.github.seonwkim.actor.internal.cluster.ddata.PekkoReplicatedData;
import io.github.seonwkim.actor.internal.shard.ShardedActorRegistry;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Registers {@link SpringShardedActor} beans with cluster sharding and provides {@link ReplicatedData}.
 * Applies only when {@code actor-cluster} is on the classpath.
 */
@AutoConfiguration(after = ActorConfiguration.class)
@ConditionalOnActorEnabled
@ConditionalOnClass(name = "io.github.seonwkim.actor.internal.cluster.PekkoClusterSupport")
@ConditionalOnClusterMode
public class ClusterConfiguration {

    /** This context's sharded actor beans; the cluster runtime reads it through the bean factory. */
    @Bean
    public static ShardedActorRegistry shardedActorRegistry() {
        return new ShardedActorRegistry();
    }

    /** Collects sharded actor beans as Spring creates them. */
    @Bean
    public static BeanPostProcessor shardedActorRegistrationBeanPostProcessor(ShardedActorRegistry registry) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof SpringShardedActor<?> actor) {
                    registry.register(actor);
                }
                return bean;
            }
        };
    }

    /**
     * Publishes cluster events once the context has refreshed: {@code @EventListener} methods are
     * registered by then, and the subscription replays what happened during startup.
     */
    @Bean
    public ApplicationListener<ContextRefreshedEvent> clusterEventPublisher(SpringActorSystem actorSystem) {
        return event -> {
            if (actorSystem instanceof PekkoActorSystem pekko) {
                pekko.publishClusterEvents();
            }
        };
    }

    /** Starts sharding for every sharded actor once all beans exist. */
    @Bean
    public SmartInitializingSingleton clusterShardingInitializer(SpringActorSystem actorSystem) {
        return () -> {
            if (actorSystem instanceof PekkoActorSystem pekko) {
                pekko.initializeClusterSharding();
            }
        };
    }

    /** Handles to replicated maps, counters and sets; the replicator starts on first use. */
    @Bean
    @ConditionalOnMissingBean(ReplicatedData.class)
    public ReplicatedData replicatedData(SpringActorSystem actorSystem) {
        return new PekkoReplicatedData(actorSystem);
    }
}
