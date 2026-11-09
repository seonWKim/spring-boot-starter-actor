package io.github.seonwkim.actor.autoconfigure.metrics;

import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.cluster.ClusterEvent;
import io.github.seonwkim.actor.internal.cluster.ClusterStateView;
import io.github.seonwkim.actor.metrics.cluster.ClusterMeters;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Cluster membership meters ({@code actor-metrics}) in cluster mode, when Micrometer is on the classpath.
 * Spring Boot binds the {@link ClusterMeters} bean to every {@code MeterRegistry}.
 * {@code spring.actor.metrics.enabled=false} turns them off.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(
        name = {
            "io.micrometer.core.instrument.MeterRegistry",
            "io.github.seonwkim.actor.metrics.cluster.ClusterMeters",
            "io.github.seonwkim.actor.internal.cluster.ClusterStateView"
        })
@ConditionalOnProperty(name = "spring.actor.metrics.enabled", matchIfMissing = true)
@ConditionalOnProperty(name = "spring.actor.provider", havingValue = "cluster")
public class ClusterMetricsConfiguration {

    @Bean
    public ClusterMeters actorClusterMeters(SpringActorSystem actorSystem) {
        return new ClusterMeters(
                () -> ClusterStateView.of(actorSystem).members(),
                () -> ClusterStateView.of(actorSystem).unreachable(),
                () -> ClusterStateView.of(actorSystem).leader());
    }

    @Bean
    public ClusterEventCounter actorClusterEventCounter(ClusterMeters meters) {
        return new ClusterEventCounter(meters);
    }

    /** Counts the cluster events the starter publishes as Spring events. */
    public static final class ClusterEventCounter {
        private final ClusterMeters meters;

        ClusterEventCounter(ClusterMeters meters) {
            this.meters = meters;
        }

        @EventListener
        public void onClusterEvent(ClusterEvent event) {
            meters.record(event);
        }
    }
}
