package io.github.seonwkim.actor.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.EnableActorSupport;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.spi.Peer;
import io.github.seonwkim.actor.spi.PeerRegistry;
import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.typed.Cluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Nodes with {@code spring.actor.cluster.join=discovery} form a cluster through a
 * {@link PeerRegistry} store. A store module's test extends this class and gives the properties
 * that select its store; each node is a Spring context in this JVM on free ports.
 */
public abstract class DiscoveryClusterContract {

    /** The application of every node. Not a {@code @Configuration}, so component scans skip it. */
    @EnableAutoConfiguration
    @EnableActorSupport
    public static class Node {}

    /** Properties that select the store, added to every node's. */
    protected abstract List<String> storeProperties();

    private final String service = "discovery-" + UUID.randomUUID().toString().substring(0, 8);
    private final List<ConfigurableApplicationContext> nodes = new ArrayList<>();

    @AfterEach
    void stopNodes() {
        nodes.forEach(ConfigurableApplicationContext::close);
    }

    /** Starts a node and returns its context. */
    protected ConfigurableApplicationContext startNode() {
        List<String> properties = new ArrayList<>(
                List.of(
                        "spring.actor.name=" + service,
                        "spring.actor.provider=cluster",
                        "spring.actor.remote.hostname=127.0.0.1",
                        "spring.actor.remote.port=" + freePort(),
                        "spring.actor.cluster.join=discovery",
                        "spring.actor.cluster.discovery.heartbeat-interval=500ms",
                        "spring.actor.cluster.discovery.ttl=10s",
                        "spring.actor.shutdown.exit-process=false",
                        "spring.actor.pekko.management.http.hostname=127.0.0.1",
                        "spring.actor.pekko.management.http.port=" + freePort(),
                        "spring.actor.pekko.management.cluster.bootstrap.contact-point-discovery.required-contact-point-nr=3",
                        "spring.actor.pekko.management.cluster.bootstrap.contact-point-discovery.interval=300ms",
                        "spring.actor.pekko.management.cluster.bootstrap.contact-point-discovery.stable-margin=1s",
                        "spring.actor.pekko.management.cluster.bootstrap.contact-point.probe-interval=300ms",
                        // All nodes share one host here: keep every port, not only this node's own one.
                        "spring.actor.pekko.management.cluster.bootstrap.contact-point-discovery.filter-on-fallback-port=false"));
        properties.addAll(storeProperties());
        ConfigurableApplicationContext node = new SpringApplicationBuilder(Node.class)
                .web(WebApplicationType.NONE)
                .properties(properties.toArray(String[]::new))
                .run();
        nodes.add(node);
        return node;
    }

    private static long upMembers(ConfigurableApplicationContext node) {
        Cluster cluster = node.getBean(SpringActorSystem.class).unwrap(Cluster.class);
        return java.util.stream.StreamSupport.stream(
                        cluster.state().getMembers().spliterator(), false)
                .filter(member -> member.status() == MemberStatus.up())
                .count();
    }

    private List<String> registeredIds(ConfigurableApplicationContext node) {
        return node.getBean(PeerRegistry.class).peers(service, Duration.ofMinutes(5)).stream()
                .map(Peer::id)
                .toList();
    }

    @Test
    void threeNodesFormOneCluster() {
        ConfigurableApplicationContext first = startNode();
        ConfigurableApplicationContext second = startNode();
        ConfigurableApplicationContext third = startNode();

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            assertThat(upMembers(first)).isEqualTo(3);
            assertThat(upMembers(second)).isEqualTo(3);
            assertThat(upMembers(third)).isEqualTo(3);
        });
    }

    @Test
    void aNodeThatLeavesGracefullyRemovesItsEntry() {
        ConfigurableApplicationContext staying = startNode();
        ConfigurableApplicationContext leaving = startNode();
        String stayingId = staying.getBean(SpringActorSystem.class).nodeAddress();
        String leavingId = leaving.getBean(SpringActorSystem.class).nodeAddress();
        assertThat(registeredIds(staying)).containsExactlyInAnyOrder(stayingId, leavingId);

        leaving.close();

        // The ttl (minutes here) has not passed: the entry is gone because the node deleted it.
        assertThat(registeredIds(staying)).containsExactly(stayingId);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
