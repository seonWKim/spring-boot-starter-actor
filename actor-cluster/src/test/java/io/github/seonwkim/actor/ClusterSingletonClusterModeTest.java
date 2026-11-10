package io.github.seonwkim.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Cluster mode tests for cluster singletons - verifies that cluster singletons work correctly
 * in a multi-node cluster.
 *
 * <p>These tests use the shared AbstractClusterTest base class for cluster initialization.
 */
public class ClusterSingletonClusterModeTest extends AbstractClusterTest {

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.actor")
    public static class ClusterSingletonTestApp {}

    @Override
    protected Class<?> getApplicationClass() {
        return ClusterSingletonTestApp.class;
    }

    /**
     * Tests that a cluster singleton can be spawned successfully in cluster mode.
     */
    @Test
    public void testClusterSingletonSpawnsSuccessfully() throws Exception {
        waitUntilClusterInitialized();
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);

        // When: Spawning a cluster singleton
        SpringActorHandle<ClusterSingletonTest.SingletonTestActor.Command> singleton = system1.actor(
                        ClusterSingletonTest.SingletonTestActor.class)
                .withId("test-singleton")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get();

        // Then: The singleton should be created successfully
        assertThat(singleton).isNotNull();
    }

    /** A singleton is not a child of the root guardian, but find and exists on the node that spawned it see it. */
    @Test
    public void findAndExistsSeeASingletonThisNodeSpawned() throws Exception {
        waitUntilClusterInitialized();
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        Class<ClusterSingletonTest.SingletonTestActor> type = ClusterSingletonTest.SingletonTestActor.class;

        system1.actor(type)
                .withId("found-singleton")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get();

        assertThat(system1.find(type, "found-singleton").toCompletableFuture().get())
                .isPresent();
        assertThat(system1.exists(type, "found-singleton").toCompletableFuture().get())
                .isTrue();
    }

    /**
     * Tests that messages from all nodes are handled by the same singleton instance.
     * This verifies that only ONE instance exists across the cluster.
     */
    @Test
    public void testAllNodesShareSameSingletonInstance() throws Exception {
        waitUntilClusterInitialized();
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);
        SpringActorSystem system3 = context3.getBean(SpringActorSystem.class);

        // Given: Get singleton proxy from each node
        SpringActorHandle<ClusterSingletonTest.SingletonTestActor.Command> singleton1 = system1.actor(
                        ClusterSingletonTest.SingletonTestActor.class)
                .withId("shared-singleton")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get();

        SpringActorHandle<ClusterSingletonTest.SingletonTestActor.Command> singleton2 = system2.actor(
                        ClusterSingletonTest.SingletonTestActor.class)
                .withId("shared-singleton")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get();

        SpringActorHandle<ClusterSingletonTest.SingletonTestActor.Command> singleton3 = system3.actor(
                        ClusterSingletonTest.SingletonTestActor.class)
                .withId("shared-singleton")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get();

        // When: Sending increment messages from all three nodes using their respective proxies
        singleton1.tell(new ClusterSingletonTest.SingletonTestActor.Increment());
        singleton2.tell(new ClusterSingletonTest.SingletonTestActor.Increment());
        singleton3.tell(new ClusterSingletonTest.SingletonTestActor.Increment());

        // Then: All messages should be handled by the same instance
        // Query from any node should return the same count
        awaitCount(singleton1, 3);
    }

    /**
     * Tests that the cluster singleton can be accessed from any node in the cluster.
     * The proxy reference should work from all nodes.
     */
    @Test
    public void testSingletonAccessibleFromAllNodes() throws Exception {
        waitUntilClusterInitialized();
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);

        // Given: A cluster singleton spawned from node 1
        SpringActorHandle<ClusterSingletonTest.SingletonTestActor.Command> singleton = system1.actor(
                        ClusterSingletonTest.SingletonTestActor.class)
                .withId("accessible-singleton")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get();

        // When: Sending messages from different nodes via the proxy
        singleton.tell(new ClusterSingletonTest.SingletonTestActor.Increment()); // from node 1
        singleton.tell(new ClusterSingletonTest.SingletonTestActor.Increment()); // from node 1
        singleton.tell(new ClusterSingletonTest.SingletonTestActor.Increment()); // from node 1

        // Then: Query from any node should return the same count
        awaitCount(singleton, 3);
    }

    /**
     * Tests that attempting to spawn the same singleton twice returns successfully.
     * The ClusterSingleton.init() is idempotent.
     */
    @Test
    public void testSingletonSpawnIsIdempotent() throws Exception {
        waitUntilClusterInitialized();
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);

        // Given: A cluster singleton spawned from node 1
        SpringActorHandle<ClusterSingletonTest.SingletonTestActor.Command> singleton1 = system1.actor(
                        ClusterSingletonTest.SingletonTestActor.class)
                .withId("idempotent-singleton")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get();

        // When: Attempting to spawn the same singleton from node 2
        SpringActorHandle<ClusterSingletonTest.SingletonTestActor.Command> singleton2 = system2.actor(
                        ClusterSingletonTest.SingletonTestActor.class)
                .withId("idempotent-singleton")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get();

        // Then: Both should succeed (ClusterSingleton.init is idempotent)
        assertThat(singleton1).isNotNull();
        assertThat(singleton2).isNotNull();

        // And: Both should refer to the same singleton instance
        // Send increment from both references
        singleton1.tell(new ClusterSingletonTest.SingletonTestActor.Increment());
        singleton2.tell(new ClusterSingletonTest.SingletonTestActor.Increment());

        // Query should show 2 increments (same instance)
        awaitCount(singleton1, 2);
    }

    /**
     * Waits until the singleton has counted {@code expected} increments. Messages sent through
     * proxies on different nodes are not ordered with each other, so a single read could run
     * before an increment from another node arrives.
     */
    private static void awaitCount(
            SpringActorHandle<ClusterSingletonTest.SingletonTestActor.Command> singleton, int expected) {
        await().atMost(Duration.ofSeconds(15))
                .until(() -> singleton
                                .ask(new ClusterSingletonTest.SingletonTestActor.GetCount())
                                .withTimeout(Duration.ofSeconds(5))
                                .execute()
                                .toCompletableFuture()
                                .get()
                                .count
                        == expected);
    }
}
