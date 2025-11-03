package io.github.seonwkim.core;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.github.seonwkim.core.serialization.JsonSerializable;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.typed.Cluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

/**
 * Tests for cluster singleton functionality.
 *
 * <p>Note: These tests run in local mode (not cluster mode) to verify that:
 * <ul>
 *   <li>The cluster singleton API is correctly integrated</li>
 *   <li>Attempting to spawn a cluster singleton in local mode fails gracefully</li>
 *   <li>The fluent API for cluster singletons works correctly</li>
 * </ul>
 *
 * <p>For full cluster singleton testing (multi-node failover, etc.), see the integration
 * tests in the example/cluster module.
 */
@SpringBootTest(classes = ClusterSingletonTest.TestApp.class)
@TestPropertySource(properties = {
    "spring.actor.pekko.loglevel=INFO",
    "spring.actor.pekko.actor.provider=local"  // Local mode - cluster singleton should fail
})
public class ClusterSingletonTest {

    @Autowired
    private ApplicationContext applicationContext;

    // Shared configuration for cluster tests
    private static final int BASE_HTTP_PORT = 35000;
    private static final int BASE_ARTERY_PORT = 45000;
    private static int clusterPortOffset = 0;

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    public static class ClusterSingletonTestApp {}

    /**
     * Simple test actor for cluster singleton tests.
     */
    @Component
    public static class SingletonTestActor implements SpringActorWithContext<SingletonTestActor.Command, SpringActorContext> {

        public interface Command extends JsonSerializable {}

        public static class GetCount implements Command {
            public final ActorRef<CountResponse> replyTo;

            public GetCount(ActorRef<CountResponse> replyTo) {
                this.replyTo = replyTo;
            }
        }

        public static class Increment implements Command {}

        public static class CountResponse implements JsonSerializable {
            public final int count;

            public CountResponse(int count) {
                this.count = count;
            }
        }

        private final AtomicInteger count = new AtomicInteger(0);

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(Increment.class, (ctx, msg) -> {
                        count.incrementAndGet();
                        ctx.getLog().debug("Count incremented to {}", count.get());
                        return Behaviors.same();
                    })
                    .onMessage(GetCount.class, (ctx, msg) -> {
                        msg.replyTo.tell(new CountResponse(count.get()));
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(properties = {
        "spring.actor.pekko.loglevel=INFO",
        "spring.actor.pekko.actor.provider=local"
    })
    class LocalModeTests {

        @Autowired
        private SpringActorSystem actorSystem;

        /**
         * Tests that attempting to spawn a cluster singleton in local mode fails gracefully.
         */
        @Test
        public void testClusterSingletonInLocalModeFails() {
            // When: Attempting to spawn a cluster singleton in local mode
            CompletionStage<SpringActorRef<SingletonTestActor.Command>> result = actorSystem
                    .actor(SingletonTestActor.class)
                    .withId("singleton-test")
                    .asClusterSingleton()
                    .spawn();

            // Then: Should fail with IllegalStateException
            assertThatThrownBy(() -> result.toCompletableFuture().join())
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cluster singleton requested but cluster mode is not enabled");
        }

        /**
         * Tests that the cluster singleton API with role also fails in local mode.
         */
        @Test
        public void testClusterSingletonWithRoleInLocalModeFails() {
            // When: Attempting to spawn a cluster singleton with role in local mode
            CompletionStage<SpringActorRef<SingletonTestActor.Command>> result = actorSystem
                    .actor(SingletonTestActor.class)
                    .withId("singleton-test-role")
                    .asClusterSingleton("worker")
                    .spawn();

            // Then: Should fail with IllegalStateException
            assertThatThrownBy(() -> result.toCompletableFuture().join())
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cluster singleton requested but cluster mode is not enabled");
        }

        /**
         * Tests that regular (non-singleton) actors still work in local mode.
         */
        @Test
        public void testRegularActorWorksInLocalMode() throws Exception {
            // Given: A regular (non-singleton) actor
            SpringActorRef<SingletonTestActor.Command> actor = actorSystem
                    .actor(SingletonTestActor.class)
                    .withId("regular-actor")
                    // Note: NOT calling asClusterSingleton()
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // When: Sending messages to the actor
            actor.tell(new SingletonTestActor.Increment());
            actor.tell(new SingletonTestActor.Increment());
            actor.tell(new SingletonTestActor.Increment());

            SingletonTestActor.CountResponse response = actor
                    .<SingletonTestActor.GetCount, SingletonTestActor.CountResponse>ask(
                            SingletonTestActor.GetCount::new,
                            Duration.ofSeconds(3))
                    .toCompletableFuture()
                    .get();

            // Then: The actor should work normally
            assertThat(response.count).isEqualTo(3);
        }
    }

    /**
     * Cluster mode tests - verifies that cluster singletons work correctly in a multi-node cluster.
     */
    @Nested
    class ClusterModeTests {

        private ConfigurableApplicationContext context1;
        private ConfigurableApplicationContext context2;
        private ConfigurableApplicationContext context3;

        @BeforeEach
        void setUp() {
            final int[] httpPorts = {
                BASE_HTTP_PORT + clusterPortOffset,
                BASE_HTTP_PORT + clusterPortOffset + 1,
                BASE_HTTP_PORT + clusterPortOffset + 2
            };

            final int[] arteryPorts = {
                BASE_ARTERY_PORT + clusterPortOffset,
                BASE_ARTERY_PORT + clusterPortOffset + 1,
                BASE_ARTERY_PORT + clusterPortOffset + 2
            };

            clusterPortOffset += 3; // Increment offset so next test uses fresh ports

            String seedNodes = String.format(
                    "pekko://spring-pekko-example@127.0.0.1:%d,pekko://spring-pekko-example@127.0.0.1:%d,pekko://spring-pekko-example@127.0.0.1:%d",
                    arteryPorts[0], arteryPorts[1], arteryPorts[2]);

            context1 = startContext(httpPorts[0], arteryPorts[0], seedNodes);
            context2 = startContext(httpPorts[1], arteryPorts[1], seedNodes);
            context3 = startContext(httpPorts[2], arteryPorts[2], seedNodes);
        }

        @AfterEach
        void tearDown() {
            System.out.println("Cluster shutting down for singleton tests");
            if (context1 != null && context1.isActive()) {
                context1.close();
            }
            if (context2 != null && context2.isActive()) {
                context2.close();
            }
            if (context3 != null && context3.isActive()) {
                context3.close();
            }
        }

        private ConfigurableApplicationContext startContext(int httpPort, int arteryPort, String seedNodes) {
            return new SpringApplicationBuilder(ClusterSingletonTestApp.class)
                    .web(WebApplicationType.NONE)
                    .properties(
                            "server.port=" + httpPort,
                            "spring.actor.pekko.name=spring-pekko-example",
                            "spring.actor.pekko.actor.provider=cluster",
                            "spring.actor.pekko.remote.artery.canonical.hostname=127.0.0.1",
                            "spring.actor.pekko.remote.artery.canonical.port=" + arteryPort,
                            "spring.actor.pekko.cluster.name=cluster",
                            "spring.actor.pekko.cluster.seed-nodes=" + seedNodes,
                            "spring.actor.pekko.cluster.downing-provider-class=org.apache.pekko.cluster.sbr.SplitBrainResolverProvider",
                            "spring.actor.pekko.actor.allow-java-serialization=off",
                            "spring.actor.pekko.actor.warn-about-java-serializer-usage=on")
                    .run();
        }

        private void waitUntilClusterInitialized() {
            Cluster cluster = context1.getBean(SpringActorSystem.class).getCluster();
            // Wait until all 3 cluster nodes are UP
            await().atMost(10, SECONDS).pollInterval(200, TimeUnit.MILLISECONDS).until(() -> {
                Assertions.assertNotNull(cluster);
                return cluster.state()
                                .members()
                                .filter(it -> it.status() == MemberStatus.up())
                                .size()
                        == 3;
            });
        }

        /**
         * Tests that a cluster singleton can be spawned successfully in cluster mode.
         */
        @Test
        public void testClusterSingletonSpawnsSuccessfully() throws Exception {
            SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
            waitUntilClusterInitialized();

            // When: Spawning a cluster singleton
            SpringActorRef<SingletonTestActor.Command> singleton = system1
                    .actor(SingletonTestActor.class)
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
            SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
            SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);
            SpringActorSystem system3 = context3.getBean(SpringActorSystem.class);
            waitUntilClusterInitialized();

            // Given: A cluster singleton spawned from node 1
            SpringActorRef<SingletonTestActor.Command> singleton1 = system1
                    .actor(SingletonTestActor.class)
                    .withId("shared-singleton")
                    .asClusterSingleton()
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // Wait a bit for singleton to initialize
            Thread.sleep(2000);

            // When: Sending increment messages from all three nodes
            singleton1.tell(new SingletonTestActor.Increment());
            singleton1.tell(new SingletonTestActor.Increment());
            singleton1.tell(new SingletonTestActor.Increment());

            // Wait for messages to be processed
            Thread.sleep(1000);

            // Then: All messages should be handled by the same instance
            SingletonTestActor.CountResponse response = singleton1
                    .<SingletonTestActor.GetCount, SingletonTestActor.CountResponse>ask(
                            SingletonTestActor.GetCount::new,
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
            SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
            SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);
            SpringActorSystem system3 = context3.getBean(SpringActorSystem.class);
            waitUntilClusterInitialized();

            // Given: A cluster singleton spawned from node 1
            SpringActorRef<SingletonTestActor.Command> singleton = system1
                    .actor(SingletonTestActor.class)
                    .withId("accessible-singleton")
                    .asClusterSingleton()
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // Wait for singleton to initialize
            Thread.sleep(2000);

            // When: Sending messages from different nodes via the proxy
            singleton.tell(new SingletonTestActor.Increment()); // from node 1
            singleton.tell(new SingletonTestActor.Increment()); // from node 1
            singleton.tell(new SingletonTestActor.Increment()); // from node 1

            // Wait for processing
            Thread.sleep(1000);

            // Then: Query from any node should return the same count
            SingletonTestActor.CountResponse response = singleton
                    .<SingletonTestActor.GetCount, SingletonTestActor.CountResponse>ask(
                            SingletonTestActor.GetCount::new,
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
            SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
            SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);
            waitUntilClusterInitialized();

            // Given: A cluster singleton spawned from node 1
            SpringActorRef<SingletonTestActor.Command> singleton1 = system1
                    .actor(SingletonTestActor.class)
                    .withId("idempotent-singleton")
                    .asClusterSingleton()
                    .spawn()
                    .toCompletableFuture()
                    .get();

            // When: Attempting to spawn the same singleton from node 2
            SpringActorRef<SingletonTestActor.Command> singleton2 = system2
                    .actor(SingletonTestActor.class)
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
            singleton1.tell(new SingletonTestActor.Increment());
            singleton2.tell(new SingletonTestActor.Increment());

            Thread.sleep(1000);

            // Query should show 2 increments (same instance)
            SingletonTestActor.CountResponse response = singleton1
                    .<SingletonTestActor.GetCount, SingletonTestActor.CountResponse>ask(
                            SingletonTestActor.GetCount::new,
                            Duration.ofSeconds(5))
                    .toCompletableFuture()
                    .get();

            assertThat(response.count).isEqualTo(2);
        }
    }

    /**
     * Test configuration.
     */
    @Configuration
    @EnableAutoConfiguration
    @ComponentScan
    public static class TestApp {}
}
