package io.github.seonwkim.actor.autoconfigure.cluster;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import io.github.seonwkim.actor.internal.shard.ShardedActorRegistry;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link SpringShardedActor} beans with cluster sharding. Applies only when
 * {@code actor-cluster} is on the classpath.
 */
@Configuration
@ConditionalOnClass(name = "io.github.seonwkim.actor.internal.cluster.PekkoClusterSupport")
public class ClusterConfiguration {

    /** Collects sharded actor beans as Spring creates them. */
    @Bean
    public static BeanPostProcessor shardedActorRegistrationBeanPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof SpringShardedActor<?> actor) {
                    ShardedActorRegistry.register(actor);
                }
                return bean;
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
}
