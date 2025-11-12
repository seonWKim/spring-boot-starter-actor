package io.github.seonwkim.core.pubsub;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.*;
import io.github.seonwkim.core.serialization.JsonSerializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
    public interface TestMessage extends JsonSerializable {}

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

            org.apache.pekko.actor.typed.Behavior<TestMessage> onTextMessage(TextMessage msg) {
                receivedMessages.add(msg);
                return Behaviors.same();
            }

            org.apache.pekko.actor.typed.Behavior<TestMessage> onNumberMessage(NumberMessage msg) {
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

        public interface Command extends JsonSerializable {}

        public static class GetReceivedMessages extends AskCommand<List<TestMessage>> implements Command {
            @JsonCreator
            public GetReceivedMessages() {}
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

            org.apache.pekko.actor.typed.Behavior<Command> onTextMessage(TextMessageCommand msg) {
                receivedMessages.add(new TextMessage(msg.content));
                return Behaviors.same();
            }

            org.apache.pekko.actor.typed.Behavior<Command> onNumberMessage(NumberMessageCommand msg) {
                receivedMessages.add(new NumberMessage(msg.value));
                return Behaviors.same();
            }

            org.apache.pekko.actor.typed.Behavior<Command> onGetReceivedMessages(GetReceivedMessages msg) {
                msg.reply(new ArrayList<>(receivedMessages));
                return Behaviors.same();
            }
        }
    }

    // Test actor that uses a latch to signal message receipt
    @Component
    static class LatchSubscriberActor
            implements SpringActorWithContext<
                    LatchSubscriberActor.Command, LatchSubscriberActor.LatchActorContext> {

        public interface Command extends JsonSerializable {}

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
        public SpringActorBehavior<Command> create(LatchActorContext actorContext) {
            return SpringActorBehavior.builder(Command.class, actorContext)
                    .onMessage(TextMessageCommand.class, (ctx, msg) -> {
                        actorContext.receivedMessages.add(new TextMessage(msg.content));
                        actorContext.latch.countDown();
                        return Behaviors.same();
                    })
                    .onMessage(NumberMessageCommand.class, (ctx, msg) -> {
                        actorContext.receivedMessages.add(new NumberMessage(msg.value));
                        actorContext.latch.countDown();
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {}

    @Nested
    @SpringBootTest(classes = TestApp.class)
    @TestPropertySource(
            properties = {
                "spring.actor.pekko.loglevel=INFO",
                "spring.actor.pekko.actor.provider=local"
            })
    class BasicPubSubTest {

        @Test
        void createTopicWithBuilder(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create a topic using the builder
            SpringTopicRef<TestMessage> topic =
                    actorSystem.topic(TestMessage.class).withName("test-topic").getOrCreate();

            assertThat(topic).isNotNull();
            assertThat(topic.getTopicName()).isEqualTo("test-topic");
        }

        @Test
        void getOrCreateIsIdempotent(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create the same topic multiple times
            SpringTopicRef<TestMessage> topic1 =
                    actorSystem.topic(TestMessage.class).withName("idempotent-topic").getOrCreate();
            SpringTopicRef<TestMessage> topic2 =
                    actorSystem.topic(TestMessage.class).withName("idempotent-topic").getOrCreate();

            assertThat(topic1).isNotNull();
            assertThat(topic2).isNotNull();
            assertThat(topic1.getTopicName()).isEqualTo(topic2.getTopicName());
        }

        @Test
        void publishAndSubscribeSingleMessage(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create topic
            SpringTopicRef<LatchSubscriberActor.Command> topic = actorSystem
                    .topic(LatchSubscriberActor.Command.class)
                    .withName("single-message-topic")
                    .getOrCreate();

            // Create subscriber actor with latch
            CountDownLatch latch = new CountDownLatch(1);
            LatchSubscriberActor.LatchActorContext actorContext =
                    new LatchSubscriberActor.LatchActorContext("subscriber-1", latch);
            SpringActorRef<LatchSubscriberActor.Command> subscriber = actorSystem
                    .actor(LatchSubscriberActor.class)
                    .withContext(actorContext)
                    .spawnAndWait();

            // Subscribe
            topic.subscribe(subscriber);

            // Give subscription time to propagate
            Thread.sleep(100);

            // Publish message
            topic.publish(new LatchSubscriberActor.TextMessageCommand("Hello, World!"));

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

            // Create topic
            SpringTopicRef<LatchSubscriberActor.Command> topic = actorSystem
                    .topic(LatchSubscriberActor.Command.class)
                    .withName("multiple-subscribers-topic")
                    .getOrCreate();

            // Create multiple subscriber actors
            int subscriberCount = 3;
            CountDownLatch latch = new CountDownLatch(subscriberCount);
            List<LatchSubscriberActor.LatchActorContext> contexts = new ArrayList<>();

            for (int i = 0; i < subscriberCount; i++) {
                LatchSubscriberActor.LatchActorContext actorContext =
                        new LatchSubscriberActor.LatchActorContext("subscriber-" + i, latch);
                contexts.add(actorContext);

                SpringActorRef<LatchSubscriberActor.Command> subscriber = actorSystem
                        .actor(LatchSubscriberActor.class)
                        .withContext(actorContext)
                        .spawnAndWait();

                topic.subscribe(subscriber);
            }

            // Give subscriptions time to propagate
            Thread.sleep(100);

            // Publish a single message
            topic.publish(new LatchSubscriberActor.TextMessageCommand("Broadcast message"));

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

            // Create topic
            SpringTopicRef<LatchSubscriberActor.Command> topic = actorSystem
                    .topic(LatchSubscriberActor.Command.class)
                    .withName("unsubscribe-topic")
                    .getOrCreate();

            // Create subscriber
            CountDownLatch latch = new CountDownLatch(1);
            LatchSubscriberActor.LatchActorContext actorContext =
                    new LatchSubscriberActor.LatchActorContext("subscriber", latch);
            SpringActorRef<LatchSubscriberActor.Command> subscriber = actorSystem
                    .actor(LatchSubscriberActor.class)
                    .withContext(actorContext)
                    .spawnAndWait();

            // Subscribe and receive first message
            topic.subscribe(subscriber);
            Thread.sleep(100);

            topic.publish(new LatchSubscriberActor.TextMessageCommand("Message 1"));
            latch.await(5, TimeUnit.SECONDS);

            assertEquals(1, actorContext.receivedMessages.size());

            // Unsubscribe
            topic.unsubscribe(subscriber);
            Thread.sleep(100);

            // Publish second message - should NOT be received
            topic.publish(new LatchSubscriberActor.TextMessageCommand("Message 2"));
            Thread.sleep(500); // Wait to ensure message would have been delivered

            // Should still only have the first message
            assertEquals(1, actorContext.receivedMessages.size());
            assertEquals("Message 1", ((TextMessage) actorContext.receivedMessages.get(0)).content);
        }

        @Test
        void publishMultipleMessagesToMultipleSubscribers(ApplicationContext context) throws Exception {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create topic
            SpringTopicRef<LatchSubscriberActor.Command> topic = actorSystem
                    .topic(LatchSubscriberActor.Command.class)
                    .withName("multiple-messages-topic")
                    .getOrCreate();

            // Create subscribers
            int subscriberCount = 2;
            int messageCount = 5;
            CountDownLatch latch = new CountDownLatch(subscriberCount * messageCount);
            List<LatchSubscriberActor.LatchActorContext> contexts = new ArrayList<>();

            for (int i = 0; i < subscriberCount; i++) {
                LatchSubscriberActor.LatchActorContext actorContext =
                        new LatchSubscriberActor.LatchActorContext("subscriber-" + i, latch);
                contexts.add(actorContext);

                SpringActorRef<LatchSubscriberActor.Command> subscriber = actorSystem
                        .actor(LatchSubscriberActor.class)
                        .withContext(actorContext)
                        .spawnAndWait();

                topic.subscribe(subscriber);
            }

            Thread.sleep(100);

            // Publish multiple messages
            for (int i = 0; i < messageCount; i++) {
                topic.publish(new LatchSubscriberActor.NumberMessageCommand(i));
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
        void topicWithDifferentNames(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Create two different topics
            SpringTopicRef<TestMessage> topic1 =
                    actorSystem.topic(TestMessage.class).withName("topic-1").getOrCreate();
            SpringTopicRef<TestMessage> topic2 =
                    actorSystem.topic(TestMessage.class).withName("topic-2").getOrCreate();

            assertThat(topic1.getTopicName()).isEqualTo("topic-1");
            assertThat(topic2.getTopicName()).isEqualTo("topic-2");
            assertThat(topic1.getTopicName()).isNotEqualTo(topic2.getTopicName());
        }

        @Test
        void getMethodWorksSameAsGetOrCreate(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // get() should work the same as getOrCreate()
            SpringTopicRef<TestMessage> topic1 =
                    actorSystem.topic(TestMessage.class).withName("get-test-topic").get();
            SpringTopicRef<TestMessage> topic2 =
                    actorSystem.topic(TestMessage.class).withName("get-test-topic").getOrCreate();

            assertThat(topic1).isNotNull();
            assertThat(topic2).isNotNull();
            assertThat(topic1.getTopicName()).isEqualTo(topic2.getTopicName());
        }

        @Test
        void throwsExceptionWhenTopicNameNotSet(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            // Try to create topic without setting name
            assertThrows(IllegalStateException.class, () -> actorSystem.topic(TestMessage.class).getOrCreate());
        }

        @Test
        void throwsExceptionWhenSubscriberIsNull(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            SpringTopicRef<TestMessage> topic =
                    actorSystem.topic(TestMessage.class).withName("null-subscriber-topic").getOrCreate();

            assertThrows(IllegalArgumentException.class, () -> topic.subscribe(null));
        }

        @Test
        void throwsExceptionWhenUnsubscriberIsNull(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            SpringTopicRef<TestMessage> topic =
                    actorSystem.topic(TestMessage.class).withName("null-unsubscriber-topic").getOrCreate();

            assertThrows(IllegalArgumentException.class, () -> topic.unsubscribe(null));
        }

        @Test
        void throwsExceptionWhenPublishingNull(ApplicationContext context) {
            SpringActorSystem actorSystem = context.getBean(SpringActorSystem.class);

            SpringTopicRef<TestMessage> topic =
                    actorSystem.topic(TestMessage.class).withName("null-publish-topic").getOrCreate();

            assertThrows(IllegalArgumentException.class, () -> topic.publish(null));
        }
    }
}
