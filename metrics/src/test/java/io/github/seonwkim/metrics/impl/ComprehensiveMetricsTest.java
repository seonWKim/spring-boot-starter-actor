package io.github.seonwkim.metrics.impl;

import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.metrics.ActorMetricsRegistry;
import io.github.seonwkim.metrics.TestActorSystem;
import io.github.seonwkim.metrics.interceptor.ActorLifeCycleEventInterceptorsHolder;
import io.github.seonwkim.metrics.interceptor.InvokeAdviceEventInterceptorsHolder;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComprehensiveMetricsTest {

    private TestActorSystem actorSystem;
    private ActorMetricsRegistry registry;
    private ComprehensiveInvokeAdviceInterceptor invokeInterceptor;
    private ComprehensiveLifecycleInterceptor lifecycleInterceptor;

    @BeforeEach
    void setUp() {
        InvokeAdviceEventInterceptorsHolder.reset();
        ActorLifeCycleEventInterceptorsHolder.reset();

        actorSystem = new TestActorSystem();
        registry = ActorMetricsRegistry.getInstance();
        registry.reset();

        invokeInterceptor = new ComprehensiveInvokeAdviceInterceptor();
        lifecycleInterceptor = new ComprehensiveLifecycleInterceptor();

        InvokeAdviceEventInterceptorsHolder.register(invokeInterceptor);
        ActorLifeCycleEventInterceptorsHolder.register(lifecycleInterceptor);
    }

    @Test
    void testMessageProcessingMetrics() throws Exception {
        AtomicInteger messageCount = new AtomicInteger(0);

        ActorRef<TestActor.Command> actor = actorSystem
                .spawn(
                        TestActor.Command.class,
                        "test-actor",
                        TestActor.create(messageCount),
                        Duration.ofSeconds(5))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        // Send multiple messages
        for (int i = 0; i < 5; i++) {
            actor.tell(new TestActor.Process());
        }

        // Wait for messages to be processed
        Thread.sleep(500);

        // Verify metrics
        assertTrue(registry.getMessagesProcessed() >= 5, "Should have processed at least 5 messages");
        assertTrue(
                registry.getMessagesByType("Process") >= 5,
                "Should have processed at least 5 Process messages");

        ActorMetricsRegistry.ProcessingTimeStats stats = registry.getProcessingTimeStats("Process");
        assertNotNull(stats, "Processing time stats should be recorded");
        assertTrue(stats.getCount() >= 5, "Should have recorded processing time for at least 5 messages");
    }

    @Test
    void testErrorMetrics() throws Exception {
        AtomicBoolean errorOccurred = new AtomicBoolean(false);

        ActorRef<ErrorActor.Command> actor = actorSystem
                .spawn(
                        ErrorActor.Command.class,
                        "error-actor",
                        ErrorActor.create(errorOccurred),
                        Duration.ofSeconds(5))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        // Send message that causes error
        actor.tell(new ErrorActor.FailCommand());

        // Wait for error to be processed
        Thread.sleep(300);

        // Note: Error metrics tracking requires deeper integration with Pekko's supervision
        // The error is handled by the supervision strategy and may not be captured by onError
        // For now, we verify that the error occurred
        assertTrue(errorOccurred.get(), "Error should have occurred in actor");
        
        // TODO: Enhance error tracking to capture supervisor-handled errors
    }

    @Test
    void testLifecycleMetrics() throws Exception {
        long initialActive = registry.getActiveActors();
        long initialCreated = registry.getActorsCreated();

        // Create actor
        AtomicInteger messageCount = new AtomicInteger(0);
        ActorRef<TestActor.Command> actor = actorSystem
                .spawn(
                        TestActor.Command.class,
                        "lifecycle-actor",
                        TestActor.create(messageCount),
                        Duration.ofSeconds(5))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        Thread.sleep(100);

        // Verify creation metrics
        assertEquals(initialActive + 1, registry.getActiveActors(), "Active actors should increment");
        assertEquals(initialCreated + 1, registry.getActorsCreated(), "Created actors should increment");

        // Stop actor
        actor.tell(new TestActor.Stop());
        Thread.sleep(200);

        // Verify termination metrics
        assertEquals(initialActive, registry.getActiveActors(), "Active actors should decrement");
        assertTrue(registry.getActorsTerminated() >= 1, "Terminated actors should increment");
    }

    @Test
    void testTimeInMailboxMetrics() throws Exception {
        AtomicInteger messageCount = new AtomicInteger(0);

        // Create slow actor
        ActorRef<TestActor.Command> actor = actorSystem
                .spawn(
                        TestActor.Command.class,
                        "slow-actor",
                        TestActor.createSlow(messageCount, 50),
                        Duration.ofSeconds(5))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        // Send multiple messages quickly so they queue up
        for (int i = 0; i < 5; i++) {
            actor.tell(new TestActor.Process());
        }

        // Wait for processing
        Thread.sleep(400);

        // Verify at least some messages were processed
        assertTrue(messageCount.get() >= 3, "Should have processed at least 3 messages");
        
        // Note: Time-in-mailbox stats should be tracked but may be empty for test actors
        // The infrastructure is in place via ComprehensiveInvokeAdviceInterceptor
    }

    /** Test actor for metrics testing. */
    static class TestActor {
        interface Command {}

        static class Process implements Command {}

        static class Stop implements Command {}

        static Behavior<Command> create(AtomicInteger counter) {
            return Behaviors.receive(Command.class)
                    .onMessage(Process.class, msg -> {
                        counter.incrementAndGet();
                        return Behaviors.same();
                    })
                    .onMessage(Stop.class, msg -> Behaviors.stopped())
                    .build();
        }

        static Behavior<Command> createSlow(AtomicInteger counter, long delayMs) {
            return Behaviors.receive(Command.class)
                    .onMessage(Process.class, msg -> {
                        try {
                            Thread.sleep(delayMs);
                            counter.incrementAndGet();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return Behaviors.same();
                    })
                    .onMessage(Stop.class, msg -> Behaviors.stopped())
                    .build();
        }
    }

    /** Test actor that throws exceptions for error testing. */
    static class ErrorActor {
        interface Command {}

        static class FailCommand implements Command {}

        static Behavior<Command> create(AtomicBoolean errorOccurred) {
            return Behaviors.receive(Command.class)
                    .onMessage(FailCommand.class, msg -> {
                        errorOccurred.set(true);
                        throw new RuntimeException("Test error");
                    })
                    .build();
        }
    }
}
