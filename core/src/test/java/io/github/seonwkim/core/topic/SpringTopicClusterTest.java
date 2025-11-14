package io.github.seonwkim.core.topic;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.*;
import io.github.seonwkim.core.serialization.JsonSerializable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Cluster tests for SpringTopicManager to verify distributed pub/sub functionality.
 * Tests use unique topic names per test to avoid interference.
 * Waits are increased to account for eventually-consistent cluster pub/sub propagation.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SpringTopicClusterTest extends AbstractClusterTest {

    @Override
    protected Class<?> getApplicationClass() {
        return TestApp.class;
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {
        @Bean
        public ClusterSubscriberActor clusterSubscriberActor() {
            return new ClusterSubscriberActor();
        }
    }

    // Test message types
    public static class ClusterMessage implements JsonSerializable {
        public final String content;
        public final String nodeId;

        @JsonCreator
        public ClusterMessage(@JsonProperty("content") String content, @JsonProperty("nodeId") String nodeId) {
            this.content = content;
            this.nodeId = nodeId;
        }
    }

    // ========== Basic Cluster Pub/Sub Tests ==========

    @Test
    void messageDistributesAcrossAllNodesInCluster() throws Exception {
        waitUntilClusterInitialized();

        CountDownLatch latch = new CountDownLatch(3); // 3 nodes
        AtomicInteger messageCount = new AtomicInteger(0);

        SpringTopicManager manager1 = context1.getBean(SpringTopicManager.class);
        SpringTopicManager manager2 = context2.getBean(SpringTopicManager.class);
        SpringTopicManager manager3 = context3.getBean(SpringTopicManager.class);

        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);
        SpringActorSystem system3 = context3.getBean(SpringActorSystem.class);

        // Create topic on node 1
        SpringTopicRef<ClusterMessage> topic1 = manager1.topic(ClusterMessage.class)
                .withName("messageDistributesAcrossAllNodesInCluster-topic")
                .create();

        Thread.sleep(1000); // Wait for cluster propagation

        // Get same topic on other nodes
        SpringTopicRef<ClusterMessage> topic2 = manager2.topic(ClusterMessage.class)
                .withName("messageDistributesAcrossAllNodesInCluster-topic")
                .getOrCreate();

        SpringTopicRef<ClusterMessage> topic3 = manager3.topic(ClusterMessage.class)
                .withName("messageDistributesAcrossAllNodesInCluster-topic")
                .getOrCreate();

        // Create subscribers on each node
        SpringActorRef<ClusterMessage> sub1 = system1.actor(ClusterSubscriberActor.class)
                .withContext(new ClusterSubscriberActor.SubscriberContext(
                        latch, messageCount, "messageDistributesAcrossAllNodesInCluster-sub-1"))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> sub2 = system2.actor(ClusterSubscriberActor.class)
                .withContext(new ClusterSubscriberActor.SubscriberContext(
                        latch, messageCount, "messageDistributesAcrossAllNodesInCluster-sub-2"))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> sub3 = system3.actor(ClusterSubscriberActor.class)
                .withContext(new ClusterSubscriberActor.SubscriberContext(
                        latch, messageCount, "messageDistributesAcrossAllNodesInCluster-sub-3"))
                .spawnAndWait();

        topic1.subscribe(sub1);
        topic2.subscribe(sub2);
        topic3.subscribe(sub3);
        Thread.sleep(1000); // Wait for subscriptions to propagate across cluster

        // Publish from node 1 - all nodes should receive
        topic1.publish(new ClusterMessage("Hello cluster", "node-1"));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All 3 nodes should receive the message");
        assertEquals(3, messageCount.get());
    }

    @Test
    void publishFromDifferentNodesReachesAllSubscribers() throws Exception {
        waitUntilClusterInitialized();

        CountDownLatch latch = new CountDownLatch(6); // 2 subscribers × 3 messages
        AtomicInteger messageCount = new AtomicInteger(0);

        SpringTopicManager manager1 = context1.getBean(SpringTopicManager.class);
        SpringTopicManager manager2 = context2.getBean(SpringTopicManager.class);
        SpringTopicManager manager3 = context3.getBean(SpringTopicManager.class);

        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);

        // Create topic
        SpringTopicRef<ClusterMessage> topic1 = manager1.topic(ClusterMessage.class)
                .withName("publishFromDifferentNodesReachesAllSubscribers-topic")
                .create();

        Thread.sleep(1000); // Wait for topic creation to propagate

        SpringTopicRef<ClusterMessage> topic2 = manager2.topic(ClusterMessage.class)
                .withName("publishFromDifferentNodesReachesAllSubscribers-topic")
                .getOrCreate();

        SpringTopicRef<ClusterMessage> topic3 = manager3.topic(ClusterMessage.class)
                .withName("publishFromDifferentNodesReachesAllSubscribers-topic")
                .getOrCreate();

        // Create 2 subscribers on different nodes
        SpringActorRef<ClusterMessage> sub1 = system1.actor(ClusterSubscriberActor.class)
                .withContext(new ClusterSubscriberActor.SubscriberContext(
                        latch, messageCount, "publishFromDifferentNodesReachesAllSubscribers-sub-1"))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> sub2 = system2.actor(ClusterSubscriberActor.class)
                .withContext(new ClusterSubscriberActor.SubscriberContext(
                        latch, messageCount, "publishFromDifferentNodesReachesAllSubscribers-sub-2"))
                .spawnAndWait();

        topic1.subscribe(sub1);
        topic2.subscribe(sub2);
        Thread.sleep(1000); // Wait for cluster propagation

        // Publish from different nodes
        topic1.publish(new ClusterMessage("From node 1", "node-1"));
        topic2.publish(new ClusterMessage("From node 2", "node-2"));
        topic3.publish(new ClusterMessage("From node 3", "node-3"));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All messages should be received");
        assertEquals(6, messageCount.get());
    }

    // ========== Unsubscribe in Cluster ==========

    @Test
    void unsubscribeOnOneNodeStopsMessagesOnlyForThatNode() throws Exception {
        waitUntilClusterInitialized();

        CountDownLatch latch = new CountDownLatch(2); // Both receive first message
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);

        SpringTopicManager manager1 = context1.getBean(SpringTopicManager.class);
        SpringTopicManager manager2 = context2.getBean(SpringTopicManager.class);

        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);

        SpringTopicRef<ClusterMessage> topic1 = manager1.topic(ClusterMessage.class)
                .withName("unsubscribeOnOneNodeStopsMessagesOnlyForThatNode-topic")
                .create();

        Thread.sleep(1000); // Wait for topic creation to propagate

        SpringTopicRef<ClusterMessage> topic2 = manager2.topic(ClusterMessage.class)
                .withName("unsubscribeOnOneNodeStopsMessagesOnlyForThatNode-topic")
                .getOrCreate();

        // Create subscribers
        SpringActorRef<ClusterMessage> sub1 = system1.actor(ClusterSubscriberActor.class)
                .withContext(new ClusterSubscriberActor.SubscriberContext(
                        latch, count1, "unsubscribeOnOneNodeStopsMessagesOnlyForThatNode-sub-1"))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> sub2 = system2.actor(ClusterSubscriberActor.class)
                .withContext(new ClusterSubscriberActor.SubscriberContext(
                        latch, count2, "unsubscribeOnOneNodeStopsMessagesOnlyForThatNode-sub-2"))
                .spawnAndWait();

        topic1.subscribe(sub1);
        topic2.subscribe(sub2);
        Thread.sleep(1000); // Wait for cluster propagation

        // First message - both receive
        topic1.publish(new ClusterMessage("First", "node-1"));
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Both nodes should receive first message");
        assertEquals(1, count1.get());
        assertEquals(1, count2.get());

        // Unsubscribe node 1
        topic1.unsubscribe(sub1);
        Thread.sleep(1000); // Wait longer for unsubscribe to propagate across cluster

        // Second message - only node 2 receives
        topic2.publish(new ClusterMessage("Second", "node-2"));
        Thread.sleep(1000); // Wait for message delivery

        assertEquals(1, count1.get(), "Node 1 should not receive after unsubscribe");
        assertEquals(2, count2.get(), "Node 2 should still receive");
    }

    // ========== Edge Cases in Cluster ==========

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
        SpringTopicRef<ClusterMessage> topicA1 = manager1.topic(ClusterMessage.class)
                .withName("multipleTopicsWorkIndependentlyAcrossCluster-topic-a")
                .create();

        SpringTopicRef<ClusterMessage> topicB1 = manager1.topic(ClusterMessage.class)
                .withName("multipleTopicsWorkIndependentlyAcrossCluster-topic-b")
                .create();

        Thread.sleep(1000); // Wait for topic creation to propagate

        SpringTopicRef<ClusterMessage> topicA2 = manager2.topic(ClusterMessage.class)
                .withName("multipleTopicsWorkIndependentlyAcrossCluster-topic-a")
                .getOrCreate();

        SpringTopicRef<ClusterMessage> topicB2 = manager2.topic(ClusterMessage.class)
                .withName("multipleTopicsWorkIndependentlyAcrossCluster-topic-b")
                .getOrCreate();

        // Subscribers for topic A
        SpringActorRef<ClusterMessage> subA1 = system1.actor(ClusterSubscriberActor.class)
                .withContext(new ClusterSubscriberActor.SubscriberContext(
                        topicALatch, countA, "multipleTopicsWorkIndependentlyAcrossCluster-sub-a-1"))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> subA2 = system2.actor(ClusterSubscriberActor.class)
                .withContext(new ClusterSubscriberActor.SubscriberContext(
                        topicALatch, countA, "multipleTopicsWorkIndependentlyAcrossCluster-sub-a-2"))
                .spawnAndWait();

        // Subscribers for topic B
        SpringActorRef<ClusterMessage> subB1 = system1.actor(ClusterSubscriberActor.class)
                .withContext(new ClusterSubscriberActor.SubscriberContext(
                        topicBLatch, countB, "multipleTopicsWorkIndependentlyAcrossCluster-sub-b-1"))
                .spawnAndWait();

        SpringActorRef<ClusterMessage> subB2 = system2.actor(ClusterSubscriberActor.class)
                .withContext(new ClusterSubscriberActor.SubscriberContext(
                        topicBLatch, countB, "multipleTopicsWorkIndependentlyAcrossCluster-sub-b-2"))
                .spawnAndWait();

        topicA1.subscribe(subA1);
        topicA2.subscribe(subA2);
        topicB1.subscribe(subB1);
        topicB2.subscribe(subB2);
        Thread.sleep(1000); // Wait for cluster propagation

        // Publish to topic A
        topicA1.publish(new ClusterMessage("Message A", "node-1"));
        assertTrue(topicALatch.await(10, TimeUnit.SECONDS));
        assertEquals(2, countA.get());
        assertEquals(0, countB.get(), "Topic B should not receive topic A messages");

        // Publish to topic B
        topicB2.publish(new ClusterMessage("Message B", "node-2"));
        assertTrue(topicBLatch.await(10, TimeUnit.SECONDS));
        assertEquals(2, countA.get());
        assertEquals(2, countB.get());
    }

    @Test
    void subscriberOnOneNodeCanReceiveFromAnotherNode() throws Exception {
        waitUntilClusterInitialized();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger messageCount = new AtomicInteger(0);

        SpringTopicManager manager1 = context1.getBean(SpringTopicManager.class);
        SpringTopicManager manager2 = context2.getBean(SpringTopicManager.class);

        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);

        // Create topic on node 1
        SpringTopicRef<ClusterMessage> topic1 = manager1.topic(ClusterMessage.class)
                .withName("subscriberOnOneNodeCanReceiveFromAnotherNode-topic")
                .create();

        Thread.sleep(1000); // Wait for topic creation to propagate

        SpringTopicRef<ClusterMessage> topic2 = manager2.topic(ClusterMessage.class)
                .withName("subscriberOnOneNodeCanReceiveFromAnotherNode-topic")
                .getOrCreate();

        // Create subscriber ONLY on node 2
        SpringActorRef<ClusterMessage> sub2 = system2.actor(ClusterSubscriberActor.class)
                .withContext(new ClusterSubscriberActor.SubscriberContext(
                        latch, messageCount, "subscriberOnOneNodeCanReceiveFromAnotherNode-sub-2"))
                .spawnAndWait();

        topic2.subscribe(sub2);
        Thread.sleep(1000); // Wait for cluster propagation

        // Publish from node 1 - should reach node 2
        topic1.publish(new ClusterMessage("Cross-node message", "node-1"));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Message should cross node boundaries");
        assertEquals(1, messageCount.get());
    }

    // ========== Test Actor Implementation ==========

    public static class ClusterSubscriberActor
            implements SpringActorWithContext<ClusterMessage, ClusterSubscriberActor.SubscriberContext> {

        public static class SubscriberContext extends SpringActorContext {
            final CountDownLatch latch;
            final AtomicInteger messageCount;
            final String actorId;

            public SubscriberContext(CountDownLatch latch, AtomicInteger messageCount, String actorId) {
                this.latch = latch;
                this.messageCount = messageCount;
                this.actorId = actorId;
            }

            @Override
            public String actorId() {
                return actorId;
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
}
