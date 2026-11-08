package io.github.seonwkim.core.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.core.EnableActorSupport;
import io.github.seonwkim.core.SpringActorSystem;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.typed.Cluster;
import org.apache.pekko.cluster.typed.Leave;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Checks the cluster health indicator: the status it reports for each member status, on a live
 * single-node cluster, and that its details hold no addresses.
 */
class ClusterHealthIndicatorTest {

    @EnableActorSupport
    static class ClusterApp {}

    @Test
    void upMemberIsUp() {
        assertThat(ClusterHealthIndicator.statusOf(MemberStatus.up(), false)).isEqualTo(Status.UP);
    }

    @Test
    void leavingOrExitingMemberIsOutOfService() {
        assertThat(ClusterHealthIndicator.statusOf(MemberStatus.leaving(), false))
                .isEqualTo(Status.OUT_OF_SERVICE);
        assertThat(ClusterHealthIndicator.statusOf(MemberStatus.exiting(), false))
                .isEqualTo(Status.OUT_OF_SERVICE);
    }

    @Test
    void joiningMemberIsOutOfService() {
        assertThat(ClusterHealthIndicator.statusOf(MemberStatus.joining(), false))
                .isEqualTo(Status.OUT_OF_SERVICE);
    }

    @Test
    void unreachableOrRemovedMemberIsDown() {
        assertThat(ClusterHealthIndicator.statusOf(MemberStatus.up(), true)).isEqualTo(Status.DOWN);
        assertThat(ClusterHealthIndicator.statusOf(MemberStatus.removed(), false)).isEqualTo(Status.DOWN);
        assertThat(ClusterHealthIndicator.statusOf(MemberStatus.down(), false)).isEqualTo(Status.DOWN);
    }

    @Test
    void liveClusterIsUpWithCountsOnlyThenDownOnceRemoved() throws IOException {
        int port = freePort();
        new ApplicationContextRunner()
                .withUserConfiguration(ClusterApp.class)
                .withConfiguration(AutoConfigurations.of(
                        HealthContributorAutoConfiguration.class,
                        HealthEndpointAutoConfiguration.class,
                        ActorHealthAutoConfiguration.class))
                .withPropertyValues(
                        "spring.actor.pekko.name=health-cluster",
                        "spring.actor.pekko.actor.provider=cluster",
                        "spring.actor.pekko.remote.artery.canonical.hostname=127.0.0.1",
                        "spring.actor.pekko.remote.artery.canonical.port=" + port,
                        "spring.actor.pekko.cluster.seed-nodes[0]=pekko://health-cluster@127.0.0.1:" + port,
                        "spring.actor.pekko.coordinated.shutdown.run-by-jvm-shutdown-hook=off",
                        "management.endpoint.health.show-details=always")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ClusterHealthIndicator indicator = context.getBean(ClusterHealthIndicator.class);

                    await().atMost(Duration.ofSeconds(20))
                            .untilAsserted(() -> assertThat(indicator.health().getStatus()).isEqualTo(Status.UP));

                    Health health = indicator.health();
                    assertThat(health.getDetails()).containsOnlyKeys("members", "unreachable", "status");
                    assertThat(health.getDetails())
                            .containsEntry("members", 1)
                            .containsEntry("unreachable", 0)
                            .containsEntry("status", "Up");
                    assertThat(health.getDetails().values().toString())
                            .doesNotContain("127.0.0.1")
                            .doesNotContain(String.valueOf(port))
                            .doesNotContain("pekko://");

                    CompositeHealth endpoint = (CompositeHealth) context.getBean(HealthEndpoint.class).health();
                    assertThat(endpoint.getComponents()).containsKey(ActorHealthAutoConfiguration.ACTOR_CLUSTER);

                    Cluster cluster = context.getBean(SpringActorSystem.class).getCluster();
                    cluster.manager().tell(Leave.create(cluster.selfMember().address()));

                    await().atMost(Duration.ofSeconds(20))
                            .untilAsserted(() -> assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN));
                });
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
