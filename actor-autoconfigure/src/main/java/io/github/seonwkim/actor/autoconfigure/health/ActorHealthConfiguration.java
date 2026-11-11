package io.github.seonwkim.actor.autoconfigure.health;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.autoconfigure.ActorConfiguration;
import io.github.seonwkim.actor.autoconfigure.ActorProperties;
import io.github.seonwkim.actor.autoconfigure.ConditionalOnActorEnabled;
import io.github.seonwkim.actor.autoconfigure.cluster.ClusterConfiguration;
import io.github.seonwkim.actor.autoconfigure.cluster.StoreConfiguration;
import io.github.seonwkim.actor.spi.PeerRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Health indicators for the actor system and, in cluster mode, this node's cluster membership and
 * warm-up (with the {@code warmup} health group). Applies when Spring Boot Actuator is on the classpath; {@code management.health.actor.enabled=false}
 * turns both off.
 *
 * <p>Each class carries all of its own conditions, and none needs an Actuator class to evaluate: a
 * component scan can find the nested class on its own, and Actuator may be absent.
 */
@AutoConfiguration(after = {ActorConfiguration.class, ClusterConfiguration.class, StoreConfiguration.class})
@ConditionalOnActorEnabled
@ConditionalOnClass(name = ActorHealthConfiguration.ACTUATOR)
@ConditionalOnProperty(name = ActorHealthConfiguration.ENABLED, matchIfMissing = true)
public class ActorHealthConfiguration {

    static final String ACTUATOR = "org.springframework.boot.actuate.health.AbstractHealthIndicator";
    static final String ENABLED = "management.health.actor.enabled";
    static final String WARMUP_MIN_MEMBERS = "management.health.actor.warmup.min-members";

    /** Reported as the {@code actorSystem} component. */
    @Bean
    public ActorSystemHealthIndicator actorSystemHealthIndicator(SpringActorSystem actorSystem) {
        return new ActorSystemHealthIndicator(actorSystem);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {ACTUATOR, "io.github.seonwkim.actor.internal.cluster.ClusterStateView"})
    @ConditionalOnProperty(name = ENABLED, matchIfMissing = true)
    @ConditionalOnProperty(name = "spring.actor.provider", havingValue = "cluster")
    static class Cluster {

        /** Reported as the {@code actorCluster} component. */
        @Bean
        public ActorClusterHealthIndicator actorClusterHealthIndicator(SpringActorSystem actorSystem) {
            return new ActorClusterHealthIndicator(actorSystem);
        }

        /** Reported as the {@code actorWarmup} component and the {@code warmup} group. */
        @Bean
        public ActorWarmupHealthIndicator actorWarmupHealthIndicator(
                SpringActorSystem actorSystem,
                Environment environment,
                ObjectProvider<PeerRegistry> peerRegistry,
                ActorProperties properties) {
            return new ActorWarmupHealthIndicator(
                    actorSystem,
                    environment.getProperty(WARMUP_MIN_MEMBERS, Integer.class, 1),
                    peerRegistry.getIfUnique(),
                    properties.getCluster().getDiscovery().getTtl());
        }

        @Bean
        static WarmupHealthGroup warmupHealthGroup() {
            return new WarmupHealthGroup();
        }
    }
}
