package io.github.seonwkim.metrics.impl;

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
import io.github.seonwkim.metrics.impl.ActorProcessingTimeMetricsImplTest.TestFastActor.Command;
import io.github.seonwkim.metrics.listener.ActorInstrumentationEventListener;

class ActorProcessingTimeMetricsImplTest {

    private TestActorSystem actorSystem;
    private ActorProcessingTimeMetricsImpl metrics;

    @BeforeEach
    void setUp() {
        ActorInstrumentationEventListener.reset();

        actorSystem = new TestActorSystem();
        metrics = new ActorProcessingTimeMetricsImpl();
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
        ActorProcessingTimeMetricsImpl.TimerMetric timerMetric =
                metrics.getProcessingTimeMetric(TestSlowActor.Process.class.getSimpleName());

        assertNotNull(timerMetric, "Timer metric should be recorded");
        assertEquals(1, timerMetric.getCount(), "Should have processed 1 message");

        // Processing time should be at least 100ms (the sleep time)
        assertTrue(timerMetric.getMinTimeMillis() >= 100,
                   "Min processing time should be at least 100ms, was: " + timerMetric.getMinTimeMillis());
        assertTrue(timerMetric.getMaxTimeMillis() >= 100,
                   "Max processing time should be at least 100ms, was: " + timerMetric.getMaxTimeMillis());
        assertTrue(timerMetric.getAverageTimeMillis() >= 100,
                   "Average processing time should be at least 100ms, was: "
                   + timerMetric.getAverageTimeMillis());
        assertTrue(timerMetric.getTotalTimeMillis() >= 100,
                   "Total processing time should be at least 100ms, was: " + timerMetric.getTotalTimeMillis());
    }

    @Test
    void testMultipleMessagesMetrics() throws Exception {
        AtomicBoolean messageProcessed = new AtomicBoolean(false);

        // Create behavior for fast processing
        Behavior<TestFastActor.Command> behavior = Behaviors.setup(ctx ->
                                                                           Behaviors.receive(
                                                                                            TestFastActor.Command.class)
                                                                                    .onMessage(
                                                                                            TestFastActor.QuickProcess.class,
                                                                                            msg -> {
                                                                                                messageProcessed.set(
                                                                                                        true);
                                                                                                return Behaviors.same();
                                                                                            })
                                                                                    .build());

        // Send multiple messages
        for (int i = 0; i < 3; i++) {
            messageProcessed.set(false);
            actorSystem.spawn(
                               TestFastActor.QuickProcess.class,
                               "fast-actor-" + UUID.randomUUID(),
                               behavior,
                               Duration.ofSeconds(2))
                       .toCompletableFuture()
                       .get(2, TimeUnit.SECONDS);

            // Wait for message to be processed
            Thread.sleep(50);
            assertTrue(messageProcessed.get(), "Message " + i + " should have been processed");
        }

        // Give a small delay for metrics to be recorded
        Thread.sleep(100);

        // Verify metrics were recorded for all messages
        ActorProcessingTimeMetricsImpl.TimerMetric timerMetric =
                metrics.getProcessingTimeMetric("QuickProcess");

        assertNotNull(timerMetric, "Timer metric should be recorded");
        assertEquals(3, timerMetric.getCount(), "Should have processed 3 messages");

        // All times should be positive
        assertTrue(timerMetric.getMinTimeMillis() >= 0, "Min time should be non-negative");
        assertTrue(timerMetric.getMaxTimeMillis() >= 0, "Max time should be non-negative");
        assertTrue(timerMetric.getAverageTimeMillis() >= 0, "Average time should be non-negative");
        assertTrue(timerMetric.getTotalTimeMillis() >= 0, "Total time should be non-negative");

        // Min should be <= average <= max
        assertTrue(timerMetric.getMinTimeMillis() <= timerMetric.getAverageTimeMillis(),
                   "Min should be <= average");
        assertTrue(timerMetric.getAverageTimeMillis() <= timerMetric.getMaxTimeMillis(),
                   "Average should be <= max");
    }

    @Test
    void testMetricsWithDifferentMessageTypes() throws Exception {
        AtomicBoolean pingProcessed = new AtomicBoolean(false);

        // Create behavior that handles Ping and Pong differently
        Behavior<TestMultiMessageActor.Command> behavior = Behaviors.setup(ctx ->
                                                                                   Behaviors.receive(
                                                                                                    TestMultiMessageActor.Command.class)
                                                                                            .onMessage(
                                                                                                    TestMultiMessageActor.Ping.class,
                                                                                                    msg -> {
                                                                                                        pingProcessed.set(
                                                                                                                true);
                                                                                                        return Behaviors.same();
                                                                                                    })
                                                                                            .onMessage(
                                                                                                    TestMultiMessageActor.Pong.class,
                                                                                                    msg -> {
                                                                                                        // This won't be called in this test
                                                                                                        return Behaviors.same();
                                                                                                    })
                                                                                            .build());

        // Send Ping message
        actorSystem.spawn(
                           TestMultiMessageActor.Ping.class,
                           "multi-msg-actor-" + UUID.randomUUID(),
                           behavior,
                           Duration.ofSeconds(3))
                   .toCompletableFuture()
                   .get(3, TimeUnit.SECONDS);

        // Wait for message to be processed
        Thread.sleep(100);

        assertTrue(pingProcessed.get(), "Ping message should have been processed");

        // Verify metrics for different message types
        ActorProcessingTimeMetricsImpl.TimerMetric pingMetric =
                metrics.getProcessingTimeMetric("Ping");
        ActorProcessingTimeMetricsImpl.TimerMetric pongMetric =
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
    }

    static class TestMultiMessageActor {
        public interface Command {}

        public static class Ping implements Command {}

        public static class Pong implements Command {}
    }
}
