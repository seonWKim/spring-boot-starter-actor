package io.github.seonwkim.core;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Integration test for ActorTags functionality.
 * Tests that actors can be spawned with tags for logging and categorization.
 */
@SpringBootTest(classes = {ActorConfiguration.class, ActorTagsIntegrationTest.TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ActorTagsIntegrationTest {

    @Autowired
    private SpringActorSystem actorSystem;

    private TestLogAppender logAppender;

    @BeforeEach
    void setUp() {
        logAppender = new TestLogAppender();
        logAppender.start();
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (logAppender != null) {
            Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            rootLogger.detachAppender(logAppender);
            logAppender.stop();
            logAppender = null;
        }
    }

    /**
     * Test log appender that captures log events with their MDC context.
     */
    static class TestLogAppender extends AppenderBase<ILoggingEvent> {
        private final List<ILoggingEvent> events = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected void append(ILoggingEvent event) {
            events.add(event);
        }

        public List<ILoggingEvent> getEvents() {
            return new ArrayList<>(events);
        }

        public void clear() {
            events.clear();
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        public TestActor tagsTestActor() {
            return new TestActor();
        }

        @Bean
        public TestParentActor tagsTestParentActor() {
            return new TestParentActor();
        }

        @Bean
        public TestChildActor tagsTestChildActor() {
            return new TestChildActor();
        }
    }

    // Test message types
    public interface TestCommand extends FrameworkCommand {}

    public static class Ping extends AskCommand<Pong> implements TestCommand {
        public final String message;

        public Ping(String message) {
            this.message = message;
        }
    }

    public static class Pong {
        public final String message;
        public final String actorPath;

        public Pong(String message, String actorPath) {
            this.message = message;
            this.actorPath = actorPath;
        }
    }

    public static class TestActor implements SpringActorWithContext<TestCommand, SpringActorContext> {
        @Override
        public SpringActorBehavior<TestCommand> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestCommand.class, actorContext)
                    .onMessage(Ping.class, (ctx, msg) -> {
                        ctx.getLog().info("Received: {}", msg.message);
                        msg.reply(new Pong(
                                "Pong: " + msg.message, ctx.getSelf().path().toString()));
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    public static class TestParentActor implements SpringActorWithContext<TestCommand, SpringActorContext> {
        @Override
        public SpringActorBehavior<TestCommand> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestCommand.class, actorContext)
                    .onMessage(Ping.class, (ctx, msg) -> {
                        ctx.getLog().info("Parent received: {}", msg.message);
                        msg.reply(new Pong(
                                "Pong: " + msg.message, ctx.getSelf().path().toString()));
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    public static class TestChildActor implements SpringActorWithContext<TestCommand, SpringActorContext> {
        @Override
        public SpringActorBehavior<TestCommand> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestCommand.class, actorContext)
                    .onMessage(Ping.class, (ctx, msg) -> {
                        ctx.getLog().info("Child received: {}", msg.message);
                        msg.reply(new Pong(
                                "Pong: " + msg.message, ctx.getSelf().path().toString()));
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    private Pong sendPingAndWait(SpringActorRef<TestCommand> actor, String message) throws Exception {
        return actor.ask(new Ping(message)).execute().toCompletableFuture().get();
    }

    private void verifyTagsInLogs(String actorPath, String... expectedTags) {
        // Wait a bit for logs to be written
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<ILoggingEvent> events = logAppender.getEvents();
        boolean foundMatchingLog = false;

        for (ILoggingEvent event : events) {
            Map<String, String> mdc = event.getMDCPropertyMap();
            String pekkoSource = mdc.get("pekkoSource");

            if (pekkoSource != null && pekkoSource.contains(actorPath)) {
                String pekkoTags = mdc.get("pekkoTags");
                if (pekkoTags != null && !pekkoTags.isEmpty()) {
                    // Verify all expected tags are present
                    for (String expectedTag : expectedTags) {
                        assertTrue(
                                pekkoTags.contains(expectedTag),
                                String.format(
                                        "Expected tag '%s' not found in pekkoTags '%s' for actor %s",
                                        expectedTag, pekkoTags, actorPath));
                    }
                    foundMatchingLog = true;
                    break;
                }
            }
        }

        assertTrue(
                foundMatchingLog,
                String.format(
                        "No log entry found with tags for actor %s. Total log events: %d", actorPath, events.size()));
    }

    @Test
    void testEmptyTags() throws Exception {
        logAppender.clear();

        SpringActorRef<TestCommand> actor = actorSystem
                .actor(TestActor.class)
                .withId("empty-tags-actor")
                .withTags(TagsConfig.empty())
                .spawnAndWait();

        assertNotNull(actor);
        Pong response = sendPingAndWait(actor, "Hello with empty tags");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("empty-tags-actor"));
    }

    @Test
    void testSingleTag() throws Exception {
        logAppender.clear();

        SpringActorRef<TestCommand> actor = actorSystem
                .actor(TestActor.class)
                .withId("single-tag-actor")
                .withTags(TagsConfig.of("worker"))
                .spawnAndWait();

        assertNotNull(actor);
        Pong response = sendPingAndWait(actor, "Hello with single tag");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("single-tag-actor"));

        // Verify the tag appears in logs
        verifyTagsInLogs("single-tag-actor", "worker");
    }

    @Test
    void testMultipleTags() throws Exception {
        logAppender.clear();

        SpringActorRef<TestCommand> actor = actorSystem
                .actor(TestActor.class)
                .withId("multiple-tags-actor")
                .withTags(TagsConfig.of("worker", "high-priority", "cpu-intensive"))
                .spawnAndWait();

        assertNotNull(actor);
        Pong response = sendPingAndWait(actor, "Hello with multiple tags");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("multiple-tags-actor"));

        // Verify all tags appear in logs
        verifyTagsInLogs("multiple-tags-actor", "worker", "high-priority", "cpu-intensive");
    }

    @Test
    void testTagsFromSet() throws Exception {
        logAppender.clear();

        Set<String> tags = Set.of("worker", "backend");
        SpringActorRef<TestCommand> actor = actorSystem
                .actor(TestActor.class)
                .withId("tags-from-set-actor")
                .withTags(TagsConfig.of(tags))
                .spawnAndWait();

        assertNotNull(actor);
        Pong response = sendPingAndWait(actor, "Hello with tags from set");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("tags-from-set-actor"));

        // Verify tags appear in logs
        verifyTagsInLogs("tags-from-set-actor", "worker", "backend");
    }

    @Test
    void testCombinedWithMailbox() throws Exception {
        logAppender.clear();

        SpringActorRef<TestCommand> actor = actorSystem
                .actor(TestActor.class)
                .withId("tags-with-mailbox-actor")
                .withTags(TagsConfig.of("worker", "bounded"))
                .withMailbox(MailboxConfig.bounded(50))
                .spawnAndWait();

        assertNotNull(actor);
        Pong response = sendPingAndWait(actor, "Hello with tags and bounded mailbox");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("tags-with-mailbox-actor"));

        // Verify tags work with mailbox configuration
        verifyTagsInLogs("tags-with-mailbox-actor", "worker", "bounded");
    }

    @Test
    void testCombinedWithDispatcher() throws Exception {
        logAppender.clear();

        SpringActorRef<TestCommand> actor = actorSystem
                .actor(TestActor.class)
                .withId("tags-with-dispatcher-actor")
                .withTags(TagsConfig.of("worker", "blocking"))
                .withBlockingDispatcher()
                .spawnAndWait();

        assertNotNull(actor);
        Pong response = sendPingAndWait(actor, "Hello with tags and blocking dispatcher");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("tags-with-dispatcher-actor"));

        // Verify tags work with dispatcher configuration
        verifyTagsInLogs("tags-with-dispatcher-actor", "worker", "blocking");
    }

    @Test
    void testCombinedWithMailboxAndDispatcher() throws Exception {
        logAppender.clear();

        SpringActorRef<TestCommand> actor = actorSystem
                .actor(TestActor.class)
                .withId("tags-with-all-actor")
                .withTags(TagsConfig.of("worker", "high-priority", "io-bound"))
                .withMailbox(MailboxConfig.bounded(100))
                .withBlockingDispatcher()
                .spawnAndWait();

        assertNotNull(actor);
        Pong response = sendPingAndWait(actor, "Hello with tags, mailbox, and dispatcher");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("tags-with-all-actor"));

        // Verify tags work with both mailbox and dispatcher
        verifyTagsInLogs("tags-with-all-actor", "worker", "high-priority", "io-bound");
    }

    @Test
    void testChildActorWithTags() throws Exception {
        logAppender.clear();

        // Spawn parent actor
        SpringActorRef<TestCommand> parent = actorSystem
                .actor(TestParentActor.class)
                .withId("parent-with-tagged-child")
                .spawnAndWait();

        assertNotNull(parent);

        // Spawn child actor with tags
        CompletionStage<SpringActorRef<TestCommand>> childFuture = parent.child(TestChildActor.class)
                .withId("tagged-child")
                .withTags(TagsConfig.of("child-worker", "low-priority"))
                .spawn();

        SpringActorRef<TestCommand> child = childFuture.toCompletableFuture().get();
        assertNotNull(child);

        Pong response = sendPingAndWait(child, "Hello from tagged child");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("tagged-child"));

        // Verify child actor tags appear in logs
        verifyTagsInLogs("tagged-child", "child-worker", "low-priority");
    }

    @Test
    void testChildActorWithTagsAndMailbox() throws Exception {
        logAppender.clear();

        // Spawn parent actor
        SpringActorRef<TestCommand> parent = actorSystem
                .actor(TestParentActor.class)
                .withId("parent-with-configured-child")
                .spawnAndWait();

        assertNotNull(parent);

        // Spawn child actor with tags and bounded mailbox
        CompletionStage<SpringActorRef<TestCommand>> childFuture = parent.child(TestChildActor.class)
                .withId("tagged-bounded-child")
                .withTags(TagsConfig.of("child-worker", "bounded"))
                .withMailbox(MailboxConfig.bounded(25))
                .spawn();

        SpringActorRef<TestCommand> child = childFuture.toCompletableFuture().get();
        assertNotNull(child);

        Pong response = sendPingAndWait(child, "Hello from child with tags and bounded mailbox");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("tagged-bounded-child"));

        // Verify child actor tags work with mailbox
        verifyTagsInLogs("tagged-bounded-child", "child-worker", "bounded");
    }

    @Test
    void testChildActorWithTagsMailboxAndDispatcher() throws Exception {
        logAppender.clear();

        // Spawn parent actor
        SpringActorRef<TestCommand> parent = actorSystem
                .actor(TestParentActor.class)
                .withId("parent-with-fully-configured-child")
                .spawnAndWait();

        assertNotNull(parent);

        // Spawn child actor with tags, bounded mailbox, and blocking dispatcher
        CompletionStage<SpringActorRef<TestCommand>> childFuture = parent.child(TestChildActor.class)
                .withId("fully-configured-child")
                .withTags(TagsConfig.of("child-worker", "io-bound", "critical"))
                .withMailbox(MailboxConfig.bounded(50))
                .withDispatcher(DispatcherConfig.blocking())
                .spawn();

        SpringActorRef<TestCommand> child = childFuture.toCompletableFuture().get();
        assertNotNull(child);

        Pong response = sendPingAndWait(child, "Hello from child with tags, bounded mailbox, and blocking dispatcher");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("fully-configured-child"));

        // Verify child actor tags work with mailbox and dispatcher
        verifyTagsInLogs("fully-configured-child", "child-worker", "io-bound", "critical");
    }

    @Test
    void testTagsConfigOfNullArray() {
        assertThrows(IllegalArgumentException.class, () -> TagsConfig.of((String[]) null));
    }

    @Test
    void testTagsConfigOfNullSet() {
        assertThrows(IllegalArgumentException.class, () -> TagsConfig.of((Set<String>) null));
    }

    @Test
    void testWithTagsNull() {
        assertThrows(IllegalArgumentException.class, () -> actorSystem
                .actor(TestActor.class)
                .withId("null-tags-actor")
                .withTags(null));
    }

    @Test
    void testMultipleActorsWithDifferentTags() throws Exception {
        logAppender.clear();

        // Spawn multiple actors with different tags to ensure they don't interfere
        SpringActorRef<TestCommand> worker1 = actorSystem
                .actor(TestActor.class)
                .withId("worker-1")
                .withTags(TagsConfig.of("worker", "group-1"))
                .spawnAndWait();

        SpringActorRef<TestCommand> worker2 = actorSystem
                .actor(TestActor.class)
                .withId("worker-2")
                .withTags(TagsConfig.of("worker", "group-2"))
                .spawnAndWait();

        SpringActorRef<TestCommand> supervisor = actorSystem
                .actor(TestActor.class)
                .withId("supervisor")
                .withTags(TagsConfig.of("supervisor", "management"))
                .spawnAndWait();

        assertNotNull(worker1);
        assertNotNull(worker2);
        assertNotNull(supervisor);

        // Send messages and verify tags
        sendPingAndWait(worker1, "Hello from worker-1");
        sendPingAndWait(worker2, "Hello from worker-2");
        sendPingAndWait(supervisor, "Hello from supervisor");

        // Verify each actor has its own tags
        verifyTagsInLogs("worker-1", "worker", "group-1");
        verifyTagsInLogs("worker-2", "worker", "group-2");
        verifyTagsInLogs("supervisor", "supervisor", "management");
    }
}
