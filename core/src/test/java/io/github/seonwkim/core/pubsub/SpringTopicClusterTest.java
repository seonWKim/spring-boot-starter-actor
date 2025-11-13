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
 *   <li>Topics can be created from actors using SpringBehaviorContext</li>
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
     * Actor that manages topics - creates them from SpringBehaviorContext.
     * This actor can be deployed on any cluster node.
     */
    @Component
    public static class TopicManagerActor
            implements SpringActorWithContext<TopicManagerActor.Command, TopicManagerActor.ManagerContext> {

        public interface Command extends JsonSerializable {}

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
            public final SpringActorRef<TopicTestMessage> subscriber;

            @JsonCreator
            public Subscribe(@JsonProperty("subscriber") SpringActorRef<TopicTestMessage> subscriber) {
                this.subscriber = subscriber;
            }
        }

        public static class Unsubscribe implements Command {
            public final SpringActorRef<TopicTestMessage> subscriber;

            @JsonCreator
            public Unsubscribe(@JsonProperty("subscriber") SpringActorRef<TopicTestMessage> subscriber) {
                this.subscriber = subscriber;
            }
        }

        public static class GetTopicRef extends AskCommand<SpringTopicRef<TopicTestMessage>> implements Command {
            @JsonCreator
            public GetTopicRef() {}
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
            private SpringTopicRef<TopicTestMessage> topic;

            ManagerBehavior(SpringBehaviorContext<Command> ctx) {
                this.ctx = ctx;
            }

            private org.apache.pekko.actor.typed.Behavior<Command> onCreateTopic(CreateTopic msg) {
                topic = ctx.createTopic(TopicTestMessage.class, msg.topicName);
                ctx.getLog().info("Created topic: {}", msg.topicName);
                return Behaviors.same();
            }

            private org.apache.pekko.actor.typed.Behavior<Command> onPublishTextMessage(
                    PublishTextMessage msg) {
                if (topic != null) {
                    topic.publish(msg.message);
                }
                return Behaviors.same();
            }

            private org.apache.pekko.actor.typed.Behavior<Command> onPublishNumberMessage(
                    PublishNumberMessage msg) {
                if (topic != null) {
                    topic.publish(msg.message);
                }
                return Behaviors.same();
            }

            private org.apache.pekko.actor.typed.Behavior<Command> onSubscribe(Subscribe msg) {
                if (topic != null) {
                    topic.subscribe(msg.subscriber);
                }
                return Behaviors.same();
            }

            private org.apache.pekko.actor.typed.Behavior<Command> onUnsubscribe(Unsubscribe msg) {
                if (topic != null) {
                    topic.unsubscribe(msg.subscriber);
                }
                return Behaviors.same();
            }

            private org.apache.pekko.actor.typed.Behavior<Command> onGetTopicRef(GetTopicRef msg) {
                msg.reply(topic);
                return Behaviors.same();
            }
        }
    }

    /**
     * Test subscriber actor that collects received messages.
     * Uses a custom context to hold the message list and latch.
     */
    @Component
    public static class MessageCollectorActor
            implements SpringActorWithContext<TopicTestMessage, MessageCollectorActor.CollectorContext> {

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
        public SpringActorBehavior<TopicTestMessage> create(CollectorContext context) {
            return SpringActorBehavior.builder(TopicTestMessage.class, context)
                    .onMessage(TextMessage.class, (ctx, msg) -> {
                        context.receivedMessages.add(msg);
                        context.latch.countDown();
                        ctx.getLog().info("Actor {} received text message: {}", context.actorId(), msg.content);
                        return Behaviors.same();
                    })
                    .onMessage(NumberMessage.class, (ctx, msg) -> {
                        context.receivedMessages.add(msg);
                        context.latch.countDown();
                        ctx.getLog()
                                .info(
                                        "Actor {} received number message: {}",
                                        context.actorId(),
                                        msg.value);
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

        // Create topic manager on node 1
        SpringActorRef<TopicManagerActor.Command> manager = system1
                .actor(TopicManagerActor.class)
                .withContext(new TopicManagerActor.ManagerContext("topic-manager-1"))
                .spawnAndWait();

        // Create topic
        manager.tell(new TopicManagerActor.CreateTopic("cluster-test-topic"));
        Thread.sleep(1000);

        // Create subscribers on all 3 nodes
        int expectedMessages = 3; // We'll publish 3 messages
        MessageCollectorActor.CollectorContext context1 =
                new MessageCollectorActor.CollectorContext("subscriber-node-1", expectedMessages);
        MessageCollectorActor.CollectorContext context2 =
                new MessageCollectorActor.CollectorContext("subscriber-node-2", expectedMessages);
        MessageCollectorActor.CollectorContext context3 =
                new MessageCollectorActor.CollectorContext("subscriber-node-3", expectedMessages);

        SpringActorRef<TopicTestMessage> subscriber1 = system1
                .actor(MessageCollectorActor.class)
                .withContext(context1)
                .spawnAndWait();

        SpringActorRef<TopicTestMessage> subscriber2 = system2
                .actor(MessageCollectorActor.class)
                .withContext(context2)
                .spawnAndWait();

        SpringActorRef<TopicTestMessage> subscriber3 = system3
                .actor(MessageCollectorActor.class)
                .withContext(context3)
                .spawnAndWait();

        System.out.println("Subscribers created on all nodes");

        // Subscribe all actors to the topic
        manager.tell(new TopicManagerActor.Subscribe(subscriber1));
        manager.tell(new TopicManagerActor.Subscribe(subscriber2));
        manager.tell(new TopicManagerActor.Subscribe(subscriber3));

        // Give subscriptions time to propagate across cluster
        Thread.sleep(2000);
        System.out.println("Subscriptions propagated");

        // Publish messages from node 1
        manager.tell(new TopicManagerActor.PublishTextMessage(new TextMessage("Hello from node 1")));
        manager.tell(new TopicManagerActor.PublishNumberMessage(new NumberMessage(42)));
        manager.tell(new TopicManagerActor.PublishTextMessage(new TextMessage("Distributed pub/sub works!")));

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

        // Create two topic managers with different topics
        SpringActorRef<TopicManagerActor.Command> manager1 = system1
                .actor(TopicManagerActor.class)
                .withContext(new TopicManagerActor.ManagerContext("manager-topic-1"))
                .spawnAndWait();

        SpringActorRef<TopicManagerActor.Command> manager2 = system1
                .actor(TopicManagerActor.class)
                .withContext(new TopicManagerActor.ManagerContext("manager-topic-2"))
                .spawnAndWait();

        // Create different topics
        manager1.tell(new TopicManagerActor.CreateTopic("cluster-topic-1"));
        manager2.tell(new TopicManagerActor.CreateTopic("cluster-topic-2"));
        Thread.sleep(1000);

        // Create subscribers
        MessageCollectorActor.CollectorContext context1Topic1 =
                new MessageCollectorActor.CollectorContext("sub1-topic1", 1);
        MessageCollectorActor.CollectorContext context2Topic2 =
                new MessageCollectorActor.CollectorContext("sub2-topic2", 1);

        SpringActorRef<TopicTestMessage> subscriberTopic1 = system1
                .actor(MessageCollectorActor.class)
                .withContext(context1Topic1)
                .spawnAndWait();

        SpringActorRef<TopicTestMessage> subscriberTopic2 = system2
                .actor(MessageCollectorActor.class)
                .withContext(context2Topic2)
                .spawnAndWait();

        // Subscribe to different topics
        manager1.tell(new TopicManagerActor.Subscribe(subscriberTopic1));
        manager2.tell(new TopicManagerActor.Subscribe(subscriberTopic2));

        Thread.sleep(1000); // Wait for subscriptions

        // Publish to topic 1 only
        manager1.tell(new TopicManagerActor.PublishTextMessage(new TextMessage("Only for topic 1")));

        // Publish to topic 2 only
        manager2.tell(new TopicManagerActor.PublishTextMessage(new TextMessage("Only for topic 2")));

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

        // Create topic manager
        SpringActorRef<TopicManagerActor.Command> manager = system1
                .actor(TopicManagerActor.class)
                .withContext(new TopicManagerActor.ManagerContext("manager-unsub"))
                .spawnAndWait();

        manager.tell(new TopicManagerActor.CreateTopic("unsubscribe-test-topic"));
        Thread.sleep(1000);

        // Create subscribers on different nodes - expect 1 message each initially
        MessageCollectorActor.CollectorContext context1 =
                new MessageCollectorActor.CollectorContext("subscriber1", 1);
        MessageCollectorActor.CollectorContext context2 =
                new MessageCollectorActor.CollectorContext("subscriber2", 2); // Expects 2 messages

        SpringActorRef<TopicTestMessage> subscriber1 = system1
                .actor(MessageCollectorActor.class)
                .withContext(context1)
                .spawnAndWait();

        SpringActorRef<TopicTestMessage> subscriber2 = system2
                .actor(MessageCollectorActor.class)
                .withContext(context2)
                .spawnAndWait();

        manager.tell(new TopicManagerActor.Subscribe(subscriber1));
        manager.tell(new TopicManagerActor.Subscribe(subscriber2));

        Thread.sleep(1000);

        // Publish first message - both should receive
        manager.tell(new TopicManagerActor.PublishTextMessage(new TextMessage("Message 1")));

        assertTrue(context1.latch.await(5, TimeUnit.SECONDS), "Subscriber 1 should receive first message");

        // Unsubscribe subscriber1
        manager.tell(new TopicManagerActor.Unsubscribe(subscriber1));
        Thread.sleep(1000); // Wait for unsubscribe to propagate

        // Publish second message - only subscriber2 should receive
        manager.tell(new TopicManagerActor.PublishTextMessage(new TextMessage("Message 2")));

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
