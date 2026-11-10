package io.github.seonwkim.actor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.cluster.ClusterEvent;
import io.github.seonwkim.actor.cluster.MemberStatus;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.pekko.cluster.typed.Cluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

/**
 * Two-node cluster: closing one node's Spring context makes it leave the cluster gracefully, so the
 * other node sees it Removed (after Exiting), never unreachable.
 *
 * <p>The node that stays is the leader (the lowest address), so the leaving node is a follower: the
 * leader moves it to Exiting and removes it once it confirms, which Pekko guarantees. When the leader
 * itself leaves, it moves itself to Exiting and tells the others only best effort before it shuts
 * down; if that last gossip is lost, the others remove it only after failure detection and the
 * split brain resolver's {@code stable-after} (20 s), as Down. Random ports made node 2 the leader in
 * about half of the CI runs (Linux picks ephemeral ports at random), which is the suspected cause of
 * #163 and #201.
 */
class GracefulShutdownClusterTest {

    /** Not annotated with {@code @Configuration}, so other tests' component scans skip it. */
    @EnableActorSupport
    static class ClusterApp {
        @Bean
        public MembershipEvents membershipEvents() {
            return new MembershipEvents();
        }
    }

    public static class MembershipEvents {
        final List<ClusterEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void on(ClusterEvent event) {
            events.add(event);
        }
    }

    private ConfigurableApplicationContext node1;
    private ConfigurableApplicationContext node2;

    @AfterEach
    void tearDown() {
        if (node2 != null && node2.isActive()) {
            node2.close();
        }
        if (node1 != null && node1.isActive()) {
            node1.close();
        }
    }

    @Test
    void closingANodeContextMakesItLeaveGracefully() {
        // Node 1 gets the lower port, so it is and stays the leader (see the class comment).
        int portA = freePort();
        int portB = freePort();
        int port1 = Math.min(portA, portB);
        int port2 = Math.max(portA, portB);
        String seeds = String.format(
                "pekko://graceful-shutdown@127.0.0.1:%d,pekko://graceful-shutdown@127.0.0.1:%d", port1, port2);
        node1 = startNode(port1, seeds);
        node2 = startNode(port2, seeds);

        Cluster cluster1 = context(node1).unwrap(Cluster.class);
        assertThat(cluster1).isNotNull();
        await().atMost(20, SECONDS)
                .pollInterval(200, MILLISECONDS)
                .until(() -> cluster1.state()
                                .members()
                                .filter(m -> m.status() == org.apache.pekko.cluster.MemberStatus.up())
                                .size()
                        == 2);
        assertThat(cluster1.state().getLeader())
                .as("leader")
                .isEqualTo(cluster1.selfMember().address());

        SpringActorSystem system2 = context(node2);
        String address2 = system2.unwrap(Cluster.class).selfMember().address().toString();
        MembershipEvents events1 = node1.getBean(MembershipEvents.class);

        node2.close();

        assertThat(PekkoActorSystem.raw(system2)
                        .getWhenTerminated()
                        .toCompletableFuture()
                        .isDone())
                .isTrue();
        // untilAsserted, so a timeout lists the events node 1 saw and its cluster state (#163, #201).
        await().atMost(20, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() -> assertThat(events1.events)
                .as("events node 1 saw; node 1's cluster state: %s", cluster1.state())
                .anyMatch(e -> e instanceof ClusterEvent.MemberRemoved
                        && ((ClusterEvent.MemberRemoved) e).member().address().equals(address2)));

        ClusterEvent.MemberRemoved removed = events1.events.stream()
                .filter(e -> e instanceof ClusterEvent.MemberRemoved)
                .map(e -> (ClusterEvent.MemberRemoved) e)
                .filter(e -> e.member().address().equals(address2))
                .findFirst()
                .orElseThrow();
        assertThat(removed.member().status()).isEqualTo(MemberStatus.REMOVED);
        // Graceful leave goes Leaving -> Exiting -> Removed; a crashed node would be Down first.
        assertThat(removed.previousStatus()).isEqualTo(MemberStatus.EXITING);
        assertThat(events1.events)
                .noneMatch(e -> e instanceof ClusterEvent.UnreachableMember
                        && ((ClusterEvent.UnreachableMember) e)
                                .member()
                                .address()
                                .equals(address2));
        assertThat(cluster1.state().getUnreachable()).isEmpty();
    }

    private static SpringActorSystem context(ConfigurableApplicationContext ctx) {
        return ctx.getBean(SpringActorSystem.class);
    }

    private static ConfigurableApplicationContext startNode(int arteryPort, String seeds) {
        return new SpringApplicationBuilder(ClusterApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.actor.pekko.name=graceful-shutdown",
                        "spring.actor.pekko.actor.provider=cluster",
                        "spring.actor.pekko.remote.artery.canonical.hostname=127.0.0.1",
                        "spring.actor.pekko.remote.artery.canonical.port=" + arteryPort,
                        "spring.actor.pekko.cluster.seed-nodes=" + seeds,
                        "spring.actor.pekko.cluster.downing-provider-class=org.apache.pekko.cluster.sbr.SplitBrainResolverProvider",
                        "spring.actor.pekko.actor.allow-java-serialization=off")
                .run();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
