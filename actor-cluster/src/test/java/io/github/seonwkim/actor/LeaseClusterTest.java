package io.github.seonwkim.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.actor.fixture.TestShardedActor;
import io.github.seonwkim.actor.spi.LeaseStore;
import io.github.seonwkim.actor.test.InMemoryLeaseStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.typed.Cluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Singletons, shards and the split brain resolver take their leases from the application's
 * LeaseStore bean (spring.actor.cluster.lease.store=custom).
 */
class LeaseClusterTest {

    /** A node. Not a {@code @Configuration}, so the component scans of other tests skip it. */
    @EnableAutoConfiguration
    @EnableActorSupport
    static class Node {}

    private final String name = "lease-" + UUID.randomUUID().toString().substring(0, 8);
    private final List<ConfigurableApplicationContext> nodes = new ArrayList<>();

    @AfterEach
    void stopNodes() {
        nodes.forEach(ConfigurableApplicationContext::close);
    }

    private ConfigurableApplicationContext start(LeaseStore store, String... properties) {
        ConfigurableApplicationContext node = new SpringApplicationBuilder(
                        Node.class, ClusterSingletonTest.SingletonTestActor.class, TestShardedActor.class)
                .web(WebApplicationType.NONE)
                .initializers(context -> context.getBeanFactory().registerSingleton("leaseStore", store))
                .properties(
                        "spring.actor.name=" + name,
                        "spring.actor.provider=cluster",
                        "spring.actor.remote.hostname=127.0.0.1",
                        "spring.actor.remote.port=0",
                        "spring.actor.shutdown.exit-process=false",
                        "spring.actor.cluster.lease.store=custom",
                        "spring.actor.cluster.lease.ttl=10s",
                        "spring.actor.cluster.lease.renew-interval=1s",
                        "spring.actor.cluster.lease.expiry-margin=2s",
                        // Retry a refused lease quickly.
                        "spring.actor.pekko.cluster.singleton.lease-retry-interval=500ms",
                        "spring.actor.pekko.cluster.sharding.lease-retry-interval=500ms")
                .properties(properties)
                .run();
        nodes.add(node);
        return node;
    }

    private static SpringActorHandle<ClusterSingletonTest.SingletonTestActor.Command> singleton(
            ConfigurableApplicationContext node) throws Exception {
        return node.getBean(SpringActorSystem.class)
                .actor(ClusterSingletonTest.SingletonTestActor.class)
                .withId("counter")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get();
    }

    private static Optional<Integer> count(
            SpringActorHandle<ClusterSingletonTest.SingletonTestActor.Command> singleton) {
        try {
            return Optional.of(singleton
                    .ask(new ClusterSingletonTest.SingletonTestActor.GetCount())
                    .withTimeout(Duration.ofMillis(500))
                    .execute()
                    .toCompletableFuture()
                    .get()
                    .count);
        } catch (Exception noReply) {
            return Optional.empty();
        }
    }

    private static String owner(ConfigurableApplicationContext node) {
        // Pekko names lease owners <system>@<host>:<port>.
        return node.getBean(SpringActorSystem.class).nodeAddress().replace("pekko://", "");
    }

    @Test
    void aSingletonRunsWhileItHoldsItsLease() throws Exception {
        InMemoryLeaseStore store = new InMemoryLeaseStore();
        ConfigurableApplicationContext node =
                start(store, "spring.actor.cluster.join-self=true", "spring.actor.cluster.singleton.use-lease=true");

        var counter = singleton(node);

        await().atMost(Duration.ofSeconds(20)).until(() -> count(counter).isPresent());
        assertThat(store.held())
                .hasEntrySatisfying(
                        store.held().keySet().stream()
                                .filter(lease -> lease.startsWith(name + "-singleton-") && lease.endsWith(":counter"))
                                .findFirst()
                                .orElseThrow(() -> new AssertionError("no singleton lease in " + store.held())),
                        holder -> assertThat(holder).isEqualTo(owner(node)));
    }

    @Test
    void aSingletonDoesNotStartWithoutItsLease() throws Exception {
        LeaseStore refusing = new LeaseStore() {
            @Override
            public boolean tryAcquire(String lease, String owner, Duration ttl) {
                return false;
            }

            @Override
            public boolean renew(String lease, String owner, Duration ttl) {
                return false;
            }

            @Override
            public void release(String lease, String owner) {}

            @Override
            public Optional<String> holder(String lease) {
                return Optional.empty();
            }
        };
        ConfigurableApplicationContext node =
                start(refusing, "spring.actor.cluster.join-self=true", "spring.actor.cluster.singleton.use-lease=true");

        var counter = singleton(node);

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).until(() -> count(counter)
                .isEmpty());
    }

    @Test
    void eachShardHoldsALease() {
        InMemoryLeaseStore store = new InMemoryLeaseStore();
        ConfigurableApplicationContext node =
                start(store, "spring.actor.cluster.join-self=true", "spring.actor.cluster.sharding.use-lease=true");
        await().atMost(Duration.ofSeconds(20))
                .until(() -> node.getBean(SpringActorSystem.class)
                                .unwrap(Cluster.class)
                                .selfMember()
                                .status()
                        == MemberStatus.up());

        node.getBean(SpringActorSystem.class)
                .sharded(TestShardedActor.class)
                .withId("entity-1")
                .get()
                .tell(new TestShardedActor.Ping("hello"));

        await().atMost(Duration.ofSeconds(20))
                .until(() -> store.held().keySet().stream().anyMatch(lease -> lease.contains("-shard-")));
        assertThat(store.held())
                .allSatisfy((lease, holder) -> assertThat(holder).isEqualTo(owner(node)));
    }

    @Test
    void leaseMajorityKeepsTheSideThatTakesTheLease() {
        InMemoryLeaseStore store = new InMemoryLeaseStore();
        String[] downing = {
            "spring.actor.cluster.downing.strategy=lease-majority",
            "spring.actor.pekko.cluster.split-brain-resolver.stable-after=2s"
        };
        ConfigurableApplicationContext survivor = start(store, concat(downing, "spring.actor.cluster.join-self=true"));
        ConfigurableApplicationContext crashing = start(
                store,
                concat(
                        downing,
                        "spring.actor.cluster.seed-nodes="
                                + survivor.getBean(SpringActorSystem.class).nodeAddress(),
                        // Stop without leaving the cluster, like a crash.
                        "spring.actor.pekko.coordinated-shutdown.run-by-actor-system-terminate=off"));
        Cluster cluster = survivor.getBean(SpringActorSystem.class).unwrap(Cluster.class);
        await().atMost(Duration.ofSeconds(20)).until(() -> upMembers(cluster) == 2);

        crashing.getBean(SpringActorSystem.class).unwrap(ActorSystem.class).terminate();

        await().atMost(Duration.ofSeconds(60))
                .until(() -> cluster.state().getMembers().spliterator().getExactSizeIfKnown() == 1
                        && upMembers(cluster) == 1);
        assertThat(store.holder(name + "-pekko-sbr")).contains(owner(survivor));
    }

    private static long upMembers(Cluster cluster) {
        long up = 0;
        for (var member : cluster.state().getMembers()) {
            if (member.status() == MemberStatus.up()) {
                up++;
            }
        }
        return up;
    }

    private static String[] concat(String[] first, String... more) {
        List<String> all = new ArrayList<>(List.of(first));
        all.addAll(List.of(more));
        return all.toArray(String[]::new);
    }
}
