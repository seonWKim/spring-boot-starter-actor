package io.github.seonwkim.actor;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.seonwkim.actor.cluster.ClusterEvent;
import io.github.seonwkim.actor.cluster.ClusterEvent.MemberLeft;
import io.github.seonwkim.actor.cluster.ClusterEvent.MemberUp;
import io.github.seonwkim.actor.fixture.SimpleShardedActorWithoutWithState;
import io.github.seonwkim.actor.fixture.TestShardedActor;
import io.github.seonwkim.actor.fixture.TestShardedActor.GetState;
import io.github.seonwkim.actor.shard.SpringShardedActorHandle;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Tests for cluster functionality using the shared AbstractClusterTest base class.
 */
public class ClusterTest extends AbstractClusterTest {

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.actor")
    public static class ClusterTestApp {
        @Bean
        public ClusterEventCollector clusterEventCollector() {
            return new ClusterEventCollector();
        }

        @Bean
        public TestShardedActor testShardedActor() {
            return new TestShardedActor();
        }

        @Bean
        public SimpleShardedActorWithoutWithState simpleShardedActorWithoutwithState() {
            return new SimpleShardedActorWithoutWithState();
        }
    }

    public static class ClusterEventCollector {
        private final List<Object> events = new CopyOnWriteArrayList<>();

        @org.springframework.context.event.EventListener
        public void on(ClusterEvent event) {
            events.add(event);
        }

        public int eventCount(Class<?> eventType) {
            return (int) events.stream()
                    .filter(event -> event.getClass() == eventType)
                    .count();
        }
    }

    @Override
    protected Class<?> getApplicationClass() {
        return ClusterTestApp.class;
    }

    @Test
    void clusterEventsShouldBePublished() {
        waitUntilClusterInitialized();
        ClusterEventCollector collector = context1.getBean(ClusterEventCollector.class);

        assertTrue(collector.eventCount(MemberUp.class) >= 3, "Expected at least 3 MemberUp events");

        context2.close();
        context3.close();
        waitUntilClusterHasMembers(1);
        assertTrue(collector.eventCount(MemberLeft.class) >= 2, "Expected at least 2 MemberLeft events");
    }

    @Test
    void sameShardedEntityHandlingMessages() throws Exception {
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);
        SpringActorSystem system3 = context3.getBean(SpringActorSystem.class);
        waitUntilClusterInitialized();

        SpringShardedActorHandle<TestShardedActor.Command> sharedActor1 =
                system1.sharded(TestShardedActor.class).withId("shared-entity").get();
        SpringShardedActorHandle<TestShardedActor.Command> sharedActor2 =
                system2.sharded(TestShardedActor.class).withId("shared-entity").get();
        SpringShardedActorHandle<TestShardedActor.Command> sharedActor3 =
                system3.sharded(TestShardedActor.class).withId("shared-entity").get();

        sharedActor1.tell(new TestShardedActor.Ping("hello shard1"));
        sharedActor2.tell(new TestShardedActor.Ping("hello shard2"));
        sharedActor3.tell(new TestShardedActor.Ping("hello shard3"));

        // The three pings come from three nodes, so they may still be in flight: wait for all of them.
        awaitMessageCount(sharedActor1, 3);
    }

    @Test
    void differentShardedEntitiesHandlingMessages() throws Exception {
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);
        SpringActorSystem system3 = context3.getBean(SpringActorSystem.class);
        waitUntilClusterInitialized();

        System.out.println("Cluster is configured 🚀");
        SpringShardedActorHandle<TestShardedActor.Command> actor1 = system1.sharded(TestShardedActor.class)
                .withId("shared-entity-1")
                .get();
        SpringShardedActorHandle<TestShardedActor.Command> actor2 = system2.sharded(TestShardedActor.class)
                .withId("shared-entity-2")
                .get();
        SpringShardedActorHandle<TestShardedActor.Command> actor3 = system3.sharded(TestShardedActor.class)
                .withId("shared-entity-3")
                .get();

        actor1.tell(new TestShardedActor.Ping("hello shard1"));
        actor2.tell(new TestShardedActor.Ping("hello shard2"));
        actor3.tell(new TestShardedActor.Ping("hello shard3"));

        awaitMessageCount(actor1, 1);
        awaitMessageCount(actor2, 1);
        awaitMessageCount(actor3, 1);
    }

    @Test
    void shardedActorWithoutwithStateWorks() throws Exception {
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        waitUntilClusterInitialized();

        final String entityId = "test-entity";
        SpringShardedActorHandle<SimpleShardedActorWithoutWithState.Command> actor = system1.sharded(
                        SimpleShardedActorWithoutWithState.class)
                .withId(entityId)
                .get();

        // Test Echo message
        String echoResponse = actor.ask(new SimpleShardedActorWithoutWithState.Echo("hello"))
                .withTimeout(Duration.ofSeconds(3))
                .execute()
                .toCompletableFuture()
                .get();
        assertEquals("Echo from entity [" + entityId + "]: hello", echoResponse);

        // Test GetEntityId message
        String entityIdResponse = (String) actor.ask(new SimpleShardedActorWithoutWithState.GetEntityId())
                .withTimeout(Duration.ofSeconds(3))
                .execute()
                .toCompletableFuture()
                .get();
        assertEquals(entityId, entityIdResponse);
    }

    /** Waits until the entity has counted {@code expected} pings (a single read could run first). */
    private static void awaitMessageCount(SpringShardedActorHandle<TestShardedActor.Command> entity, int expected) {
        await().atMost(Duration.ofSeconds(15))
                .until(() -> entity.ask(new GetState())
                                .withTimeout(Duration.ofSeconds(5))
                                .execute()
                                .toCompletableFuture()
                                .get()
                                .getMessageCount()
                        == expected);
    }
}
