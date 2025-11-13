package io.github.seonwkim.core.topic;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.*;
import io.github.seonwkim.core.serialization.JsonSerializable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.InvalidActorNameException;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

class SpringTopicTest {

    // Test messages
    public interface TestMessage extends JsonSerializable {
    }

    public static class TextMessage implements TestMessage {
        public final String content;

        @JsonCreator
        public TextMessage(@JsonProperty("content") String content) {
            this.content = content;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TextMessage that = (TextMessage) o;
            return content.equals(that.content);
        }

        @Override
        public int hashCode() {
            return content.hashCode();
        }
    }

    public static class NumberMessage implements TestMessage {
        public final int value;

        @JsonCreator
        public NumberMessage(@JsonProperty("value") int value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NumberMessage that = (NumberMessage) o;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return value;
        }
    }

    /**
     * Actor that manages topics - creates them from SpringBehaviorContext and coordinates pub/sub.
     */
    @Component
    public static class TopicManagerActor
            implements SpringActorWithContext<TopicManagerActor.Command, TopicManagerActor.ManagerContext> {

        public interface Command extends JsonSerializable {
        }

        public static class CreateTopic implements Command {
            public final String topicName;

            @JsonCreator
            public CreateTopic(@JsonProperty("topicName") String topicName) {
                this.topicName = topicName;
            }
        }

        public static class PublishTextMessage implements Command {
            public final TextMessage message;

            @JsonCreator
            public PublishTextMessage(@JsonProperty("message") TextMessage message) {
                this.message = message;
            }
        }

        public static class PublishNumberMessage implements Command {
            public final NumberMessage message;

            @JsonCreator
            public PublishNumberMessage(@JsonProperty("message") NumberMessage message) {
                this.message = message;
            }
        }

        public static class Subscribe implements Command {
            public final SpringActorRef<TestMessage> subscriber;

            @JsonCreator
            public Subscribe(@JsonProperty("subscriber") SpringActorRef<TestMessage> subscriber) {
                this.subscriber = subscriber;
            }
        }

        public static class Unsubscribe implements Command {
            public final SpringActorRef<TestMessage> subscriber;

            @JsonCreator
            public Unsubscribe(@JsonProperty("subscriber") SpringActorRef<TestMessage> subscriber) {
                this.subscriber = subscriber;
            }
        }

        public static class GetTopicRef extends AskCommand<SpringTopicRef<TestMessage>> implements Command {
            @JsonCreator
            public GetTopicRef() {
            }
        }

        public static class ManagerContext extends SpringActorContext {
            private final String actorId;

            public ManagerContext(String actorId) {
                this.actorId = actorId;
            }

            @Override
            public String actorId() {
                return actorId;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(ManagerContext context) {
            return SpringActorBehavior.builder(Command.class, context)
                    .withState(ManagerBehavior::new)
                    .onMessage(CreateTopic.class, ManagerBehavior::onCreateTopic)
                    .onMessage(PublishTextMessage.class, ManagerBehavior::onPublishTextMessage)
                    .onMessage(PublishNumberMessage.class, ManagerBehavior::onPublishNumberMessage)
                    .onMessage(Subscribe.class, ManagerBehavior::onSubscribe)
                    .onMessage(Unsubscribe.class, ManagerBehavior::onUnsubscribe)
                    .onMessage(GetTopicRef.class, ManagerBehavior::onGetTopicRef)
                    .build();
        }

        private static class ManagerBehavior {
            private final SpringBehaviorContext<Command> ctx;
            private SpringTopicRef<TestMessage> topic;

            ManagerBehavior(SpringBehaviorContext<Command> ctx) {
                this.ctx = ctx;
            }

            private Behavior<Command> onCreateTopic(CreateTopic msg) {
                topic = ctx.createTopic(TestMessage.class, msg.topicName);
                ctx.getLog().info("Created topic: {}", msg.topicName);
                return Behaviors.same();
            }

            private Behavior<Command> onPublishTextMessage(
                    PublishTextMessage msg) {
                if (topic != null) {
                    topic.publish(msg.message);
                }
                return Behaviors.same();
            }

            private Behavior<Command> onPublishNumberMessage(
                    PublishNumberMessage msg) {
                if (topic != null) {
                    topic.publish(msg.message);
                }
                return Behaviors.same();
            }

            private Behavior<Command> onSubscribe(Subscribe msg) {
                if (topic != null) {
                    topic.subscribe(msg.subscriber);
                }
                return Behaviors.same();
            }

            private Behavior<Command> onUnsubscribe(Unsubscribe msg) {
                if (topic != null) {
                    topic.unsubscribe(msg.subscriber);
                }
                return Behaviors.same();
            }

            private Behavior<Command> onGetTopicRef(GetTopicRef msg) {
                msg.reply(topic);
                return Behaviors.same();
            }
        }
    }

    // Test subscriber actor
    @Component
    static class SubscriberActor implements SpringActor<TestMessage> {

        @Override
        public SpringActorBehavior<TestMessage> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestMessage.class, actorContext)
                    .withState(ctx -> new SubscriberBehavior())
                    .onMessage(TextMessage.class, SubscriberBehavior::onTextMessage)
                    .onMessage(NumberMessage.class, SubscriberBehavior::onNumberMessage)
                    .build();
        }

        static class SubscriberBehavior {
            private final List<TestMessage> receivedMessages =
                    Collections.synchronizedList(new ArrayList<>());

            Behavior<TestMessage> onTextMessage(TextMessage msg) {
                receivedMessages.add(msg);
                return Behaviors.same();
            }

            Behavior<TestMessage> onNumberMessage(NumberMessage msg) {
                receivedMessages.add(msg);
                return Behaviors.same();
            }

            List<TestMessage> getReceivedMessages() {
                return new ArrayList<>(receivedMessages);
            }
        }
    }

    // Test actor with ask command to retrieve received messages
    @Component
    static class QueryableSubscriberActor implements SpringActor<QueryableSubscriberActor.Command> {

        public interface Command extends JsonSerializable {
        }

        public static class GetReceivedMessages extends AskCommand<List<TestMessage>> implements Command {
            @JsonCreator
            public GetReceivedMessages() {
            }
        }

        // Text and Number messages also implement Command
        public static class TextMessageCommand implements Command {
            public final String content;

            @JsonCreator
            public TextMessageCommand(@JsonProperty("content") String content) {
                this.content = content;
            }
        }

        public static class NumberMessageCommand implements Command {
            public final int value;

            @JsonCreator
            public NumberMessageCommand(@JsonProperty("value") int value) {
                this.value = value;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .withState(ctx -> new QueryableBehavior())
                    .onMessage(TextMessageCommand.class, QueryableBehavior::onTextMessage)
                    .onMessage(NumberMessageCommand.class, QueryableBehavior::onNumberMessage)
                    .onMessage(GetReceivedMessages.class, QueryableBehavior::onGetReceivedMessages)
                    .build();
        }

        static class QueryableBehavior {
            private final List<TestMessage> receivedMessages =
                    Collections.synchronizedList(new ArrayList<>());

            Behavior<Command> onTextMessage(TextMessageCommand msg) {
                receivedMessages.add(new TextMessage(msg.content));
                return Behaviors.same();
            }

            Behavior<Command> onNumberMessage(NumberMessageCommand msg) {
                receivedMessages.add(new NumberMessage(msg.value));
                return Behaviors.same();
            }

            Behavior<Command> onGetReceivedMessages(GetReceivedMessages msg) {
                msg.reply(new ArrayList<>(receivedMessages));
                return Behaviors.same();
            }
        }
    }

    // Test actor that uses a latch to signal message receipt
    @Component
    static class LatchSubscriberActor
            implements SpringActorWithContext<TestMessage, LatchSubscriberActor.LatchActorContext> {

        static class LatchActorContext extends SpringActorContext {
            private final String actorId;
            public final CountDownLatch latch;
            public final List<TestMessage> receivedMessages;

            LatchActorContext(String actorId, CountDownLatch latch) {
                this.actorId = actorId;
                this.latch = latch;
                this.receivedMessages = Collections.synchronizedList(new ArrayList<>());
            }

            @Override
            public String actorId() {
                return actorId;
            }
        }

        @Override
        public SpringActorBehavior<TestMessage> create(LatchActorContext actorContext) {
            return SpringActorBehavior.builder(TestMessage.class, actorContext)
                    .onMessage(TextMessage.class, (ctx, msg) -> {
                        actorContext.receivedMessages.add(msg);
                        actorContext.latch.countDown();
                        return Behaviors.same();
                    })
                    .onMessage(NumberMessage.class, (ctx, msg) -> {
                        actorContext.receivedMessages.add(msg);
                        actorContext.latch.countDown();
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(
            properties = {
                    "spring.actor.pekko.loglevel=INFO",
                    "spring.actor.pekko.actor.provider=local"
            })
    class BasicPubSubTest {

        @Test
        void createTopicFromBehaviorContext(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create topic manager
            SpringActorRef<TopicManagerActor.Command> manager = actorSystem
                    .actor(TopicManagerActor.class)
                    .withContext(new TopicManagerActor.ManagerContext("manager-create-topic"))
                    .spawnAndWait();

            // Create topic
            manager.tell(new TopicManagerActor.CreateTopic("test-topic"));
            Thread.sleep(500);

            // Verify topic was created
            SpringTopicRef<TestMessage> topic =
                    manager.ask(new TopicManagerActor.GetTopicRef()).execute().toCompletableFuture().get();

            assertThat(topic).isNotNull();
            assertThat(topic.getTopicName()).isEqualTo("test-topic");
        }

        @Test
        void canCreateMultipleTopicsWithDifferentNames(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create two topic managers with different topics
            SpringActorRef<TopicManagerActor.Command> manager1 = actorSystem
                    .actor(TopicManagerActor.class)
                    .withContext(new TopicManagerActor.ManagerContext("manager-topic-1"))
                    .spawnAndWait();

            SpringActorRef<TopicManagerActor.Command> manager2 = actorSystem
                    .actor(TopicManagerActor.class)
                    .withContext(new TopicManagerActor.ManagerContext("manager-topic-2"))
                    .spawnAndWait();

            // Create topics
            manager1.tell(new TopicManagerActor.CreateTopic("topic-1"));
            manager2.tell(new TopicManagerActor.CreateTopic("topic-2"));
            Thread.sleep(500);

            // Get topic refs
            SpringTopicRef<TestMessage> topic1 =
                    manager1.ask(new TopicManagerActor.GetTopicRef()).execute().toCompletableFuture().get();
            SpringTopicRef<TestMessage> topic2 =
                    manager2.ask(new TopicManagerActor.GetTopicRef()).execute().toCompletableFuture().get();

            assertThat(topic1.getTopicName()).isEqualTo("topic-1");
            assertThat(topic2.getTopicName()).isEqualTo("topic-2");
            assertThat(topic1.getTopicName()).isNotEqualTo(topic2.getTopicName());
        }

        @Test
        void publishAndSubscribeSingleMessage(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create topic manager
            SpringActorRef<TopicManagerActor.Command> manager = actorSystem
                    .actor(TopicManagerActor.class)
                    .withContext(new TopicManagerActor.ManagerContext("manager-single-msg"))
                    .spawnAndWait();

            manager.tell(new TopicManagerActor.CreateTopic("single-message-topic"));
            Thread.sleep(500);

            // Create subscriber actor with latch
            CountDownLatch latch = new CountDownLatch(1);
            LatchSubscriberActor.LatchActorContext actorContext =
                    new LatchSubscriberActor.LatchActorContext("single-msg-subscriber", latch);
            SpringActorRef<TestMessage> subscriber =
                    actorSystem.actor(LatchSubscriberActor.class).withContext(actorContext).spawnAndWait();

            // Subscribe
            manager.tell(new TopicManagerActor.Subscribe(subscriber));
            Thread.sleep(100);

            // Publish message
            manager.tell(new TopicManagerActor.PublishTextMessage(new TextMessage("Hello, World!")));

            // Wait for message to be received
            boolean received = latch.await(5, TimeUnit.SECONDS);
            assertTrue(received, "Message should be received by subscriber");

            // Verify message content
            List<TestMessage> messages = actorContext.receivedMessages;
            assertEquals(1, messages.size());
            assertEquals("Hello, World!", ((TextMessage) messages.get(0)).content);
        }

        @Test
        void multipleSubscribersReceiveSameMessage(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create topic manager
            SpringActorRef<TopicManagerActor.Command> manager = actorSystem
                    .actor(TopicManagerActor.class)
                    .withContext(new TopicManagerActor.ManagerContext("manager-multi-sub"))
                    .spawnAndWait();

            manager.tell(new TopicManagerActor.CreateTopic("multiple-subscribers-topic"));
            Thread.sleep(500);

            // Create multiple subscriber actors
            int subscriberCount = 3;
            CountDownLatch latch = new CountDownLatch(subscriberCount);
            List<LatchSubscriberActor.LatchActorContext> contexts = new ArrayList<>();

            for (int i = 0; i < subscriberCount; i++) {
                LatchSubscriberActor.LatchActorContext actorContext =
                        new LatchSubscriberActor.LatchActorContext("multi-sub-subscriber-" + i, latch);
                contexts.add(actorContext);

                SpringActorRef<TestMessage> subscriber = actorSystem
                        .actor(LatchSubscriberActor.class)
                        .withContext(actorContext)
                        .spawnAndWait();

                manager.tell(new TopicManagerActor.Subscribe(subscriber));
            }

            // Give subscriptions time to propagate
            Thread.sleep(100);

            // Publish a single message
            manager.tell(new TopicManagerActor.PublishTextMessage(new TextMessage("Broadcast message")));

            // Wait for all subscribers to receive the message
            boolean allReceived = latch.await(5, TimeUnit.SECONDS);
            assertTrue(allReceived, "All subscribers should receive the message");

            // Verify each subscriber received the message
            for (LatchSubscriberActor.LatchActorContext ctx : contexts) {
                assertEquals(1, ctx.receivedMessages.size());
                assertEquals("Broadcast message", ((TextMessage) ctx.receivedMessages.get(0)).content);
            }
        }

        @Test
        void unsubscribeStopsReceivingMessages(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create topic manager
            SpringActorRef<TopicManagerActor.Command> manager = actorSystem
                    .actor(TopicManagerActor.class)
                    .withContext(new TopicManagerActor.ManagerContext("manager-unsub"))
                    .spawnAndWait();

            manager.tell(new TopicManagerActor.CreateTopic("unsubscribe-topic"));
            Thread.sleep(500);

            // Create subscriber
            CountDownLatch latch = new CountDownLatch(1);
            LatchSubscriberActor.LatchActorContext actorContext =
                    new LatchSubscriberActor.LatchActorContext("unsub-subscriber", latch);
            SpringActorRef<TestMessage> subscriber =
                    actorSystem.actor(LatchSubscriberActor.class).withContext(actorContext).spawnAndWait();

            // Subscribe and receive first message
            manager.tell(new TopicManagerActor.Subscribe(subscriber));
            Thread.sleep(100);

            manager.tell(new TopicManagerActor.PublishTextMessage(new TextMessage("Message 1")));
            latch.await(5, TimeUnit.SECONDS);

            assertEquals(1, actorContext.receivedMessages.size());

            // Unsubscribe
            manager.tell(new TopicManagerActor.Unsubscribe(subscriber));
            Thread.sleep(100);

            // Publish second message - should NOT be received
            manager.tell(new TopicManagerActor.PublishTextMessage(new TextMessage("Message 2")));
            Thread.sleep(500); // Wait to ensure message would have been delivered

            // Should still only have the first message
            assertEquals(1, actorContext.receivedMessages.size());
            assertEquals("Message 1", ((TextMessage) actorContext.receivedMessages.get(0)).content);
        }

        @Test
        void publishMultipleMessagesToMultipleSubscribers(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create topic manager
            SpringActorRef<TopicManagerActor.Command> manager = actorSystem
                    .actor(TopicManagerActor.class)
                    .withContext(new TopicManagerActor.ManagerContext("manager-multi-msgs"))
                    .spawnAndWait();

            manager.tell(new TopicManagerActor.CreateTopic("multiple-messages-topic"));
            Thread.sleep(500);

            // Create subscribers
            int subscriberCount = 2;
            int messageCount = 5;
            CountDownLatch latch = new CountDownLatch(subscriberCount * messageCount);
            List<LatchSubscriberActor.LatchActorContext> contexts = new ArrayList<>();

            for (int i = 0; i < subscriberCount; i++) {
                LatchSubscriberActor.LatchActorContext actorContext =
                        new LatchSubscriberActor.LatchActorContext("multi-msgs-subscriber-" + i, latch);
                contexts.add(actorContext);

                SpringActorRef<TestMessage> subscriber = actorSystem
                        .actor(LatchSubscriberActor.class)
                        .withContext(actorContext)
                        .spawnAndWait();

                manager.tell(new TopicManagerActor.Subscribe(subscriber));
            }

            Thread.sleep(100);

            // Publish multiple messages
            for (int i = 0; i < messageCount; i++) {
                manager.tell(new TopicManagerActor.PublishNumberMessage(new NumberMessage(i)));
            }

            // Wait for all messages to be received
            boolean allReceived = latch.await(10, TimeUnit.SECONDS);
            assertTrue(allReceived, "All messages should be received by all subscribers");

            // Verify each subscriber received all messages
            for (LatchSubscriberActor.LatchActorContext ctx : contexts) {
                assertEquals(messageCount, ctx.receivedMessages.size());
            }
        }

        @Test
        void topicsAreIndependentWithinSameActor(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create two topic managers
            SpringActorRef<TopicManagerActor.Command> manager1 = actorSystem
                    .actor(TopicManagerActor.class)
                    .withContext(new TopicManagerActor.ManagerContext("manager-independent-1"))
                    .spawnAndWait();

            SpringActorRef<TopicManagerActor.Command> manager2 = actorSystem
                    .actor(TopicManagerActor.class)
                    .withContext(new TopicManagerActor.ManagerContext("manager-independent-2"))
                    .spawnAndWait();

            // Create different topics
            manager1.tell(new TopicManagerActor.CreateTopic("topic-a"));
            manager2.tell(new TopicManagerActor.CreateTopic("topic-b"));
            Thread.sleep(500);

            // Create subscribers
            CountDownLatch latch1 = new CountDownLatch(1);
            CountDownLatch latch2 = new CountDownLatch(1);

            LatchSubscriberActor.LatchActorContext ctx1 =
                    new LatchSubscriberActor.LatchActorContext("independent-sub-a", latch1);
            LatchSubscriberActor.LatchActorContext ctx2 =
                    new LatchSubscriberActor.LatchActorContext("independent-sub-b", latch2);

            SpringActorRef<TestMessage> subscriber1 =
                    actorSystem.actor(LatchSubscriberActor.class).withContext(ctx1).spawnAndWait();
            SpringActorRef<TestMessage> subscriber2 =
                    actorSystem.actor(LatchSubscriberActor.class).withContext(ctx2).spawnAndWait();

            // Subscribe to different topics
            manager1.tell(new TopicManagerActor.Subscribe(subscriber1));
            manager2.tell(new TopicManagerActor.Subscribe(subscriber2));
            Thread.sleep(100);

            // Publish to topic 1 only
            manager1.tell(new TopicManagerActor.PublishTextMessage(new TextMessage("Only for topic A")));

            // Publish to topic 2 only
            manager2.tell(new TopicManagerActor.PublishTextMessage(new TextMessage("Only for topic B")));

            // Wait for delivery
            assertTrue(latch1.await(5, TimeUnit.SECONDS));
            assertTrue(latch2.await(5, TimeUnit.SECONDS));

            // Verify correct messages received
            assertEquals(1, ctx1.receivedMessages.size());
            assertEquals("Only for topic A", ((TextMessage) ctx1.receivedMessages.get(0)).content);

            assertEquals(1, ctx2.receivedMessages.size());
            assertEquals("Only for topic B", ((TextMessage) ctx2.receivedMessages.get(0)).content);
        }
    }

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(
            properties = {
                    "spring.actor.pekko.loglevel=INFO",
                    "spring.actor.pekko.actor.provider=local"
            })
    class SystemLevelTopicTest {

        @Test
        void canCreateSystemLevelTopics(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Test create()
            SpringTopicRef<TestMessage> topic1 = actorSystem
                    .topic(TestMessage.class)
                    .withName("system-topic-1")
                    .create();

            assertThat(topic1).isNotNull();
            assertThat(topic1.getTopicName()).isEqualTo("system-topic-1");

            // Test getOrCreate()
            SpringTopicRef<TestMessage> topic2 = actorSystem
                    .topic(TestMessage.class)
                    .withName("system-topic-2")
                    .getOrCreate();

            assertThat(topic2).isNotNull();
            assertThat(topic2.getTopicName()).isEqualTo("system-topic-2");
        }

        @Test
        void unsubscribeFromSystemLevelTopic(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create system-level topic
            SpringTopicRef<TestMessage> topic = actorSystem
                    .topic(TestMessage.class)
                    .withName("system-unsub-topic")
                    .create();

            // Create subscriber
            CountDownLatch latch = new CountDownLatch(1);
            LatchSubscriberActor.LatchActorContext ctx =
                    new LatchSubscriberActor.LatchActorContext("system-unsub-subscriber", latch);
            SpringActorRef<TestMessage> subscriber =
                    actorSystem.actor(LatchSubscriberActor.class).withContext(ctx).spawnAndWait();

            // Subscribe and receive first message
            topic.subscribe(subscriber);
            Thread.sleep(100);

            topic.publish(new TextMessage("Message 1"));
            latch.await(1, TimeUnit.SECONDS);

            assertEquals(1, ctx.receivedMessages.size());

            // Unsubscribe
            topic.unsubscribe(subscriber);
            Thread.sleep(100);

            // Publish second message - should NOT be received
            topic.publish(new TextMessage("Message 2"));
            Thread.sleep(500);

            // Should still only have first message
            assertEquals(1, ctx.receivedMessages.size());
            assertEquals("Message 1", ((TextMessage) ctx.receivedMessages.get(0)).content);
        }

        @Test
        void throwsExceptionForInvalidTopicNames(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Should throw when withName() is not called
            assertThrows(IllegalStateException.class, () -> {
                actorSystem.topic(TestMessage.class).create();
            });

            // Should throw when topic name is empty
            assertThrows(IllegalStateException.class, () -> {
                actorSystem.topic(TestMessage.class).withName("").create();
            });
        }

        @Test
        void throwsExceptionWhenCreatingDuplicateSystemTopic(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create first topic
            actorSystem
                    .topic(TestMessage.class)
                    .withName("duplicate-topic")
                    .create();

            // Trying to create second topic with same name should fail
            assertThrows(InvalidActorNameException.class, () -> {
                actorSystem
                        .topic(TestMessage.class)
                        .withName("duplicate-topic")
                        .create();
            });
        }
    }
}
