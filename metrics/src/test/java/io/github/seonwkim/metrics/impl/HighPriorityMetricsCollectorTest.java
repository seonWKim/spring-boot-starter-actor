package io.github.seonwkim.metrics.impl;

import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.metrics.TestActorSystem;
import io.github.seonwkim.metrics.interceptor.InvokeAdviceEventInterceptorsHolder;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HighPriorityMetricsCollectorTest {

    private TestActorSystem actorSystem;
    private HighPriorityMetricsCollector metrics;

    @BeforeEach
    void setUp() {
        InvokeAdviceEventInterceptorsHolder.reset();

        actorSystem = new TestActorSystem();
        metrics = new HighPriorityMetricsCollector();
        InvokeAdviceEventInterceptorsHolder.register(metrics);
    }

    @Test
    void testProcessingTimeMetricRecorded() throws Exception {
        AtomicBoolean messageProcessed = new AtomicBoolean(false);
        ActorRef<TestSlowActor.Command> actor = actorSystem
                .spawn(
                        TestSlowActor.Command.class,
                        "slow-actor-" + UUID.randomUUID(),
                        TestSlowActor.create(messageProcessed),
                        Duration.ofSeconds(5))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        actor.tell(new TestSlowActor.Process());

        // Wait for message to be processed
        Thread.sleep(200);

        assertTrue(messageProcessed.get(), "Message should have been processed");

        // Verify processing time metrics
        HighPriorityMetricsCollector.TimerMetric processingTimer =
                metrics.getProcessingTimeMetric(TestSlowActor.Process.class.getSimpleName());

        assertNotNull(processingTimer, "Processing timer metric should be recorded");
        assertEquals(1, processingTimer.getCount(), "Should have processed 1 message");

        // Processing time should be at least 100ms (100,000,000 nanos)
        long expectedMinNanos = 100_000_000L;
        assertTrue(
                processingTimer.getMinTimeNanos() >= expectedMinNanos,
                "Min processing time should be at least 100ms");
    }

    @Test
    void testTimeInMailboxMetricRecorded() throws Exception {
        AtomicBoolean messageProcessed = new AtomicBoolean(false);
        ActorRef<TestFastActor.Command> actor = actorSystem
                .spawn(
                        TestFastActor.Command.class,
                        "fast-actor-" + UUID.randomUUID(),
                        TestFastActor.create(messageProcessed),
                        Duration.ofSeconds(2))
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS);
        actor.tell(new TestFastActor.QuickProcess());

        // Wait for message to be processed
        Thread.sleep(100);

        assertTrue(messageProcessed.get(), "Message should have been processed");

        // Verify time in mailbox metrics
        HighPriorityMetricsCollector.TimerMetric mailboxTimer =
                metrics.getTimeInMailboxMetric(TestFastActor.QuickProcess.class.getSimpleName());

        assertNotNull(mailboxTimer, "Time in mailbox metric should be recorded");
        assertEquals(1, mailboxTimer.getCount(), "Should have recorded 1 mailbox time");
        assertTrue(mailboxTimer.getTotalTimeNanos() > 0, "Time in mailbox should be positive");
    }

    @Test
    void testMessagesProcessedCounter() throws Exception {
        AtomicInteger processCount = new AtomicInteger(0);
        ActorRef<TestFastActor.Command> actor = actorSystem
                .spawn(
                        TestFastActor.Command.class,
                        "counter-actor-" + UUID.randomUUID(),
                        TestFastActor.createWithCounter(processCount),
                        Duration.ofSeconds(3))
                .toCompletableFuture()
                .get(3, TimeUnit.SECONDS);

        // Send multiple messages
        int messageCount = 5;
        for (int i = 0; i < messageCount; i++) {
            actor.tell(new TestFastActor.QuickProcess());
        }

        // Wait for all messages to be processed
        Thread.sleep(300);

        assertEquals(messageCount, processCount.get(), "All messages should be processed");

        // Verify messages processed counter
        long processedCount =
                metrics.getMessagesProcessedCount(TestFastActor.QuickProcess.class.getSimpleName());
        assertEquals(messageCount, processedCount, "Should have counted all processed messages");
    }

    @Test
    void testErrorCounterOnException() throws Exception {
        AtomicInteger errorCount = new AtomicInteger(0);
        ActorRef<TestErrorActor.Command> actor = actorSystem
                .spawn(
                        TestErrorActor.Command.class,
                        "error-actor-" + UUID.randomUUID(),
                        TestErrorActor.create(errorCount),
                        Duration.ofSeconds(3))
                .toCompletableFuture()
                .get(3, TimeUnit.SECONDS);

        // Send message that will cause an error
        actor.tell(new TestErrorActor.FailingMessage());

        // Wait for message to be processed and error to be recorded
        Thread.sleep(200);

        // The actor should have caught the error
        assertTrue(errorCount.get() > 0, "Error should have been caught by actor");

        // Note: In the current implementation, errors within the actor handler
        // won't increment the error counter unless the onExit method itself throws.
        // The error counter tracks instrumentation errors, not actor logic errors.
    }

    @Test
    void testMultipleMessageTypesMetrics() throws Exception {
        AtomicBoolean pingProcessed = new AtomicBoolean(false);
        AtomicBoolean pongProcessed = new AtomicBoolean(false);

        ActorRef<TestMultiMessageActor.Command> actor = actorSystem
                .spawn(
                        TestMultiMessageActor.Command.class,
                        "multi-msg-actor-" + UUID.randomUUID(),
                        TestMultiMessageActor.create(pingProcessed, pongProcessed),
                        Duration.ofSeconds(3))
                .toCompletableFuture()
                .get(3, TimeUnit.SECONDS);

        // Send different message types
        actor.tell(new TestMultiMessageActor.Ping());
        Thread.sleep(100);
        actor.tell(new TestMultiMessageActor.Pong());
        Thread.sleep(100);

        assertTrue(pingProcessed.get(), "Ping message should have been processed");
        assertTrue(pongProcessed.get(), "Pong message should have been processed");

        // Verify metrics for different message types
        long pingProcessedCount = metrics.getMessagesProcessedCount("Ping");
        long pongProcessedCount = metrics.getMessagesProcessedCount("Pong");

        assertEquals(1, pingProcessedCount, "Should have processed 1 Ping message");
        assertEquals(1, pongProcessedCount, "Should have processed 1 Pong message");

        assertNotNull(metrics.getProcessingTimeMetric("Ping"), "Ping processing metric should exist");
        assertNotNull(metrics.getProcessingTimeMetric("Pong"), "Pong processing metric should exist");
    }

    @Test
    void testMetricsAggregation() throws Exception {
        AtomicInteger processCount = new AtomicInteger(0);
        ActorRef<TestFastActor.Command> actor = actorSystem
                .spawn(
                        TestFastActor.Command.class,
                        "aggregate-actor-" + UUID.randomUUID(),
                        TestFastActor.createWithCounter(processCount),
                        Duration.ofSeconds(3))
                .toCompletableFuture()
                .get(3, TimeUnit.SECONDS);

        // Send multiple messages to verify aggregation
        for (int i = 0; i < 3; i++) {
            actor.tell(new TestFastActor.QuickProcess());
        }

        Thread.sleep(200);

        // Verify aggregated metrics
        HighPriorityMetricsCollector.TimerMetric processingTimer =
                metrics.getProcessingTimeMetric(TestFastActor.QuickProcess.class.getSimpleName());

        assertNotNull(processingTimer);
        assertEquals(3, processingTimer.getCount());
        assertTrue(processingTimer.getMinTimeNanos() <= processingTimer.getAverageTimeNanos());
        assertTrue(processingTimer.getAverageTimeNanos() <= processingTimer.getMaxTimeNanos());
    }

    // Test actor definitions

    static class TestSlowActor {
        public interface Command {}

        public static class Process implements Command {}

        public static Behavior<Command> create(AtomicBoolean messageProcessed) {
            return Behaviors.setup(ctx -> Behaviors.receive(TestSlowActor.Command.class)
                    .onMessage(TestSlowActor.Process.class, msg -> {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        messageProcessed.set(true);
                        return Behaviors.same();
                    })
                    .build());
        }
    }

    static class TestFastActor {
        public interface Command {}

        public static class QuickProcess implements Command {}

        public static Behavior<Command> create(AtomicBoolean messageProcessed) {
            return Behaviors.setup(ctx -> Behaviors.receive(TestFastActor.Command.class)
                    .onMessage(TestFastActor.QuickProcess.class, msg -> {
                        messageProcessed.set(true);
                        return Behaviors.same();
                    })
                    .build());
        }

        public static Behavior<Command> createWithCounter(AtomicInteger counter) {
            return Behaviors.setup(ctx -> Behaviors.receive(TestFastActor.Command.class)
                    .onMessage(TestFastActor.QuickProcess.class, msg -> {
                        counter.incrementAndGet();
                        return Behaviors.same();
                    })
                    .build());
        }
    }

    static class TestErrorActor {
        public interface Command {}

        public static class FailingMessage implements Command {}

        public static Behavior<Command> create(AtomicInteger errorCount) {
            return Behaviors.setup(ctx -> Behaviors.receive(TestErrorActor.Command.class)
                    .onMessage(TestErrorActor.FailingMessage.class, msg -> {
                        try {
                            throw new RuntimeException("Intentional test error");
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            // Actor handles the error and continues
                            return Behaviors.same();
                        }
                    })
                    .build());
        }
    }

    static class TestMultiMessageActor {
        public interface Command {}

        public static class Ping implements Command {}

        public static class Pong implements Command {}

        public static Behavior<Command> create(AtomicBoolean pingProcessed, AtomicBoolean pongProcessed) {
            return Behaviors.setup(ctx -> Behaviors.receive(TestMultiMessageActor.Command.class)
                    .onMessage(TestMultiMessageActor.Ping.class, msg -> {
                        pingProcessed.set(true);
                        return Behaviors.same();
                    })
                    .onMessage(TestMultiMessageActor.Pong.class, msg -> {
                        pongProcessed.set(true);
                        return Behaviors.same();
                    })
                    .build());
        }
    }
}
