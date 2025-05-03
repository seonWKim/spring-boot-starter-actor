package org.github.seonwkim.core;

import java.util.List;

import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.github.seonwkim.core.behaviors.RootBehavior;
import org.github.seonwkim.core.behaviors.ShardBehavior;
import org.github.seonwkim.core.behaviors.impl.DefaultRootBehavior;
import org.github.seonwkim.core.utils.PekkoConfigurationUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

@Configuration
public class PekkoSystemConfiguration {

    @Bean
    public ActorSystem<?> actorSystem(DependencyContainer container, PekkoConfiguration pekkoConfig) {
        final Config config =
                ConfigFactory.parseString(PekkoConfigurationUtils.toPropertiesString(pekkoConfig));
        final String name =
                pekkoConfig.isClusterMode() ? pekkoConfig.getCluster().getName() : pekkoConfig.getName();
        return ActorSystem.create(container.getRootBehavior().create(container), name, config);
    }

    @Bean
    @ConditionalOnMissingBean(RootBehavior.class)
    public RootBehavior nonClusterRootBehavior() {
        return new DefaultRootBehavior();
    }

    @Bean
    public PekkoCluster pekkoCluster(ActorSystem<?> actorSystem, PekkoConfiguration pekkoConfig) {
        if (pekkoConfig.isClusterMode()) {
            return new PekkoCluster(actorSystem);
        }

        return new PekkoCluster(null);
    }

    @Bean
    public PekkoClusterSharding pekkoClusterSharding(ActorSystem<?> actorSystem,
                                                     PekkoConfiguration configuration,
                                                     List<ShardBehavior<?>> shardBehaviors
    ) {
        if (configuration.isClusterMode()) {
            final PekkoClusterSharding clusterSharding =
                    new PekkoClusterSharding(ClusterSharding.get(actorSystem));
            for (ShardBehavior<?> shardBehavior : shardBehaviors) {
                clusterSharding.initEntity(shardBehavior);
            }

            return clusterSharding;
        }

        return new PekkoClusterSharding(null);
    }
}
