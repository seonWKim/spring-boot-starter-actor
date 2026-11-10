package io.github.seonwkim.actor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.cluster.ClusterEvent;
import io.github.seonwkim.actor.cluster.MemberStatus;
import io.github.seonwkim.actor.internal.PekkoActorSystem;
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
 * #163 and #201. So the test closes whichever node is not the leader.
 *
 * <p>Both nodes bind port 0, so no other process can take a port between picking and binding (#224).
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
        node1 = startNode("spring.actor.cluster.join-self=true");
        node2 = startNode(
                "spring.actor.pekko.cluster.seed-nodes[0]=" + context(node1).nodeAddress());

        Cluster clusterA = context(node1).unwrap(Cluster.class);
        Cluster clusterB = context(node2).unwrap(Cluster.class);
        // Both nodes must see both members Up: node 1 can see node 2 Up before node 2 has caught up, and
        // a node that leaves before it knows the membership never tells node 1, which then sees it
        // unreachable instead of Leaving (#201).
        await().atMost(20, SECONDS)
                .pollInterval(200, MILLISECONDS)
                .until(() -> upMembers(clusterA) == 2 && upMembers(clusterB) == 2);
        // The leader (the lower address) stays; the other node leaves (see the class comment).
        if (!clusterA.selfMember().address().equals(clusterA.state().getLeader())) {
            ConfigurableApplicationContext leader = node2;
            node2 = node1;
            node1 = leader;
        }
        Cluster cluster1 = context(node1).unwrap(Cluster.class);
        Cluster cluster2 = context(node2).unwrap(Cluster.class);
        assertThat(cluster1.state().getLeader())
                .as("leader")
                .isEqualTo(cluster1.selfMember().address());

        SpringActorSystem system2 = context(node2);
        String address2 = cluster2.selfMember().address().toString();
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

    private static int upMembers(Cluster cluster) {
        return cluster.state()
                .members()
                .filter(m -> m.status() == org.apache.pekko.cluster.MemberStatus.up())
                .size();
    }

    private static SpringActorSystem context(ConfigurableApplicationContext ctx) {
        return ctx.getBean(SpringActorSystem.class);
    }

    private static ConfigurableApplicationContext startNode(String join) {
        return new SpringApplicationBuilder(ClusterApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.actor.pekko.name=graceful-shutdown",
                        "spring.actor.pekko.actor.provider=cluster",
                        "spring.actor.pekko.remote.artery.canonical.hostname=127.0.0.1",
                        "spring.actor.pekko.remote.artery.canonical.port=0",
                        join,
                        "spring.actor.pekko.cluster.downing-provider-class=org.apache.pekko.cluster.sbr.SplitBrainResolverProvider",
                        "spring.actor.pekko.actor.allow-java-serialization=off")
                .run();
    }
}
