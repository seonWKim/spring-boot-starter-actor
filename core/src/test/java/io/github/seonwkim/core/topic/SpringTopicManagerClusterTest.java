package io.github.seonwkim.core.topic;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.*;
import io.github.seonwkim.core.serialization.JsonSerializable;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cluster tests for SpringTopicManager to verify distributed pub/sub functionality.
 * Tests cover cross-node message distribution, bounded topic lifecycle, and cluster-wide behavior.
 */
class SpringTopicManagerClusterTest extends AbstractClusterTest {

    @Override
    protected Class<?> getApplicationClass() {
        return TestApp.class;
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {
        @org.springframework.context.annotation.Bean
        public ClusterSubscriberActor clusterSubscriberActor() {
            return new ClusterSubscriberActor();
        }

        @org.springframework.context.annotation.Bean
        public BoundedTopicOwnerActor boundedTopicOwnerActor() {
            return new BoundedTopicOwnerActor();
        }
    }

    // Test message types
    public static class ClusterMessage implements JsonSerializable {
        public final String content;
        public final String nodeId;

        @JsonCreator
        public ClusterMessage(
                @JsonProperty("content") String content,
                @JsonProperty("nodeId") String nodeId) {
            this.content = content;
            this.nodeId = nodeId;
        }
    }

    // ========== Unbounded Topics - Cluster-Wide Tests ==========

    @Test
    void unboundedTopicDistributesMessagesAcrossAllNodes() throws Exception {
        waitUntilClusterInitialized();

        CountDownLatch latch = new CountDownLatch(3); // 3 nodes
        AtomicInteger messageCount = new AtomicInteger(0);

        // Get SpringTopicManager from each node
        SpringTopicManager manager1 = context1.getBean(SpringTopicManager.class);
        SpringTopicManager manager2 = context2.getBean(SpringTopicManager.class);
        SpringTopicManager manager3 = context3.getBean(SpringTopicManager.class);

        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);
        SpringActorSystem system3 = context3.getBean(SpringActorSystem.class);

        // Create unbounded topic on node 1
        SpringTopicRef<ClusterMessage> topic1 = manager1
                .topic(ClusterMessage.class)
                .withName("cluster-wide-topic")
                .create();

        Thread.sleep(500); // Wait for topic to propagate

        // Get same topic on other nodes
        SpringTopicRef<ClusterMessage> topic2 = manager2
                .topic(ClusterMessage.class)
                .withName("cluster-wide-topic")
                .getOrCreate();

        SpringTopicRef<ClusterMessage> topic3 = manager3
                .topic(ClusterMessage.class)
                .withName("cluster-wide-topic")
                .getOrCreate();

        // Create subscribers on each node
        SpringActorRef<ClusterMessage> sub1 = system1
                .actor(ClusterSubscriberActor.class)
                .withId("sub-node-1")
                .withContext(new ClusterSubscriberActor.SubscriberContext(latch, messageCount))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> sub2 = system2
                .actor(ClusterSubscriberActor.class)
                .withId("sub-node-2")
                .withContext(new ClusterSubscriberActor.SubscriberContext(latch, messageCount))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> sub3 = system3
                .actor(ClusterSubscriberActor.class)
                .withId("sub-node-3")
                .withContext(new ClusterSubscriberActor.SubscriberContext(latch, messageCount))
                .spawnAndWait();

        // Subscribe all nodes
        topic1.subscribe(sub1);
        topic2.subscribe(sub2);
        topic3.subscribe(sub3);

        Thread.sleep(500); // Wait for subscriptions to propagate

        // Publish from node 1 - all nodes should receive
        topic1.publish(new ClusterMessage("Hello from node 1", "node-1"));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All 3 nodes should receive the message");
        assertEquals(3, messageCount.get(), "Each node should have received exactly 1 message");
    }

    @Test
    void messagesPublishedFromDifferentNodesReachAllSubscribers() throws Exception {
        waitUntilClusterInitialized();

        CountDownLatch latch = new CountDownLatch(6); // 2 subscribers × 3 messages
        AtomicInteger messageCount = new AtomicInteger(0);

        SpringTopicManager manager1 = context1.getBean(SpringTopicManager.class);
        SpringTopicManager manager2 = context2.getBean(SpringTopicManager.class);
        SpringTopicManager manager3 = context3.getBean(SpringTopicManager.class);

        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);

        // Create topic
        SpringTopicRef<ClusterMessage> topic1 = manager1
                .topic(ClusterMessage.class)
                .withName("multi-publish-topic")
                .create();

        Thread.sleep(300);

        SpringTopicRef<ClusterMessage> topic2 = manager2
                .topic(ClusterMessage.class)
                .withName("multi-publish-topic")
                .getOrCreate();

        SpringTopicRef<ClusterMessage> topic3 = manager3
                .topic(ClusterMessage.class)
                .withName("multi-publish-topic")
                .getOrCreate();

        // Create 2 subscribers on different nodes
        SpringActorRef<ClusterMessage> sub1 = system1
                .actor(ClusterSubscriberActor.class)
                .withId("multi-sub-1")
                .withContext(new ClusterSubscriberActor.SubscriberContext(latch, messageCount))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> sub2 = system2
                .actor(ClusterSubscriberActor.class)
                .withId("multi-sub-2")
                .withContext(new ClusterSubscriberActor.SubscriberContext(latch, messageCount))
                .spawnAndWait();

        topic1.subscribe(sub1);
        topic2.subscribe(sub2);

        Thread.sleep(500);

        // Publish from different nodes
        topic1.publish(new ClusterMessage("From node 1", "node-1"));
        topic2.publish(new ClusterMessage("From node 2", "node-2"));
        topic3.publish(new ClusterMessage("From node 3", "node-3"));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All messages should be received");
        assertEquals(6, messageCount.get(), "2 subscribers × 3 messages = 6");
    }

    // ========== Bounded Topics - Cluster Tests ==========

    @Test
    void boundedTopicWorksAcrossClusterNodes() throws Exception {
        waitUntilClusterInitialized();

        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger messageCount = new AtomicInteger(0);

        SpringTopicManager manager1 = context1.getBean(SpringTopicManager.class);
        SpringTopicManager manager2 = context2.getBean(SpringTopicManager.class);

        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);

        // Create owner actor on node 1 that creates bounded topic
        SpringActorRef<BoundedTopicOwnerActor.Command> owner = system1
                .actor(BoundedTopicOwnerActor.class)
                .withId("bounded-owner")
                .withContext(new BoundedTopicOwnerActor.OwnerContext(manager1, "bounded-cluster-topic"))
                .spawnAndWait();

        Thread.sleep(500);

        // Get the bounded topic from node 2
        SpringTopicRef<ClusterMessage> topic2 = manager2
                .topic(ClusterMessage.class)
                .withName("bounded-cluster-topic")
                .getOrCreate();

        // Create subscribers on both nodes
        SpringActorRef<ClusterMessage> sub1 = system1
                .actor(ClusterSubscriberActor.class)
                .withId("bounded-sub-1")
                .withContext(new ClusterSubscriberActor.SubscriberContext(latch, messageCount))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> sub2 = system2
                .actor(ClusterSubscriberActor.class)
                .withId("bounded-sub-2")
                .withContext(new ClusterSubscriberActor.SubscriberContext(latch, messageCount))
                .spawnAndWait();

        // Get topic from node 1 too
        SpringTopicRef<ClusterMessage> topic1 = manager1
                .topic(ClusterMessage.class)
                .withName("bounded-cluster-topic")
                .getOrCreate();

        topic1.subscribe(sub1);
        topic2.subscribe(sub2);

        Thread.sleep(500);

        // Publish - both nodes should receive
        topic1.publish(new ClusterMessage("Bounded topic message", "node-1"));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Bounded topic should work across nodes");
        assertEquals(2, messageCount.get());
    }

    @Test
    void boundedTopicDiesWhenOwnerDiesAcrossCluster() throws Exception {
        waitUntilClusterInitialized();

        CountDownLatch firstLatch = new CountDownLatch(2);
        CountDownLatch secondLatch = new CountDownLatch(1);
        AtomicInteger messageCount = new AtomicInteger(0);

        SpringTopicManager manager1 = context1.getBean(SpringTopicManager.class);
        SpringTopicManager manager2 = context2.getBean(SpringTopicManager.class);

        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);

        // Create owner on node 1
        SpringActorRef<BoundedTopicOwnerActor.Command> owner = system1
                .actor(BoundedTopicOwnerActor.class)
                .withId("lifecycle-owner")
                .withContext(new BoundedTopicOwnerActor.OwnerContext(manager1, "lifecycle-topic"))
                .spawnAndWait();

        Thread.sleep(500);

        // Create subscribers on both nodes
        SpringActorRef<ClusterMessage> sub1 = system1
                .actor(ClusterSubscriberActor.class)
                .withId("lifecycle-sub-1")
                .withContext(new ClusterSubscriberActor.SubscriberContext(firstLatch, messageCount))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> sub2 = system2
                .actor(ClusterSubscriberActor.class)
                .withId("lifecycle-sub-2")
                .withContext(new ClusterSubscriberActor.SubscriberContext(firstLatch, messageCount))
                .spawnAndWait();

        SpringTopicRef<ClusterMessage> topic1 = manager1
                .topic(ClusterMessage.class)
                .withName("lifecycle-topic")
                .getOrCreate();

        SpringTopicRef<ClusterMessage> topic2 = manager2
                .topic(ClusterMessage.class)
                .withName("lifecycle-topic")
                .getOrCreate();

        topic1.subscribe(sub1);
        topic2.subscribe(sub2);

        Thread.sleep(500);

        // First message - should be received by both
        topic1.publish(new ClusterMessage("Before owner dies", "node-1"));
        assertTrue(firstLatch.await(10, TimeUnit.SECONDS));
        assertEquals(2, messageCount.get());

        // Kill owner
        owner.stop();
        Thread.sleep(1000); // Wait for topic to die cluster-wide

        // Create new subscriber on node 2
        SpringActorRef<ClusterMessage> sub3 = system2
                .actor(ClusterSubscriberActor.class)
                .withId("lifecycle-sub-3")
                .withContext(new ClusterSubscriberActor.SubscriberContext(secondLatch, messageCount))
                .spawnAndWait();

        // Try to subscribe and publish
        topic2.subscribe(sub3);
        topic2.publish(new ClusterMessage("After owner dies", "node-2"));

        // Should NOT receive because topic is dead
        assertFalse(secondLatch.await(2, TimeUnit.SECONDS),
            "Should not receive message after bounded topic owner dies");
        assertEquals(2, messageCount.get(), "Count should remain at 2");
    }

    // ========== Edge Cases - Cluster Tests ==========

    @Test
    void subscriberOnOneNodeUnsubscribesCorrectly() throws Exception {
        waitUntilClusterInitialized();

        CountDownLatch firstLatch = new CountDownLatch(2);
        CountDownLatch secondLatch = new CountDownLatch(1);
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);

        SpringTopicManager manager1 = context1.getBean(SpringTopicManager.class);
        SpringTopicManager manager2 = context2.getBean(SpringTopicManager.class);

        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);

        SpringTopicRef<ClusterMessage> topic1 = manager1
                .topic(ClusterMessage.class)
                .withName("unsub-test-topic")
                .create();

        Thread.sleep(300);

        SpringTopicRef<ClusterMessage> topic2 = manager2
                .topic(ClusterMessage.class)
                .withName("unsub-test-topic")
                .getOrCreate();

        // Create subscribers
        SpringActorRef<ClusterMessage> sub1 = system1
                .actor(ClusterSubscriberActor.class)
                .withId("unsub-sub-1")
                .withContext(new ClusterSubscriberActor.SubscriberContext(firstLatch, count1))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> sub2 = system2
                .actor(ClusterSubscriberActor.class)
                .withId("unsub-sub-2")
                .withContext(new ClusterSubscriberActor.SubscriberContext(secondLatch, count2))
                .spawnAndWait();

        topic1.subscribe(sub1);
        topic2.subscribe(sub2);
        Thread.sleep(500);

        // First message
        topic1.publish(new ClusterMessage("First", "node-1"));
        assertTrue(firstLatch.await(10, TimeUnit.SECONDS));
        assertEquals(1, count1.get());
        assertEquals(1, count2.get());

        // Unsubscribe node 1
        topic1.unsubscribe(sub1);
        Thread.sleep(500);

        // Second message
        topic2.publish(new ClusterMessage("Second", "node-2"));
        assertTrue(secondLatch.await(10, TimeUnit.SECONDS));

        // Node 1 should not have received second message
        assertEquals(1, count1.get(), "Node 1 should not receive after unsubscribe");
        assertEquals(2, count2.get(), "Node 2 should still receive");
    }

    @Test
    void multipleTopicsWorkIndependentlyAcrossCluster() throws Exception {
        waitUntilClusterInitialized();

        CountDownLatch topicALatch = new CountDownLatch(2);
        CountDownLatch topicBLatch = new CountDownLatch(2);
        AtomicInteger countA = new AtomicInteger(0);
        AtomicInteger countB = new AtomicInteger(0);

        SpringTopicManager manager1 = context1.getBean(SpringTopicManager.class);
        SpringTopicManager manager2 = context2.getBean(SpringTopicManager.class);

        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);

        // Create two different topics
        SpringTopicRef<ClusterMessage> topicA1 = manager1
                .topic(ClusterMessage.class)
                .withName("topic-a")
                .create();

        SpringTopicRef<ClusterMessage> topicB1 = manager1
                .topic(ClusterMessage.class)
                .withName("topic-b")
                .create();

        Thread.sleep(300);

        SpringTopicRef<ClusterMessage> topicA2 = manager2
                .topic(ClusterMessage.class)
                .withName("topic-a")
                .getOrCreate();

        SpringTopicRef<ClusterMessage> topicB2 = manager2
                .topic(ClusterMessage.class)
                .withName("topic-b")
                .getOrCreate();

        // Subscribers for topic A
        SpringActorRef<ClusterMessage> subA1 = system1
                .actor(ClusterSubscriberActor.class)
                .withId("multi-a-1")
                .withContext(new ClusterSubscriberActor.SubscriberContext(topicALatch, countA))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> subA2 = system2
                .actor(ClusterSubscriberActor.class)
                .withId("multi-a-2")
                .withContext(new ClusterSubscriberActor.SubscriberContext(topicALatch, countA))
                .spawnAndWait();

        // Subscribers for topic B
        SpringActorRef<ClusterMessage> subB1 = system1
                .actor(ClusterSubscriberActor.class)
                .withId("multi-b-1")
                .withContext(new ClusterSubscriberActor.SubscriberContext(topicBLatch, countB))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> subB2 = system2
                .actor(ClusterSubscriberActor.class)
                .withId("multi-b-2")
                .withContext(new ClusterSubscriberActor.SubscriberContext(topicBLatch, countB))
                .spawnAndWait();

        topicA1.subscribe(subA1);
        topicA2.subscribe(subA2);
        topicB1.subscribe(subB1);
        topicB2.subscribe(subB2);

        Thread.sleep(500);

        // Publish to topic A
        topicA1.publish(new ClusterMessage("Message A", "node-1"));
        assertTrue(topicALatch.await(10, TimeUnit.SECONDS));
        assertEquals(2, countA.get());
        assertEquals(0, countB.get(), "Topic B should not receive topic A messages");

        // Publish to topic B
        topicB2.publish(new ClusterMessage("Message B", "node-2"));
        assertTrue(topicBLatch.await(10, TimeUnit.SECONDS));
        assertEquals(2, countA.get(), "Topic A count should remain");
        assertEquals(2, countB.get());
    }

    // ========== Test Actor Implementations ==========

    public static class ClusterSubscriberActor implements SpringActorWithContext<ClusterMessage, ClusterSubscriberActor.SubscriberContext> {

        public static class SubscriberContext extends SpringActorContext {
            final CountDownLatch latch;
            final AtomicInteger messageCount;

            public SubscriberContext(CountDownLatch latch, AtomicInteger messageCount) {
                this.latch = latch;
                this.messageCount = messageCount;
            }

            @Override
            public String actorId() {
                return "cluster-subscriber";
            }
        }

        @Override
        public SpringActorBehavior<ClusterMessage> create(SubscriberContext actorContext) {
            return SpringActorBehavior.builder(ClusterMessage.class, actorContext)
                    .withState(ctx -> new SubscriberBehavior(actorContext))
                    .onMessage(ClusterMessage.class, SubscriberBehavior::onMessage)
                    .build();
        }

        private static class SubscriberBehavior {
            private final SubscriberContext context;

            SubscriberBehavior(SubscriberContext context) {
                this.context = context;
            }

            private Behavior<ClusterMessage> onMessage(ClusterMessage msg) {
                context.messageCount.incrementAndGet();
                context.latch.countDown();
                return Behaviors.same();
            }
        }
    }

    public static class BoundedTopicOwnerActor implements SpringActorWithContext<BoundedTopicOwnerActor.Command, BoundedTopicOwnerActor.OwnerContext> {

        public interface Command extends JsonSerializable {}

        public static class OwnerContext extends SpringActorContext {
            final SpringTopicManager topicManager;
            final String topicName;

            public OwnerContext(SpringTopicManager topicManager, String topicName) {
                this.topicManager = topicManager;
                this.topicName = topicName;
            }

            @Override
            public String actorId() {
                return "bounded-owner";
            }
        }

        @Override
        public SpringActorBehavior<Command> create(OwnerContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> new OwnerBehavior(ctx, actorContext.topicManager, actorContext.topicName))
                    .build();
        }

        private static class OwnerBehavior {
            private final SpringTopicRef<ClusterMessage> topic;

            OwnerBehavior(SpringBehaviorContext<Command> ctx, SpringTopicManager topicManager, String topicName) {
                // Create bounded topic
                this.topic = topicManager
                        .topic(ClusterMessage.class)
                        .withName(topicName)
                        .ownedBy(ctx)
                        .create();
            }
        }
    }
}
