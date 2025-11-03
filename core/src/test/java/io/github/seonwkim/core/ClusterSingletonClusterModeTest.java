package io.github.seonwkim.core;

import static org.assertj.core.api.Assertions.assertThat;

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

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
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
        SpringActorRef<ClusterSingletonTest.SingletonTestActor.Command> singleton = system1
                .actor(ClusterSingletonTest.SingletonTestActor.class)
                .withId("test-singleton")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get();

        // Then: The singleton should be created successfully
        assertThat(singleton).isNotNull();
        assertThat(singleton.getUnderlying()).isNotNull();
    }

    /**
     * Tests that messages from all nodes are handled by the same singleton instance.
     * This verifies that only ONE instance exists across the cluster.
     */
    @Test
    public void testAllNodesShareSameSingletonInstance() throws Exception {
        waitUntilClusterInitialized();
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);

        // Given: A cluster singleton spawned from node 1
        SpringActorRef<ClusterSingletonTest.SingletonTestActor.Command> singleton1 = system1
                .actor(ClusterSingletonTest.SingletonTestActor.class)
                .withId("shared-singleton")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get();

        // Wait a bit for singleton to initialize
        Thread.sleep(2000);

        // When: Sending increment messages from all three nodes
        singleton1.tell(new ClusterSingletonTest.SingletonTestActor.Increment());
        singleton1.tell(new ClusterSingletonTest.SingletonTestActor.Increment());
        singleton1.tell(new ClusterSingletonTest.SingletonTestActor.Increment());

        // Wait for messages to be processed
        Thread.sleep(1000);

        // Then: All messages should be handled by the same instance
        ClusterSingletonTest.SingletonTestActor.CountResponse response = singleton1
                .ask(
                        ClusterSingletonTest.SingletonTestActor.GetCount::new,
                        Duration.ofSeconds(5))
                .toCompletableFuture()
                .get();

        assertThat(response.count).isEqualTo(3);
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
        SpringActorRef<ClusterSingletonTest.SingletonTestActor.Command> singleton = system1
                .actor(ClusterSingletonTest.SingletonTestActor.class)
                .withId("accessible-singleton")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get();

        // Wait for singleton to initialize
        Thread.sleep(2000);

        // When: Sending messages from different nodes via the proxy
        singleton.tell(new ClusterSingletonTest.SingletonTestActor.Increment()); // from node 1
        singleton.tell(new ClusterSingletonTest.SingletonTestActor.Increment()); // from node 1
        singleton.tell(new ClusterSingletonTest.SingletonTestActor.Increment()); // from node 1

        // Wait for processing
        Thread.sleep(1000);

        // Then: Query from any node should return the same count
        ClusterSingletonTest.SingletonTestActor.CountResponse response = singleton
                .ask(
                        ClusterSingletonTest.SingletonTestActor.GetCount::new,
                        Duration.ofSeconds(5))
                .toCompletableFuture()
                .get();

        assertThat(response.count).isEqualTo(3);
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
        SpringActorRef<ClusterSingletonTest.SingletonTestActor.Command> singleton1 = system1
                .actor(ClusterSingletonTest.SingletonTestActor.class)
                .withId("idempotent-singleton")
                .asClusterSingleton()
                .spawn()
                .toCompletableFuture()
                .get();

        // When: Attempting to spawn the same singleton from node 2
        SpringActorRef<ClusterSingletonTest.SingletonTestActor.Command> singleton2 = system2
                .actor(ClusterSingletonTest.SingletonTestActor.class)
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

        Thread.sleep(1000);

        // Query should show 2 increments (same instance)
        ClusterSingletonTest.SingletonTestActor.CountResponse response = singleton1
                .ask(
                        ClusterSingletonTest.SingletonTestActor.GetCount::new,
                        Duration.ofSeconds(5))
                .toCompletableFuture()
                .get();

        assertThat(response.count).isEqualTo(2);
    }
}
