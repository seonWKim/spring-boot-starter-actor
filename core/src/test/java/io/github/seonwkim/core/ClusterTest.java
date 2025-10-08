package io.github.seonwkim.core;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.seonwkim.core.behavior.ClusterEventBehavior.ClusterDomainWrappedEvent;
import io.github.seonwkim.core.fixture.TestShardedActor;
import io.github.seonwkim.core.fixture.TestShardedActor.GetState;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.cluster.ClusterEvent.MemberLeft;
import org.apache.pekko.cluster.ClusterEvent.MemberUp;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.typed.Cluster;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

public class ClusterTest {

    private static ConfigurableApplicationContext context1;
    private static ConfigurableApplicationContext context2;
    private static ConfigurableApplicationContext context3;

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    public static class ClusterTestApp {
        @Bean
        public ClusterEventCollector clusterEventCollector() {
            return new ClusterEventCollector();
        }

        @Bean
        public TestShardedActor testShardedActor() {
            return new TestShardedActor();
        }
    }

    public static class ClusterEventCollector {
        private final List<Object> events = new CopyOnWriteArrayList<>();

        @org.springframework.context.event.EventListener
        public void onMemberUp(ClusterDomainWrappedEvent event) {
            events.add(event.getEvent());
        }

        public int eventCount(Class<?> eventType) {
            return (int) events.stream()
                    .filter(event -> event.getClass() == eventType)
                    .count();
        }
    }

    private static final int BASE_HTTP_PORT = 30000;
    private static final int BASE_ARTERY_PORT = 40000;
    private static int portOffset = 0;

    @BeforeEach
    void setUp() {
        final int[] httpPorts = {
            BASE_HTTP_PORT + portOffset, BASE_HTTP_PORT + portOffset + 1, BASE_HTTP_PORT + portOffset + 2
        };

        final int[] arteryPorts = {
            BASE_ARTERY_PORT + portOffset, BASE_ARTERY_PORT + portOffset + 1, BASE_ARTERY_PORT + portOffset + 2
        };

        portOffset += 3; // Increment offset so next test uses fresh ports

        String seedNodes = String.format(
                "pekko://spring-pekko-example@127.0.0.1:%d,pekko://spring-pekko-example@127.0.0.1:%d,pekko://spring-pekko-example@127.0.0.1:%d",
                arteryPorts[0], arteryPorts[1], arteryPorts[2]);

        context1 = startContext(httpPorts[0], arteryPorts[0], seedNodes);
        context2 = startContext(httpPorts[1], arteryPorts[1], seedNodes);
        context3 = startContext(httpPorts[2], arteryPorts[2], seedNodes);
    }

    @AfterEach
    void tearDown() {
        System.out.println("Cluster shutting down 🚀");
        if (context1.isActive()) {
            context1.close();
        }
        if (context2.isActive()) {
            context2.close();
        }
        if (context3.isActive()) {
            context3.close();
        }
    }

    private static ConfigurableApplicationContext startContext(int httpPort, int arteryPort, String seedNodes) {
        return new SpringApplicationBuilder(ClusterTestApp.class)
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

    @Test
    void clusterEventsShouldBePublished() {
        ClusterEventCollector collector = context1.getBean(ClusterEventCollector.class);

        await().atMost(10, SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> collector.eventCount(MemberUp.class) >= 3);
        assertTrue(collector.eventCount(MemberUp.class) >= 3, "Expected at least 3 MemberUp events");

        context2.close();
        context3.close();
        await().atMost(10, SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> collector.eventCount(MemberLeft.class) >= 2);
        assertTrue(collector.eventCount(MemberLeft.class) >= 2, "Expected at least 3 MemberLeft events");
    }

    @Test
    void sameShardedEntityHandlingMessages() throws Exception {
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);
        SpringActorSystem system3 = context3.getBean(SpringActorSystem.class);
        waitUntilClusterInitialized();

        System.out.println("Cluster is configured 🚀");
        SpringShardedActorRef<TestShardedActor.Command> sharedActor1 =
                system1.sharded(TestShardedActor.class).withId("shared-entity").get();
        SpringShardedActorRef<TestShardedActor.Command> sharedActor2 =
                system2.sharded(TestShardedActor.class).withId("shared-entity").get();
        SpringShardedActorRef<TestShardedActor.Command> sharedActor3 =
                system3.sharded(TestShardedActor.class).withId("shared-entity").get();

        sharedActor1.tell(new TestShardedActor.Ping("hello shard1"));
        sharedActor2.tell(new TestShardedActor.Ping("hello shard2"));
        sharedActor3.tell(new TestShardedActor.Ping("hello shard3"));

        Thread.sleep(500); // wait for messages to be processed
        TestShardedActor.State state = sharedActor1
                .ask(GetState::new, Duration.ofSeconds(3))
                .toCompletableFuture()
                .get();
        assertEquals(3, state.getMessageCount());
    }

    @Test
    void differentShardedEntitiesHandlingMessages() throws Exception {
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);
        SpringActorSystem system3 = context3.getBean(SpringActorSystem.class);
        waitUntilClusterInitialized();

        System.out.println("Cluster is configured 🚀");
        SpringShardedActorRef<TestShardedActor.Command> actor1 = system1.sharded(TestShardedActor.class)
                .withId("shared-entity-1")
                .get();
        SpringShardedActorRef<TestShardedActor.Command> actor2 = system2.sharded(TestShardedActor.class)
                .withId("shared-entity-2")
                .get();
        SpringShardedActorRef<TestShardedActor.Command> actor3 = system3.sharded(TestShardedActor.class)
                .withId("shared-entity-3")
                .get();

        actor1.tell(new TestShardedActor.Ping("hello shard1"));
        actor2.tell(new TestShardedActor.Ping("hello shard2"));
        actor3.tell(new TestShardedActor.Ping("hello shard3"));

        Thread.sleep(500); // wait for messages to be processed
        assertEquals(
                1,
                actor1.ask(GetState::new, Duration.ofSeconds(3))
                        .toCompletableFuture()
                        .get()
                        .getMessageCount());
        assertEquals(
                1,
                actor2.ask(GetState::new, Duration.ofSeconds(3))
                        .toCompletableFuture()
                        .get()
                        .getMessageCount());
        assertEquals(
                1,
                actor3.ask(GetState::new, Duration.ofSeconds(3))
                        .toCompletableFuture()
                        .get()
                        .getMessageCount());
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
}
