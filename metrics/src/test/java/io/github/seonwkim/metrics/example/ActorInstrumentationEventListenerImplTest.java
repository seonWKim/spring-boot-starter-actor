package io.github.seonwkim.metrics.example;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.seonwkim.metrics.TestActorSystem;
import io.github.seonwkim.metrics.listener.ActorInstrumentationEventListener;

class ActorInstrumentationEventListenerImplTest {

    private TestActorSystem actorSystem;
    private ActorInstrumentationEventListenerImpl metrics;

    @BeforeEach
    void setUp() {
        ActorInstrumentationEventListener.reset();

        actorSystem = new TestActorSystem();
        metrics = new ActorInstrumentationEventListenerImpl();
        ActorInstrumentationEventListener.register(metrics);
    }

    @Test
    void testProcessingTimeMetricRecorded() throws Exception {
        AtomicBoolean messageProcessed = new AtomicBoolean(false);
        ActorRef<TestSlowActor.Command> actor =  actorSystem.spawn(
                TestSlowActor.Command.class,
                "slow-actor-" + UUID.randomUUID(),
                TestSlowActor.create(messageProcessed),
                Duration.ofSeconds(5)).toCompletableFuture().get(5, TimeUnit.SECONDS);
        actor.tell(new TestSlowActor.Process());

        // Wait for message to be processed
        Thread.sleep(200);

        assertTrue(messageProcessed.get(), "Message should have been processed");

        // Verify metrics were recorded
        ActorInstrumentationEventListenerImpl.TimerMetric timerMetric =
                metrics.getProcessingTimeMetric(TestSlowActor.Process.class.getSimpleName());

        assertNotNull(timerMetric, "Timer metric should be recorded");
        assertEquals(1, timerMetric.getCount(), "Should have processed 1 message");

        // Processing time should be at least 100ms (100,000,000 nanos)
        long expectedMinNanos = 100_000_000L; // 100ms in nanoseconds
        assertTrue(timerMetric.getMinTimeNanos() >= expectedMinNanos,
                   "Min processing time should be at least 100ms (100,000,000ns), was: " + timerMetric.getMinTimeNanos());
        assertTrue(timerMetric.getMaxTimeNanos() >= expectedMinNanos,
                   "Max processing time should be at least 100ms (100,000,000ns), was: " + timerMetric.getMaxTimeNanos());
        assertTrue(timerMetric.getAverageTimeNanos() >= expectedMinNanos,
                   "Average processing time should be at least 100ms (100,000,000ns), was: "
                   + timerMetric.getAverageTimeNanos());
        assertTrue(timerMetric.getTotalTimeNanos() >= expectedMinNanos,
                   "Total processing time should be at least 100ms (100,000,000ns), was: " + timerMetric.getTotalTimeNanos());
    }

    @Test
    void testMultipleMessagesMetrics() throws Exception {
        AtomicBoolean messageProcessed = new AtomicBoolean(false);

        // Spawn actor
        ActorRef<TestFastActor.Command> actor = actorSystem.spawn(
                TestFastActor.Command.class,
                "fast-actor-" + UUID.randomUUID(),
                TestFastActor.create(messageProcessed),
                Duration.ofSeconds(2))
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS);

        // Send multiple messages
        for (int i = 0; i < 3; i++) {
            messageProcessed.set(false);
            actor.tell(new TestFastActor.QuickProcess());

            // Wait for message to be processed
            Thread.sleep(50);
            assertTrue(messageProcessed.get(), "Message " + i + " should have been processed");
        }

        // Give a small delay for metrics to be recorded
        Thread.sleep(100);

        // Verify metrics were recorded for all messages
        ActorInstrumentationEventListenerImpl.TimerMetric timerMetric =
                metrics.getProcessingTimeMetric(TestFastActor.QuickProcess.class.getSimpleName());

        assertNotNull(timerMetric, "Timer metric should be recorded");
        assertEquals(3, timerMetric.getCount(), "Should have processed 3 messages");

        // All times should be positive (greater than 0 for fast operations)
        assertTrue(timerMetric.getMinTimeNanos() > 0, "Min time should be positive");
        assertTrue(timerMetric.getMaxTimeNanos() > 0, "Max time should be positive");
        assertTrue(timerMetric.getAverageTimeNanos() > 0, "Average time should be positive");
        assertTrue(timerMetric.getTotalTimeNanos() > 0, "Total time should be positive");

        // Min should be <= average <= max
        assertTrue(timerMetric.getMinTimeNanos() <= timerMetric.getAverageTimeNanos(),
                   "Min should be <= average");
        assertTrue(timerMetric.getAverageTimeNanos() <= timerMetric.getMaxTimeNanos(),
                   "Average should be <= max");
    }

    @Test
    void testMetricsWithDifferentMessageTypes() throws Exception {
        AtomicBoolean pingProcessed = new AtomicBoolean(false);

        // Spawn actor
        ActorRef<TestMultiMessageActor.Command> actor = actorSystem.spawn(
                TestMultiMessageActor.Command.class,
                "multi-msg-actor-" + UUID.randomUUID(),
                TestMultiMessageActor.create(pingProcessed),
                Duration.ofSeconds(3))
                .toCompletableFuture()
                .get(3, TimeUnit.SECONDS);

        // Send Ping message
        actor.tell(new TestMultiMessageActor.Ping());

        // Wait for message to be processed
        Thread.sleep(100);

        assertTrue(pingProcessed.get(), "Ping message should have been processed");

        // Verify metrics for different message types
        ActorInstrumentationEventListenerImpl.TimerMetric pingMetric =
                metrics.getProcessingTimeMetric("Ping");
        ActorInstrumentationEventListenerImpl.TimerMetric pongMetric =
                metrics.getProcessingTimeMetric("Pong");

        assertNotNull(pingMetric, "Ping metric should be recorded");
        assertEquals(1, pingMetric.getCount(), "Should have processed 1 Ping message");

        assertNull(pongMetric, "Pong metric should not exist as it wasn't sent");
    }

    static class TestSlowActor {
        public interface Command {}

        public static class Process implements Command {}

        public static Behavior<Command> create(AtomicBoolean messageProcessed) {
            return Behaviors.setup(
                    ctx ->
                            Behaviors.receive(TestSlowActor.Command.class)
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
            return Behaviors.setup(
                    ctx ->
                            Behaviors.receive(TestFastActor.Command.class)
                                     .onMessage(TestFastActor.QuickProcess.class, msg -> {
                                         messageProcessed.set(true);
                                         return Behaviors.same();
                                     })
                                     .build());
        }
    }

    static class TestMultiMessageActor {
        public interface Command {}

        public static class Ping implements Command {}

        public static class Pong implements Command {}

        public static Behavior<Command> create(AtomicBoolean pingProcessed) {
            return Behaviors.setup(
                    ctx ->
                            Behaviors.receive(TestMultiMessageActor.Command.class)
                                     .onMessage(TestMultiMessageActor.Ping.class, msg -> {
                                         pingProcessed.set(true);
                                         return Behaviors.same();
                                     })
                                     .onMessage(TestMultiMessageActor.Pong.class, msg -> {
                                         // This won't be called in this test
                                         return Behaviors.same();
                                     })
                                     .build());
        }
    }
}
