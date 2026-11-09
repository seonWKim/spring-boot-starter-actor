package io.github.seonwkim.actor.autoconfigure.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.cluster.MemberStatus;
import io.github.seonwkim.actor.internal.cluster.ClusterStateView;
import java.time.Duration;
import java.util.Map;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Leave;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** The actorCluster component of /actuator/health follows this node's membership. */
class ActorClusterHealthTest {

    @SpringBootApplication
    @EnableActorSupport
    static class TestApp {}

    private static ConfigurableApplicationContext start(String... properties) {
        return new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.actor.provider=cluster",
                        "spring.actor.remote.port=0",
                        "spring.actor.pekko.management.enabled=false",
                        "spring.actor.shutdown.exit-process=false")
                .properties(properties)
                .run();
    }

    private static Health clusterHealth(ConfigurableApplicationContext context) {
        return (Health) context.getBean(HealthEndpoint.class).healthForPath("actorCluster");
    }

    @Test
    void aNodeThatHasNotJoinedIsOutOfService() {
        try (ConfigurableApplicationContext context = start()) {
            Health health = clusterHealth(context);
            assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
            assertThat(health.getDetails()).containsEntry("selfStatus", "NOT_JOINED");
        }
    }

    @Test
    void anUpMemberIsUpAndItsDetailsHoldCountsButNoAddresses() {
        try (ConfigurableApplicationContext context = start("spring.actor.cluster.join-self=true")) {
            await().atMost(Duration.ofSeconds(20))
                    .until(() -> clusterHealth(context).getStatus().equals(Status.UP));

            Map<String, Object> details = clusterHealth(context).getDetails();
            assertThat(details)
                    .containsEntry("selfStatus", "UP")
                    .containsEntry("selfUnreachable", false)
                    .containsEntry("members", 1)
                    .containsEntry("unreachable", 0);
            String address = context.getBean(SpringActorSystem.class).nodeAddress();
            assertThat(details.values()).noneMatch(value -> value.toString().contains(address));
        }
    }

    @Test
    void aNodeThatLeftIsDown() {
        try (ConfigurableApplicationContext context = start("spring.actor.cluster.join-self=true")) {
            ActorClusterHealthIndicator indicator = context.getBean(ActorClusterHealthIndicator.class);
            await().atMost(Duration.ofSeconds(20))
                    .until(() -> indicator.health().getStatus().equals(Status.UP));
            Cluster cluster = context.getBean(SpringActorSystem.class).unwrap(Cluster.class);

            cluster.manager().tell(Leave.create(cluster.selfMember().address()));

            await().atMost(Duration.ofSeconds(20))
                    .until(() -> indicator.health().getStatus().equals(Status.DOWN));
        }
    }

    @Test
    void localModeHasNoClusterIndicator() {
        try (ConfigurableApplicationContext context = start("spring.actor.provider=local")) {
            assertThat(context.getBeanNamesForType(ActorClusterHealthIndicator.class))
                    .isEmpty();
            assertThat(context.getBeanNamesForType(ActorSystemHealthIndicator.class))
                    .hasSize(1);
        }
    }

    private static ClusterStateView self(MemberStatus status) {
        return new ClusterStateView(status, false, false, 3, 0, false);
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = {"JOINING", "WEAKLY_UP", "LEAVING", "EXITING", "PREPARING_FOR_SHUTDOWN", "READY_FOR_SHUTDOWN"})
    void aMemberOnItsWayInOrOutIsOutOfService(MemberStatus status) {
        assertThat(ActorClusterHealthIndicator.statusOf(self(status))).isEqualTo(Status.OUT_OF_SERVICE);
    }

    @ParameterizedTest
    @EnumSource(
            value = MemberStatus.class,
            names = {"DOWN", "REMOVED"})
    void aDownedOrRemovedMemberIsDown(MemberStatus status) {
        assertThat(ActorClusterHealthIndicator.statusOf(self(status))).isEqualTo(Status.DOWN);
    }

    @Test
    void anUpMemberThatOthersCannotReachIsDown() {
        assertThat(ActorClusterHealthIndicator.statusOf(
                        new ClusterStateView(MemberStatus.UP, true, false, 3, 1, false)))
                .isEqualTo(Status.DOWN);
    }

    @Test
    void anUnreachablePeerDoesNotMakeThisNodeUnhealthy() {
        assertThat(ActorClusterHealthIndicator.statusOf(
                        new ClusterStateView(MemberStatus.UP, false, false, 3, 1, false)))
                .isEqualTo(Status.UP);
    }
}
