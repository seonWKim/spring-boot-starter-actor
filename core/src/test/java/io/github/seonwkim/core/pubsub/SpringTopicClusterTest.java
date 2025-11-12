package io.github.seonwkim.core.pubsub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.*;
import io.github.seonwkim.core.serialization.JsonSerializable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

/**
 * Cluster test for pub/sub topics. Verifies that topics work correctly across cluster nodes.
 *
 * <p>This test spawns a 3-node cluster and verifies:
 * <ul>
 *   <li>Topics can be created and accessed from any node</li>
 *   <li>Messages published from one node are received by subscribers on other nodes</li>
 *   <li>Multiple subscribers across different nodes all receive published messages</li>
 * </ul>
 */
public class SpringTopicClusterTest extends AbstractClusterTest {

    /** Test message types */
    public interface TopicTestMessage extends JsonSerializable {}

    public static class TextMessage implements TopicTestMessage {
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

    public static class NumberMessage implements TopicTestMessage {
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
     * Test subscriber actor that collects received messages.
     * Uses a custom context to hold the message list and latch.
     */
    @Component
    public static class MessageCollectorActor
            implements SpringActorWithContext<
                    MessageCollectorActor.Command, MessageCollectorActor.CollectorContext> {

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

        public static class GetReceivedMessages extends AskCommand<List<TopicTestMessage>> implements Command {
            @JsonCreator
            public GetReceivedMessages() {}
        }

        public static class CollectorContext extends SpringActorContext {
            private final String actorId;
            public final List<TopicTestMessage> receivedMessages;
            public final CountDownLatch latch;

            public CollectorContext(String actorId, int expectedMessages) {
                this.actorId = actorId;
                this.receivedMessages = new CopyOnWriteArrayList<>();
                this.latch = new CountDownLatch(expectedMessages);
            }

            @Override
            public String actorId() {
                return actorId;
            }
        }

        @Override
        public SpringActorBehavior<Command> create(CollectorContext context) {
            return SpringActorBehavior.builder(Command.class, context)
                    .onMessage(TextMessageCommand.class, (ctx, msg) -> {
                        context.receivedMessages.add(new TextMessage(msg.content));
                        context.latch.countDown();
                        ctx.getLog().info("Actor {} received text message: {}", context.actorId(), msg.content);
                        return Behaviors.same();
                    })
                    .onMessage(NumberMessageCommand.class, (ctx, msg) -> {
                        context.receivedMessages.add(new NumberMessage(msg.value));
                        context.latch.countDown();
                        ctx.getLog()
                                .info(
                                        "Actor {} received number message: {}",
                                        context.actorId(),
                                        msg.value);
                        return Behaviors.same();
                    })
                    .onMessage(GetReceivedMessages.class, (ctx, msg) -> {
                        msg.reply(Collections.unmodifiableList(context.receivedMessages));
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    public static class TopicClusterTestApp {}

    @Override
    protected Class<?> getApplicationClass() {
        return TopicClusterTestApp.class;
    }

    @Test
    void topicPublishReachesSubscribersAcrossCluster() throws Exception {
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);
        SpringActorSystem system3 = context3.getBean(SpringActorSystem.class);

        waitUntilClusterInitialized();
        System.out.println("Cluster initialized - starting pub/sub test");

        // Create topic on node 1
        SpringTopicRef<MessageCollectorActor.Command> topic = system1
                .topic(MessageCollectorActor.Command.class)
                .withName("cluster-test-topic")
                .getOrCreate();

        // Create subscribers on all 3 nodes
        int expectedMessages = 3; // We'll publish 3 messages
        MessageCollectorActor.CollectorContext context1 =
                new MessageCollectorActor.CollectorContext("subscriber-node-1", expectedMessages);
        MessageCollectorActor.CollectorContext context2 =
                new MessageCollectorActor.CollectorContext("subscriber-node-2", expectedMessages);
        MessageCollectorActor.CollectorContext context3 =
                new MessageCollectorActor.CollectorContext("subscriber-node-3", expectedMessages);

        SpringActorRef<MessageCollectorActor.Command> subscriber1 = system1
                .actor(MessageCollectorActor.class)
                .withContext(context1)
                .spawnAndWait();

        SpringActorRef<MessageCollectorActor.Command> subscriber2 = system2
                .actor(MessageCollectorActor.class)
                .withContext(context2)
                .spawnAndWait();

        SpringActorRef<MessageCollectorActor.Command> subscriber3 = system3
                .actor(MessageCollectorActor.class)
                .withContext(context3)
                .spawnAndWait();

        System.out.println("Subscribers created on all nodes");

        // Subscribe all actors to the topic
        topic.subscribe(subscriber1);
        topic.subscribe(subscriber2);
        topic.subscribe(subscriber3);

        // Give subscriptions time to propagate across cluster
        Thread.sleep(2000);
        System.out.println("Subscriptions propagated");

        // Publish messages from node 1
        topic.publish(new MessageCollectorActor.TextMessageCommand("Hello from node 1"));
        topic.publish(new MessageCollectorActor.NumberMessageCommand(42));
        topic.publish(new MessageCollectorActor.TextMessageCommand("Distributed pub/sub works!"));

        System.out.println("Messages published");

        // Wait for all subscribers to receive all messages
        boolean allReceived1 = context1.latch.await(10, TimeUnit.SECONDS);
        boolean allReceived2 = context2.latch.await(10, TimeUnit.SECONDS);
        boolean allReceived3 = context3.latch.await(10, TimeUnit.SECONDS);

        assertTrue(allReceived1, "Subscriber on node 1 should receive all messages");
        assertTrue(allReceived2, "Subscriber on node 2 should receive all messages");
        assertTrue(allReceived3, "Subscriber on node 3 should receive all messages");

        // Verify message counts
        assertEquals(3, context1.receivedMessages.size(), "Subscriber 1 should have 3 messages");
        assertEquals(3, context2.receivedMessages.size(), "Subscriber 2 should have 3 messages");
        assertEquals(3, context3.receivedMessages.size(), "Subscriber 3 should have 3 messages");

        System.out.println("All subscribers received all messages across cluster!");

        // Verify message content (order not guaranteed in pub/sub)
        assertTrue(
                context1.receivedMessages.contains(new TextMessage("Hello from node 1")),
                "Subscriber 1 should have first text message");
        assertTrue(
                context1.receivedMessages.contains(new NumberMessage(42)),
                "Subscriber 1 should have number message");
        assertTrue(
                context1.receivedMessages.contains(new TextMessage("Distributed pub/sub works!")),
                "Subscriber 1 should have second text message");
    }

    @Test
    void multipleTopicsWorkIndependentlyInCluster() throws Exception {
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);

        waitUntilClusterInitialized();

        // Create two different topics
        SpringTopicRef<MessageCollectorActor.Command> topic1 = system1
                .topic(MessageCollectorActor.Command.class)
                .withName("cluster-topic-1")
                .getOrCreate();

        SpringTopicRef<MessageCollectorActor.Command> topic2 = system1
                .topic(MessageCollectorActor.Command.class)
                .withName("cluster-topic-2")
                .getOrCreate();

        // Create subscribers
        MessageCollectorActor.CollectorContext context1Topic1 =
                new MessageCollectorActor.CollectorContext("sub1-topic1", 1);
        MessageCollectorActor.CollectorContext context2Topic2 =
                new MessageCollectorActor.CollectorContext("sub2-topic2", 1);

        SpringActorRef<MessageCollectorActor.Command> subscriberTopic1 = system1
                .actor(MessageCollectorActor.class)
                .withContext(context1Topic1)
                .spawnAndWait();

        SpringActorRef<MessageCollectorActor.Command> subscriberTopic2 = system2
                .actor(MessageCollectorActor.class)
                .withContext(context2Topic2)
                .spawnAndWait();

        // Subscribe to different topics
        topic1.subscribe(subscriberTopic1);
        topic2.subscribe(subscriberTopic2);

        Thread.sleep(1000); // Wait for subscriptions

        // Publish to topic 1 only
        topic1.publish(new MessageCollectorActor.TextMessageCommand("Only for topic 1"));

        // Publish to topic 2 only
        topic2.publish(new MessageCollectorActor.TextMessageCommand("Only for topic 2"));

        // Wait for delivery
        boolean received1 = context1Topic1.latch.await(5, TimeUnit.SECONDS);
        boolean received2 = context2Topic2.latch.await(5, TimeUnit.SECONDS);

        assertTrue(received1, "Subscriber to topic 1 should receive message");
        assertTrue(received2, "Subscriber to topic 2 should receive message");

        // Verify correct messages received
        assertEquals(1, context1Topic1.receivedMessages.size());
        assertEquals(
                "Only for topic 1",
                ((TextMessage) context1Topic1.receivedMessages.get(0)).content);

        assertEquals(1, context2Topic2.receivedMessages.size());
        assertEquals(
                "Only for topic 2",
                ((TextMessage) context2Topic2.receivedMessages.get(0)).content);
    }

    @Test
    void unsubscribeWorksAcrossCluster() throws Exception {
        SpringActorSystem system1 = context1.getBean(SpringActorSystem.class);
        SpringActorSystem system2 = context2.getBean(SpringActorSystem.class);

        waitUntilClusterInitialized();

        SpringTopicRef<MessageCollectorActor.Command> topic = system1
                .topic(MessageCollectorActor.Command.class)
                .withName("unsubscribe-test-topic")
                .getOrCreate();

        // Create subscribers on different nodes - expect 1 message each initially
        MessageCollectorActor.CollectorContext context1 =
                new MessageCollectorActor.CollectorContext("subscriber1", 1);
        MessageCollectorActor.CollectorContext context2 =
                new MessageCollectorActor.CollectorContext("subscriber2", 2); // Expects 2 messages

        SpringActorRef<MessageCollectorActor.Command> subscriber1 = system1
                .actor(MessageCollectorActor.class)
                .withContext(context1)
                .spawnAndWait();

        SpringActorRef<MessageCollectorActor.Command> subscriber2 = system2
                .actor(MessageCollectorActor.class)
                .withContext(context2)
                .spawnAndWait();

        topic.subscribe(subscriber1);
        topic.subscribe(subscriber2);

        Thread.sleep(1000);

        // Publish first message - both should receive
        topic.publish(new MessageCollectorActor.TextMessageCommand("Message 1"));

        assertTrue(context1.latch.await(5, TimeUnit.SECONDS), "Subscriber 1 should receive first message");

        // Unsubscribe subscriber1
        topic.unsubscribe(subscriber1);
        Thread.sleep(1000); // Wait for unsubscribe to propagate

        // Publish second message - only subscriber2 should receive
        topic.publish(new MessageCollectorActor.TextMessageCommand("Message 2"));

        // Wait for subscriber2 to receive both messages
        assertTrue(context2.latch.await(5, TimeUnit.SECONDS), "Subscriber 2 should receive both messages");

        // Give extra time to ensure subscriber1 doesn't receive the second message
        Thread.sleep(1000);

        // Subscriber1 should still have only 1 message
        assertEquals(
                1,
                context1.receivedMessages.size(),
                "Unsubscribed actor should not receive new messages");

        // Subscriber2 should have 2 messages
        assertEquals(
                2,
                context2.receivedMessages.size(),
                "Subscribed actor should receive both messages");
    }
}
