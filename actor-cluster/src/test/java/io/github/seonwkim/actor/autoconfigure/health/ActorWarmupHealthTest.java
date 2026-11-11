package io.github.seonwkim.actor.autoconfigure.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.cluster.MemberStatus;
import io.github.seonwkim.actor.internal.cluster.ClusterStateView;
import io.github.seonwkim.actor.spi.Peer;
import io.github.seonwkim.actor.spi.PeerRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * The warmup health group: UP only once this node is an Up member, the cluster has the configured
 * number of Up members and the discovery store answers, whatever the traffic readiness says.
 */
class ActorWarmupHealthTest {

    @SpringBootApplication
    @EnableActorSupport
    static class TestApp {}

    /** A discovery store that can be taken down. The node joins itself, so the store only answers reads. */
    @SpringBootApplication
    @EnableActorSupport
    static class WithStore {
        static final AtomicBoolean down = new AtomicBoolean();

        @Bean
        PeerRegistry peerRegistry() {
            return new PeerRegistry() {
                @Override
                public void register(Peer peer) {}

                @Override
                public void deregister(Peer peer) {}

                @Override
                public List<Peer> peers(String service, Duration ttl) {
                    if (down.get()) {
                        throw new IllegalStateException("store down");
                    }
                    return List.of();
                }
            };
        }
    }

    private static ConfigurableApplicationContext start(Class<?> app, String... properties) {
        return new SpringApplicationBuilder(app)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.actor.provider=cluster",
                        "spring.actor.remote.port=0",
                        "spring.actor.pekko.management.enabled=false",
                        "spring.actor.shutdown.exit-process=false",
                        "management.endpoint.health.probes.enabled=true",
                        "management.endpoint.health.show-details=always")
                .properties(properties)
                .run();
    }

    private static Status group(ConfigurableApplicationContext context, String name) {
        return context.getBean(HealthEndpoint.class).healthForPath(name).getStatus();
    }

    private static Health warmup(ConfigurableApplicationContext context) {
        return (Health) context.getBean(HealthEndpoint.class).healthForPath("warmup", "actorWarmup");
    }

    @Test
    void warmupIsOutOfServiceUntilTheNodeJoinsAndThenUpWhileReadinessRefusesTraffic() {
        try (ConfigurableApplicationContext context = start(TestApp.class)) {
            assertThat(group(context, "warmup")).isEqualTo(Status.OUT_OF_SERVICE);
        }
        try (ConfigurableApplicationContext context = start(TestApp.class, "spring.actor.cluster.join-self=true")) {
            AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);

            await().atMost(Duration.ofSeconds(20)).until(() -> group(context, "warmup") == Status.UP);
            assertThat(group(context, "readiness")).isEqualTo(Status.OUT_OF_SERVICE);
            assertThat(group(context, "liveness")).isEqualTo(Status.UP);
        }
    }

    @Test
    void warmupWaitsForTheConfiguredNumberOfUpMembers() {
        try (ConfigurableApplicationContext context = start(
                TestApp.class, "spring.actor.cluster.join-self=true", "management.health.actor.warmup.min-members=2")) {
            await().atMost(Duration.ofSeconds(20)).until(() -> group(context, "actorCluster") == Status.UP);

            assertThat(group(context, "warmup")).isEqualTo(Status.OUT_OF_SERVICE);
            assertThat(warmup(context).getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
            assertThat(warmup(context).getDetails())
                    .containsEntry("selfUp", true)
                    .containsEntry("upMembers", 1L)
                    .containsEntry("minMembers", 2);
        }
    }

    @Test
    void warmupIsOutOfServiceWhileTheDiscoveryStoreIsUnreachable() {
        WithStore.down.set(true);
        try (ConfigurableApplicationContext context = start(WithStore.class, "spring.actor.cluster.join-self=true")) {
            await().atMost(Duration.ofSeconds(20)).until(() -> group(context, "actorCluster") == Status.UP);
            assertThat(group(context, "warmup")).isEqualTo(Status.OUT_OF_SERVICE);

            WithStore.down.set(false);

            assertThat(group(context, "warmup")).isEqualTo(Status.UP);
        } finally {
            WithStore.down.set(false);
        }
    }

    @Test
    void aWarmupGroupTheApplicationDefinesReplacesTheDefaultOne() {
        try (ConfigurableApplicationContext context =
                start(TestApp.class, "management.endpoint.health.group.warmup.include=actorSystem")) {
            // The node never joins, so the default group would be OUT_OF_SERVICE.
            assertThat(group(context, "warmup")).isEqualTo(Status.UP);
        }
    }

    @Test
    void aNodeOnItsWayOutIsNotWarmEvenWhenTheOthersAreUp() {
        List<ClusterStateView.Node> others = List.of(
                new ClusterStateView.Node("pekko://s@a:1", MemberStatus.UP, Set.of(), true),
                new ClusterStateView.Node("pekko://s@b:1", MemberStatus.UP, Set.of(), true),
                new ClusterStateView.Node("pekko://s@c:1", MemberStatus.LEAVING, Set.of(), true));
        ClusterStateView leaving = new ClusterStateView(MemberStatus.LEAVING, false, false, 3, 0, false, false, others);

        assertThat(ActorWarmupHealthIndicator.statusOf(leaving, 2, true)).isEqualTo(Status.OUT_OF_SERVICE);
    }
}
