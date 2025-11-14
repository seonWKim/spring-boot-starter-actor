package io.github.seonwkim.core.topic;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.*;
import io.github.seonwkim.core.serialization.JsonSerializable;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for SpringTopicManager pub/sub functionality.
 * Tests basic operations, edge cases, and lifecycle management.
 */
@SpringBootTest(
    properties = {
        "spring.actor.pekko.name=topic-test",
        "spring.actor.pekko.actor.provider=local"
    }
)
class SpringTopicTest {

    @Autowired
    private SpringActorSystem actorSystem;

    @Autowired
    private SpringTopicManager topicManager;

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {
        @Bean
        public SubscriberActor subscriberActor() {
            return new SubscriberActor();
        }

        @Bean
        public TopicCreatorActor topicCreatorActor() {
            return new TopicCreatorActor();
        }
    }

    // Test message
    public static class TestMessage implements JsonSerializable {
        public final String content;

        @JsonCreator
        public TestMessage(@JsonProperty("content") String content) {
            this.content = content;
        }
    }

    // ========== Basic Pub/Sub Tests ==========

    @Test
    void basicPublishSubscribe() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger messageCount = new AtomicInteger(0);

        // Create topic
        SpringTopicRef<TestMessage> topic = topicManager
                .topic(TestMessage.class)
                .withName("basic-topic")
                .create();

        // Create subscriber
        SpringActorRef<TestMessage> subscriber = actorSystem
                .actor(SubscriberActor.class)
                .withContext(new SubscriberActor.SubscriberContext(latch, messageCount, "basicPublishSubscribe-sub-1"))
                .spawnAndWait();

        // Subscribe and publish
        topic.subscribe(subscriber);
        Thread.sleep(200);

        topic.publish(new TestMessage("Hello"));

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Subscriber should receive message");
        assertEquals(1, messageCount.get());
    }

    @Test
    void multipleSubscribersReceiveSameMessage() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger messageCount = new AtomicInteger(0);

        SpringTopicRef<TestMessage> topic = topicManager
                .topic(TestMessage.class)
                .withName("multi-sub-topic")
                .create();

        // Create 3 subscribers
        SpringActorRef<TestMessage> sub1 = actorSystem
                .actor(SubscriberActor.class)
                .withContext(new SubscriberActor.SubscriberContext(latch, messageCount, "multipleSubscribers-sub-1"))
                .spawnAndWait();

        SpringActorRef<TestMessage> sub2 = actorSystem
                .actor(SubscriberActor.class)
                .withContext(new SubscriberActor.SubscriberContext(latch, messageCount, "multipleSubscribers-sub-2"))
                .spawnAndWait();

        SpringActorRef<TestMessage> sub3 = actorSystem
                .actor(SubscriberActor.class)
                .withContext(new SubscriberActor.SubscriberContext(latch, messageCount, "multipleSubscribers-sub-3"))
                .spawnAndWait();

        topic.subscribe(sub1);
        topic.subscribe(sub2);
        topic.subscribe(sub3);
        Thread.sleep(200);

        topic.publish(new TestMessage("Broadcast"));

        assertTrue(latch.await(5, TimeUnit.SECONDS), "All subscribers should receive message");
        assertEquals(3, messageCount.get());
    }

    @Test
    void unsubscribeStopsReceivingMessages() throws Exception {
        CountDownLatch firstLatch = new CountDownLatch(1);
        CountDownLatch secondLatch = new CountDownLatch(1);
        AtomicInteger messageCount = new AtomicInteger(0);

        SpringTopicRef<TestMessage> topic = topicManager
                .topic(TestMessage.class)
                .withName("unsub-topic")
                .create();

        SpringActorRef<TestMessage> subscriber = actorSystem
                .actor(SubscriberActor.class)
                .withContext(new SubscriberActor.SubscriberContext(firstLatch, messageCount, "unsubscribeStopsReceivingMessages-sub-1"))
                .spawnAndWait();

        // Subscribe and receive first message
        topic.subscribe(subscriber);
        Thread.sleep(200);
        topic.publish(new TestMessage("First"));
        assertTrue(firstLatch.await(5, TimeUnit.SECONDS));
        assertEquals(1, messageCount.get());

        // Unsubscribe
        topic.unsubscribe(subscriber);
        Thread.sleep(200);

        // Publish second message - should NOT be received
        topic.publish(new TestMessage("Second"));
        assertFalse(secondLatch.await(2, TimeUnit.SECONDS),
                "Subscriber should not receive message after unsubscribe");
        assertEquals(1, messageCount.get(), "Message count should remain at 1");
    }

    @Test
    void partialUnsubscribeOtherSubscribersContinue() throws Exception {
        CountDownLatch latch = new CountDownLatch(2); // Only 2 will receive second message
        AtomicInteger count1 = new AtomicInteger(0);
        AtomicInteger count2 = new AtomicInteger(0);
        AtomicInteger count3 = new AtomicInteger(0);

        SpringTopicRef<TestMessage> topic = topicManager
                .topic(TestMessage.class)
                .withName("partial-unsub-topic")
                .create();

        SpringActorRef<TestMessage> sub1 = actorSystem
                .actor(SubscriberActor.class)
                .withContext(new SubscriberActor.SubscriberContext(new CountDownLatch(1), count1, "partialUnsubscribeOtherSubscribersContinue-sub-1"))
                .spawnAndWait();

        SpringActorRef<TestMessage> sub2 = actorSystem
                .actor(SubscriberActor.class)
                .withContext(new SubscriberActor.SubscriberContext(latch, count2, "partialUnsubscribeOtherSubscribersContinue-sub-2"))
                .spawnAndWait();

        SpringActorRef<TestMessage> sub3 = actorSystem
                .actor(SubscriberActor.class)
                .withContext(new SubscriberActor.SubscriberContext(latch, count3, "partialUnsubscribeOtherSubscribersContinue-sub-3"))
                .spawnAndWait();

        topic.subscribe(sub1);
        topic.subscribe(sub2);
        topic.subscribe(sub3);
        Thread.sleep(200);

        // Unsubscribe sub1
        topic.unsubscribe(sub1);
        Thread.sleep(200);

        // Publish - only sub2 and sub3 should receive
        topic.publish(new TestMessage("After unsubscribe"));
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(0, count1.get(), "Unsubscribed actor should not receive");
        assertEquals(1, count2.get());
        assertEquals(1, count3.get());
    }

    // ========== Edge Cases ==========

    @Test
    void publishToTopicWithNoSubscribers() throws Exception {
        SpringTopicRef<TestMessage> topic = topicManager
                .topic(TestMessage.class)
                .withName("empty-topic")
                .create();

        // Should not throw exception
        assertDoesNotThrow(() -> topic.publish(new TestMessage("No subscribers")));
    }

    @Test
    void duplicateSubscribeIsSafe() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger messageCount = new AtomicInteger(0);

        SpringTopicRef<TestMessage> topic = topicManager
                .topic(TestMessage.class)
                .withName("dup-sub-topic")
                .create();

        SpringActorRef<TestMessage> subscriber = actorSystem
                .actor(SubscriberActor.class)
                .withContext(new SubscriberActor.SubscriberContext(latch, messageCount, "duplicateSubscribeIsSafe-sub-1"))
                .spawnAndWait();

        // Subscribe twice
        topic.subscribe(subscriber);
        topic.subscribe(subscriber);
        Thread.sleep(200);

        topic.publish(new TestMessage("Message"));
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // Should only receive once (Pekko Topic handles deduplication)
        assertEquals(1, messageCount.get());
    }

    @Test
    void unsubscribeNonSubscribedActorIsSafe() throws Exception {
        SpringTopicRef<TestMessage> topic = topicManager
                .topic(TestMessage.class)
                .withName("safe-unsub-topic")
                .create();

        SpringActorRef<TestMessage> subscriber = actorSystem
                .actor(SubscriberActor.class)
                .withContext(new SubscriberActor.SubscriberContext(new CountDownLatch(1), new AtomicInteger(0), "unsubscribeNonSubscribedActorIsSafe-sub-1"))
                .spawnAndWait();

        // Unsubscribe without subscribing first - should not throw
        assertDoesNotThrow(() -> topic.unsubscribe(subscriber));
    }

    @Test
    void getOrCreateIsIdempotent() throws Exception {
        SpringTopicRef<TestMessage> topic1 = topicManager
                .topic(TestMessage.class)
                .withName("idempotent-topic")
                .getOrCreate();

        SpringTopicRef<TestMessage> topic2 = topicManager
                .topic(TestMessage.class)
                .withName("idempotent-topic")
                .getOrCreate();

        // Should return the same topic
        assertEquals(topic1.getUnderlying().path(), topic2.getUnderlying().path());

        // Both references should work
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger messageCount = new AtomicInteger(0);

        SpringActorRef<TestMessage> subscriber = actorSystem
                .actor(SubscriberActor.class)
                .withContext(new SubscriberActor.SubscriberContext(latch, messageCount, "getOrCreateIsIdempotent-sub-1"))
                .spawnAndWait();

        topic1.subscribe(subscriber);
        Thread.sleep(200);

        topic2.publish(new TestMessage("Test"));
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, messageCount.get());
    }

    @Test
    void multipleTopicsWorkIndependently() throws Exception {
        CountDownLatch latchA = new CountDownLatch(1);
        CountDownLatch latchB = new CountDownLatch(1);
        AtomicInteger countA = new AtomicInteger(0);
        AtomicInteger countB = new AtomicInteger(0);

        SpringTopicRef<TestMessage> topicA = topicManager
                .topic(TestMessage.class)
                .withName("topic-a")
                .create();

        SpringTopicRef<TestMessage> topicB = topicManager
                .topic(TestMessage.class)
                .withName("topic-b")
                .create();

        SpringActorRef<TestMessage> subA = actorSystem
                .actor(SubscriberActor.class)
                .withContext(new SubscriberActor.SubscriberContext(latchA, countA, "sub-a"))
                .spawnAndWait();

        SpringActorRef<TestMessage> subB = actorSystem
                .actor(SubscriberActor.class)
                .withContext(new SubscriberActor.SubscriberContext(latchB, countB, "sub-b"))
                .spawnAndWait();

        topicA.subscribe(subA);
        topicB.subscribe(subB);
        Thread.sleep(200);

        // Publish to A
        topicA.publish(new TestMessage("To A"));
        assertTrue(latchA.await(5, TimeUnit.SECONDS));
        assertEquals(1, countA.get());
        assertEquals(0, countB.get(), "Topic B should not receive topic A messages");

        // Publish to B
        topicB.publish(new TestMessage("To B"));
        assertTrue(latchB.await(5, TimeUnit.SECONDS));
        assertEquals(1, countA.get());
        assertEquals(1, countB.get());
    }

    // ========== Test Actor Implementations ==========

    public static class SubscriberActor implements SpringActorWithContext<TestMessage, SubscriberActor.SubscriberContext> {

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
        public SpringActorBehavior<TestMessage> create(SubscriberContext actorContext) {
            return SpringActorBehavior.builder(TestMessage.class, actorContext)
                    .withState(ctx -> new SubscriberBehavior(actorContext))
                    .onMessage(TestMessage.class, SubscriberBehavior::onMessage)
                    .build();
        }

        private static class SubscriberBehavior {
            private final SubscriberContext context;

            SubscriberBehavior(SubscriberContext context) {
                this.context = context;
            }

            private Behavior<TestMessage> onMessage(TestMessage msg) {
                context.messageCount.incrementAndGet();
                context.latch.countDown();
                return Behaviors.same();
            }
        }
    }

    public static class TopicCreatorActor implements SpringActorWithContext<TopicCreatorActor.Command, TopicCreatorActor.CreatorContext> {

        public interface Command extends JsonSerializable {}

        public static class CreatorContext extends SpringActorContext {
            final SpringTopicManager topicManager;
            final String topicName;
            final String actorId;

            public CreatorContext(SpringTopicManager topicManager, String topicName, String actorId) {
                this.topicManager = topicManager;
                this.topicName = topicName;
                this.actorId = actorId;
            }

            @Override
            public String actorId() {
                return actorId;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(CreatorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> new CreatorBehavior(actorContext.topicManager, actorContext.topicName))
                    .build();
        }

        private static class CreatorBehavior {
            @SuppressWarnings("unused")
            private final SpringTopicRef<TestMessage> topic;

            CreatorBehavior(SpringTopicManager topicManager, String topicName) {
                this.topic = topicManager
                        .topic(TestMessage.class)
                        .withName(topicName)
                        .create();
            }
        }
    }
}
