package io.github.seonwkim.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.apache.pekko.actor.typed.ActorRef;
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

import java.util.*;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for MDC (Mapped Diagnostic Context) functionality.
 * Tests that static and dynamic MDC values are correctly passed to actors and appear in logs.
 */
@SpringBootTest(
        classes = {
            ActorConfiguration.class,
            MdcIntegrationTest.TestConfig.class
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class MdcIntegrationTest {

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
        public StaticMdcActor mdcStaticMdcActor() {
            return new StaticMdcActor();
        }

        @Bean
        public DynamicMdcActor mdcDynamicMdcActor() {
            return new DynamicMdcActor();
        }

        @Bean
        public CombinedMdcActor mdcCombinedMdcActor() {
            return new CombinedMdcActor();
        }

        @Bean
        public TestParentActor mdcTestParentActor() {
            return new TestParentActor();
        }

        @Bean
        public TestChildActor mdcTestChildActor() {
            return new TestChildActor();
        }
    }

    // Test message types
    public interface TestCommand extends FrameworkCommand {}

    public static class Ping implements TestCommand {
        public final String message;
        public final String messageId;
        public final ActorRef<Pong> replyTo;

        public Ping(String message, String messageId, ActorRef<Pong> replyTo) {
            this.message = message;
            this.messageId = messageId;
            this.replyTo = replyTo;
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

    public static class StaticMdcActor implements SpringActorWithContext<TestCommand, SpringActorContext> {
        @Override
        public SpringActorBehavior<TestCommand> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestCommand.class, actorContext)
                    .onMessage(Ping.class, (ctx, msg) -> {
                        ctx.getLog().info("Static MDC actor received: {}", msg.message);
                        msg.replyTo.tell(new Pong("Pong: " + msg.message, ctx.getSelf().path().toString()));
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    public static class DynamicMdcActor implements SpringActorWithContext<TestCommand, SpringActorContext> {
        @Override
        public SpringActorBehavior<TestCommand> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestCommand.class, actorContext)
                    .withMdc(msg -> {
                        if (msg instanceof Ping) {
                            Ping ping = (Ping) msg;
                            return Map.of(
                                "messageId", ping.messageId,
                                "messageType", "Ping"
                            );
                        }
                        return Map.of();
                    })
                    .onMessage(Ping.class, (ctx, msg) -> {
                        ctx.getLog().info("Dynamic MDC actor received: {}", msg.message);
                        msg.replyTo.tell(new Pong("Pong: " + msg.message, ctx.getSelf().path().toString()));
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    public static class CombinedMdcActor implements SpringActorWithContext<TestCommand, SpringActorContext> {
        @Override
        public SpringActorBehavior<TestCommand> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestCommand.class, actorContext)
                    .withMdc(msg -> {
                        if (msg instanceof Ping) {
                            Ping ping = (Ping) msg;
                            return Map.of(
                                "messageId", ping.messageId,
                                "timestamp", String.valueOf(System.currentTimeMillis())
                            );
                        }
                        return Map.of();
                    })
                    .onMessage(Ping.class, (ctx, msg) -> {
                        ctx.getLog().info("Combined MDC actor received: {}", msg.message);
                        msg.replyTo.tell(new Pong("Pong: " + msg.message, ctx.getSelf().path().toString()));
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
                        msg.replyTo.tell(new Pong("Pong: " + msg.message, ctx.getSelf().path().toString()));
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    public static class TestChildActor implements SpringActorWithContext<TestCommand, SpringActorContext> {
        @Override
        public SpringActorBehavior<TestCommand> create(SpringActorContext actorContext) {
            return SpringActorBehavior.builder(TestCommand.class, actorContext)
                    .withMdc(msg -> {
                        if (msg instanceof Ping) {
                            Ping ping = (Ping) msg;
                            return Map.of(
                                "dynamicMessageId", ping.messageId,
                                "dynamicTimestamp", String.valueOf(System.currentTimeMillis())
                            );
                        }
                        return Map.of();
                    })
                    .onMessage(Ping.class, (ctx, msg) -> {
                        ctx.getLog().info("Child received: {}", msg.message);
                        msg.replyTo.tell(new Pong("Pong: " + msg.message, ctx.getSelf().path().toString()));
                        return Behaviors.same();
                    })
                    .build();
        }
    }

    private Pong sendPingAndWait(SpringActorRef<TestCommand> actor, String message, String messageId) throws Exception {
        return actor.<Ping, Pong>ask(replyTo -> new Ping(message, messageId, replyTo))
                .toCompletableFuture()
                .get();
    }

    private void verifyMdcInLogs(String actorPath, Map<String, String> expectedMdcValues) {
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
                // Verify all expected MDC values are present
                boolean allMatch = true;
                for (Map.Entry<String, String> expected : expectedMdcValues.entrySet()) {
                    String actualValue = mdc.get(expected.getKey());
                    if (actualValue == null || !actualValue.equals(expected.getValue())) {
                        allMatch = false;
                        break;
                    }
                }

                if (allMatch) {
                    foundMatchingLog = true;
                    break;
                }
            }
        }

        assertTrue(foundMatchingLog,
            String.format("No log entry found with expected MDC values %s for actor %s. Total log events: %d",
                expectedMdcValues, actorPath, events.size()));
    }

    @Test
    void testEmptyMdc() throws Exception {
        logAppender.clear();

        SpringActorRef<TestCommand> actor = actorSystem.actor(StaticMdcActor.class)
                .withId("empty-mdc-actor")
                .withMdc(MdcConfig.empty())
                .spawnAndWait();

        assertNotNull(actor);
        Pong response = sendPingAndWait(actor, "Hello with empty MDC", "msg-1");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("empty-mdc-actor"));
    }

    @Test
    void testStaticMdc() throws Exception {
        logAppender.clear();

        Map<String, String> staticMdc = Map.of(
            "userId", "user-123",
            "requestId", "req-456",
            "service", "order-service"
        );

        SpringActorRef<TestCommand> actor = actorSystem.actor(StaticMdcActor.class)
                .withId("static-mdc-actor")
                .withMdc(MdcConfig.of(staticMdc))
                .spawnAndWait();

        assertNotNull(actor);
        Pong response = sendPingAndWait(actor, "Hello with static MDC", "msg-1");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("static-mdc-actor"));

        // Verify static MDC values appear in logs
        verifyMdcInLogs("static-mdc-actor", staticMdc);
    }

    @Test
    void testDynamicMdc() throws Exception {
        logAppender.clear();

        SpringActorRef<TestCommand> actor = actorSystem.actor(DynamicMdcActor.class)
                .withId("dynamic-mdc-actor")
                .spawnAndWait();

        assertNotNull(actor);
        Pong response = sendPingAndWait(actor, "Hello with dynamic MDC", "msg-dynamic-123");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("dynamic-mdc-actor"));

        // Verify dynamic MDC values appear in logs
        verifyMdcInLogs("dynamic-mdc-actor", Map.of(
            "messageId", "msg-dynamic-123",
            "messageType", "Ping"
        ));
    }

    @Test
    void testCombinedStaticAndDynamicMdc() throws Exception {
        logAppender.clear();

        Map<String, String> staticMdc = Map.of(
            "userId", "user-789",
            "service", "payment-service"
        );

        SpringActorRef<TestCommand> actor = actorSystem.actor(CombinedMdcActor.class)
                .withId("combined-mdc-actor")
                .withMdc(MdcConfig.of(staticMdc))
                .spawnAndWait();

        assertNotNull(actor);
        Pong response = sendPingAndWait(actor, "Hello with combined MDC", "msg-combined-456");
        assertNotNull(response);
        assertTrue(response.actorPath.contains("combined-mdc-actor"));

        // Wait a bit for logs
        Thread.sleep(100);

        // Verify both static and dynamic MDC values appear in logs
        List<ILoggingEvent> events = logAppender.getEvents();
        boolean foundWithBoth = false;

        for (ILoggingEvent event : events) {
            Map<String, String> mdc = event.getMDCPropertyMap();
            String pekkoSource = mdc.get("pekkoSource");

            if (pekkoSource != null && pekkoSource.contains("combined-mdc-actor")) {
                // Check for both static and dynamic values
                if ("user-789".equals(mdc.get("userId")) &&
                    "payment-service".equals(mdc.get("service")) &&
                    "msg-combined-456".equals(mdc.get("messageId")) &&
                    mdc.get("timestamp") != null) {
                    foundWithBoth = true;
                    break;
                }
            }
        }

        assertTrue(foundWithBoth, "Log entry should contain both static and dynamic MDC values");
    }

    @Test
    void testChildActorWithMdc() throws Exception {
        logAppender.clear();

        Map<String, String> parentMdc = Map.of(
            "parentId", "parent-1",
            "sessionId", "session-abc"
        );

        // Spawn parent actor with MDC
        SpringActorRef<TestCommand> parent = actorSystem.actor(TestParentActor.class)
                .withId("parent-with-mdc")
                .withMdc(MdcConfig.of(parentMdc))
                .spawnAndWait();

        assertNotNull(parent);

        // Send a message to the parent to verify parent's MDC
        Pong parentResponse = sendPingAndWait(parent, "Hello parent", "msg-parent-1");
        assertNotNull(parentResponse);
        assertTrue(parentResponse.actorPath.contains("parent-with-mdc"));

        // Define child's static MDC
        Map<String, String> childMdc = Map.of(
            "childId", "child-1",
            "role", "worker"
        );

        // Spawn child actor with its own MDC (static)
        CompletionStage<SpringActorRef<TestCommand>> childFuture = parent.child(TestChildActor.class)
                .withId("child-with-mdc")
                .withMdc(MdcConfig.of(childMdc))
                .spawn();

        SpringActorRef<TestCommand> child = childFuture.toCompletableFuture().get();
        assertNotNull(child);

        // Send a message to the child to verify child's MDC (both static and dynamic)
        Pong childResponse = sendPingAndWait(child, "Hello from child with MDC", "msg-child-1");
        assertNotNull(childResponse);
        assertTrue(childResponse.actorPath.contains("child-with-mdc"));

        // Wait for logs
        Thread.sleep(200);

        List<ILoggingEvent> events = logAppender.getEvents();

        // Verify parent's MDC contains only parent values (no child values)
        boolean foundParentLog = false;
        for (ILoggingEvent event : events) {
            Map<String, String> mdc = event.getMDCPropertyMap();
            String pekkoSource = mdc.get("pekkoSource");

            if (pekkoSource != null && pekkoSource.contains("parent-with-mdc")) {
                // Parent should have its own static MDC
                if ("parent-1".equals(mdc.get("parentId")) &&
                    "session-abc".equals(mdc.get("sessionId"))) {
                    // Ensure child's MDC does NOT leak into parent
                    assertNull(mdc.get("childId"), "Parent should not have child's MDC");
                    assertNull(mdc.get("role"), "Parent should not have child's MDC");
                    assertNull(mdc.get("dynamicMessageId"), "Parent should not have child's dynamic MDC");
                    foundParentLog = true;
                    break;
                }
            }
        }
        assertTrue(foundParentLog, "Should find parent log with parent's MDC");

        // Verify child's MDC contains child's static MDC + dynamic MDC (no parent values)
        boolean foundChildLog = false;
        for (ILoggingEvent event : events) {
            Map<String, String> mdc = event.getMDCPropertyMap();
            String pekkoSource = mdc.get("pekkoSource");

            if (pekkoSource != null && pekkoSource.contains("child-with-mdc")) {
                // Child should have its own static MDC
                if ("child-1".equals(mdc.get("childId")) &&
                    "worker".equals(mdc.get("role"))) {
                    // Child should also have dynamic MDC
                    assertEquals("msg-child-1", mdc.get("dynamicMessageId"),
                        "Child should have dynamic MDC from message");
                    assertNotNull(mdc.get("dynamicTimestamp"),
                        "Child should have dynamic timestamp");

                    // Ensure parent's MDC does NOT leak into child
                    assertNull(mdc.get("parentId"), "Child should not inherit parent's MDC");
                    assertNull(mdc.get("sessionId"), "Child should not inherit parent's MDC");

                    foundChildLog = true;
                    break;
                }
            }
        }
        assertTrue(foundChildLog, "Should find child log with child's static and dynamic MDC");
    }

    @Test
    void testMdcConfigOfNullMap() {
        assertThrows(IllegalArgumentException.class, () -> MdcConfig.of(null));
    }

    @Test
    void testMdcConfigOfEmptyMap() {
        assertThrows(IllegalArgumentException.class, () -> MdcConfig.of(Map.of()));
    }

    @Test
    void testWithMdcNull() {
        assertThrows(IllegalArgumentException.class, () ->
            actorSystem.actor(StaticMdcActor.class)
                    .withId("null-mdc-actor")
                    .withMdc(null)
        );
    }

    @Test
    void testMultipleActorsWithDifferentMdc() throws Exception {
        logAppender.clear();

        // Spawn multiple actors with different MDC to ensure they don't interfere
        SpringActorRef<TestCommand> actor1 = actorSystem.actor(StaticMdcActor.class)
                .withId("mdc-actor-1")
                .withMdc(MdcConfig.of(Map.of("actorId", "1", "environment", "prod")))
                .spawnAndWait();

        SpringActorRef<TestCommand> actor2 = actorSystem.actor(StaticMdcActor.class)
                .withId("mdc-actor-2")
                .withMdc(MdcConfig.of(Map.of("actorId", "2", "environment", "staging")))
                .spawnAndWait();

        SpringActorRef<TestCommand> actor3 = actorSystem.actor(StaticMdcActor.class)
                .withId("mdc-actor-3")
                .withMdc(MdcConfig.of(Map.of("actorId", "3", "environment", "dev")))
                .spawnAndWait();

        assertNotNull(actor1);
        assertNotNull(actor2);
        assertNotNull(actor3);

        // Send messages and verify each has its own MDC
        sendPingAndWait(actor1, "Hello from actor 1", "msg-1");
        sendPingAndWait(actor2, "Hello from actor 2", "msg-2");
        sendPingAndWait(actor3, "Hello from actor 3", "msg-3");

        // Verify each actor has its own MDC values
        verifyMdcInLogs("mdc-actor-1", Map.of("actorId", "1", "environment", "prod"));
        verifyMdcInLogs("mdc-actor-2", Map.of("actorId", "2", "environment", "staging"));
        verifyMdcInLogs("mdc-actor-3", Map.of("actorId", "3", "environment", "dev"));
    }

    @Test
    void testMdcWithSpecialCharacters() throws Exception {
        logAppender.clear();

        Map<String, String> mdcWithSpecialChars = Map.of(
            "trace-id", "trace-123-abc",
            "user.email", "user@example.com",
            "request_path", "/api/v1/orders"
        );

        SpringActorRef<TestCommand> actor = actorSystem.actor(StaticMdcActor.class)
                .withId("special-chars-mdc-actor")
                .withMdc(MdcConfig.of(mdcWithSpecialChars))
                .spawnAndWait();

        assertNotNull(actor);
        Pong response = sendPingAndWait(actor, "Hello with special chars MDC", "msg-1");
        assertNotNull(response);

        // Verify MDC with special characters
        verifyMdcInLogs("special-chars-mdc-actor", mdcWithSpecialChars);
    }
}
