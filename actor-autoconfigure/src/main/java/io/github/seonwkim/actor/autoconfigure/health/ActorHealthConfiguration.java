package io.github.seonwkim.actor.autoconfigure.health;

import io.github.seonwkim.actor.SpringActorSystem;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Health indicators for the actor system and, in cluster mode, this node's cluster membership.
 * Applies when Spring Boot Actuator is on the classpath; {@code management.health.actor.enabled=false}
 * turns both off.
 *
 * <p>Each class carries all of its own conditions, and none needs an Actuator class to evaluate: a
 * component scan can find the nested class on its own, and Actuator may be absent.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = ActorHealthConfiguration.ACTUATOR)
@ConditionalOnProperty(name = ActorHealthConfiguration.ENABLED, matchIfMissing = true)
public class ActorHealthConfiguration {

    static final String ACTUATOR = "org.springframework.boot.actuate.health.AbstractHealthIndicator";
    static final String ENABLED = "management.health.actor.enabled";

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
    }
}
