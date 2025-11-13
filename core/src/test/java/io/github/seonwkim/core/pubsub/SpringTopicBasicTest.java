package io.github.seonwkim.core.pubsub;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.*;
import io.github.seonwkim.core.serialization.JsonSerializable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

/**
 * Basic tests for pub/sub topics using SpringBehaviorContext.
 *
 * <p>Topics are created from within actors using {@link SpringBehaviorContext#createTopic(Class, String)}.
 */
@SpringBootTest(classes = SpringTopicBasicTest.TestApp.class)
@TestPropertySource(
        properties = {
            "spring.actor.pekko.loglevel=INFO",
            "spring.actor.pekko.actor.provider=local"
        })
class SpringTopicBasicTest {

    @SpringBootApplication(scanBasePackages = "io.github.seonwkim.core")
    static class TestApp {}

    @Autowired private SpringActorSystem actorSystem;

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

    /**
     * Actor that creates a topic and allows publish/subscribe operations.
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

        public static class PublishMessage implements Command {
            public final TopicTestMessage message;

            @JsonCreator
            public PublishMessage(@JsonProperty("message") TopicTestMessage message) {
                this.message = message;
            }
        }

        public static class SubscribeActor implements Command {
            public final SpringActorRef<TopicTestMessage> subscriber;

            @JsonCreator
            public SubscribeActor(@JsonProperty("subscriber") SpringActorRef<TopicTestMessage> subscriber) {
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
                    .onMessage(PublishMessage.class, ManagerBehavior::onPublish)
                    .onMessage(SubscribeActor.class, ManagerBehavior::onSubscribe)
                    .onMessage(GetTopicRef.class, ManagerBehavior::onGetTopicRef)
                    .build();
        }

        private static class ManagerBehavior {
            private final SpringBehaviorContext<Command> ctx;
            private SpringTopicRef<TopicTestMessage> topic;

            ManagerBehavior(SpringBehaviorContext<Command> ctx) {
                this.ctx = ctx;
            }

            private Behavior<Command> onCreateTopic(CreateTopic msg) {
                topic = ctx.createTopic(TopicTestMessage.class, msg.topicName);
                ctx.getLog().info("Created topic: {}", msg.topicName);
                return Behaviors.same();
            }

            private Behavior<Command> onPublish(PublishMessage msg) {
                if (topic != null) {
                    topic.publish(msg.message);
                }
                return Behaviors.same();
            }

            private Behavior<Command> onSubscribe(SubscribeActor msg) {
                if (topic != null) {
                    topic.subscribe(msg.subscriber);
                }
                return Behaviors.same();
            }

            private Behavior<Command> onGetTopicRef(GetTopicRef msg) {
                msg.reply(topic);
                return Behaviors.same();
            }
        }
    }

    /**
     * Simple subscriber actor that collects received messages.
     */
    @Component
    public static class SubscriberActor
            implements SpringActorWithContext<TopicTestMessage, SubscriberActor.SubscriberContext> {

        public static class SubscriberContext extends SpringActorContext {
            private final String actorId;
            public final List<TopicTestMessage> receivedMessages = new CopyOnWriteArrayList<>();
            public final CountDownLatch latch;

            public SubscriberContext(String actorId, int expectedMessages) {
                this.actorId = actorId;
                this.latch = new CountDownLatch(expectedMessages);
            }

            @Override
            public String actorId() {
                return actorId;
            }
        }

        @Override
        public SpringActorBehavior<TopicTestMessage> create(SubscriberContext context) {
            return SpringActorBehavior.builder(TopicTestMessage.class, context)
                    .onMessage(TextMessage.class, (ctx, msg) -> {
                        context.receivedMessages.add(msg);
                        context.latch.countDown();
                        ctx.getLog().info("Received message: {}", msg.content);
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    @Test
    void canCreateTopicFromBehaviorContext() throws Exception {
        // Spawn manager actor
        SpringActorRef<TopicManagerActor.Command> manager = actorSystem
                .actor(TopicManagerActor.class)
                .withContext(new TopicManagerActor.ManagerContext("manager-1"))
                .spawnAndWait();

        // Create a topic
        manager.tell(new TopicManagerActor.CreateTopic("test-topic"));

        Thread.sleep(500); // Give time for topic creation

        // Verify topic was created by getting reference
        SpringTopicRef<TopicTestMessage> topicRef =
                manager.ask(new TopicManagerActor.GetTopicRef()).execute().toCompletableFuture().get();

        assertNotNull(topicRef);
        assertEquals("test-topic", topicRef.getTopicName());
    }

    @Test
    void publishSubscribeWorks() throws Exception {
        // Spawn manager
        SpringActorRef<TopicManagerActor.Command> manager = actorSystem
                .actor(TopicManagerActor.class)
                .withContext(new TopicManagerActor.ManagerContext("manager-2"))
                .spawnAndWait();

        // Create subscriber
        SubscriberActor.SubscriberContext subscriberCtx =
                new SubscriberActor.SubscriberContext("subscriber-1", 2);
        SpringActorRef<TopicTestMessage> subscriber = actorSystem
                .actor(SubscriberActor.class)
                .withContext(subscriberCtx)
                .spawnAndWait();

        // Create topic and subscribe
        manager.tell(new TopicManagerActor.CreateTopic("pub-sub-topic"));
        Thread.sleep(500);

        manager.tell(new TopicManagerActor.SubscribeActor(subscriber));
        Thread.sleep(500);

        // Publish messages
        manager.tell(new TopicManagerActor.PublishMessage(new TextMessage("Hello")));
        manager.tell(new TopicManagerActor.PublishMessage(new TextMessage("World")));

        // Wait for messages
        boolean received = subscriberCtx.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Subscriber should receive messages");

        assertEquals(2, subscriberCtx.receivedMessages.size());
        assertEquals(new TextMessage("Hello"), subscriberCtx.receivedMessages.get(0));
        assertEquals(new TextMessage("World"), subscriberCtx.receivedMessages.get(1));
    }

    @Test
    void multipleSubscribersReceiveMessages() throws Exception {
        // Spawn manager
        SpringActorRef<TopicManagerActor.Command> manager = actorSystem
                .actor(TopicManagerActor.class)
                .withContext(new TopicManagerActor.ManagerContext("manager-3"))
                .spawnAndWait();

        // Create multiple subscribers
        SubscriberActor.SubscriberContext sub1Ctx = new SubscriberActor.SubscriberContext("sub1", 1);
        SubscriberActor.SubscriberContext sub2Ctx = new SubscriberActor.SubscriberContext("sub2", 1);

        SpringActorRef<TopicTestMessage> subscriber1 =
                actorSystem.actor(SubscriberActor.class).withContext(sub1Ctx).spawnAndWait();

        SpringActorRef<TopicTestMessage> subscriber2 =
                actorSystem.actor(SubscriberActor.class).withContext(sub2Ctx).spawnAndWait();

        // Create topic and subscribe both
        manager.tell(new TopicManagerActor.CreateTopic("multi-sub-topic"));
        Thread.sleep(500);

        manager.tell(new TopicManagerActor.SubscribeActor(subscriber1));
        manager.tell(new TopicManagerActor.SubscribeActor(subscriber2));
        Thread.sleep(500);

        // Publish one message
        manager.tell(new TopicManagerActor.PublishMessage(new TextMessage("Broadcast")));

        // Both should receive
        assertTrue(sub1Ctx.latch.await(5, TimeUnit.SECONDS));
        assertTrue(sub2Ctx.latch.await(5, TimeUnit.SECONDS));

        assertEquals(1, sub1Ctx.receivedMessages.size());
        assertEquals(1, sub2Ctx.receivedMessages.size());
        assertEquals(new TextMessage("Broadcast"), sub1Ctx.receivedMessages.get(0));
        assertEquals(new TextMessage("Broadcast"), sub2Ctx.receivedMessages.get(0));
    }
}
