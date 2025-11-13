package io.github.seonwkim.metrics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActorMetricsRegistryTest {

    private ActorMetricsRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ActorMetricsRegistry.getInstance();
        registry.reset();
    }

    @Test
    void testActiveActorsIncrement() {
        assertEquals(0, registry.getActiveActors());

        registry.incrementActiveActors();
        assertEquals(1, registry.getActiveActors());
        assertEquals(1, registry.getActorsCreated());

        registry.incrementActiveActors();
        assertEquals(2, registry.getActiveActors());
        assertEquals(2, registry.getActorsCreated());
    }

    @Test
    void testActiveActorsDecrement() {
        registry.incrementActiveActors();
        registry.incrementActiveActors();
        assertEquals(2, registry.getActiveActors());

        registry.decrementActiveActors();
        assertEquals(1, registry.getActiveActors());
        assertEquals(1, registry.getActorsTerminated());

        registry.decrementActiveActors();
        assertEquals(0, registry.getActiveActors());
        assertEquals(2, registry.getActorsTerminated());
    }

    @Test
    void testActorRestartsAndStops() {
        registry.recordActorRestart("/user/test-actor");
        registry.recordActorRestart("/user/test-actor");
        assertEquals(2, registry.getActorRestarts());

        registry.recordActorStop("/user/test-actor");
        assertEquals(1, registry.getActorStops());
    }

    @Test
    void testMessageProcessing() {
        registry.recordMessageProcessed("TestMessage");
        registry.recordMessageProcessed("TestMessage");
        registry.recordMessageProcessed("AnotherMessage");

        assertEquals(3, registry.getMessagesProcessed());
        assertEquals(2, registry.getMessagesByType("TestMessage"));
        assertEquals(1, registry.getMessagesByType("AnotherMessage"));
    }

    @Test
    void testProcessingErrors() {
        registry.recordProcessingError("TestMessage", "RuntimeException");
        registry.recordProcessingError("TestMessage", "NullPointerException");
        registry.recordProcessingError("AnotherMessage", "RuntimeException");

        assertEquals(3, registry.getProcessingErrors());
        assertEquals(2, registry.getErrorsByType("RuntimeException"));
        assertEquals(1, registry.getErrorsByType("NullPointerException"));
    }

    @Test
    void testProcessingTimeStats() {
        registry.recordProcessingTime("TestMessage", 1000000); // 1ms
        registry.recordProcessingTime("TestMessage", 2000000); // 2ms
        registry.recordProcessingTime("TestMessage", 3000000); // 3ms

        ActorMetricsRegistry.ProcessingTimeStats stats =
                registry.getProcessingTimeStats("TestMessage");

        assertNotNull(stats);
        assertEquals(3, stats.getCount());
        assertEquals(2000000, stats.getAverageTimeNanos()); // Average of 1, 2, 3 ms
        assertEquals(1000000, stats.getMinTimeNanos());
        assertEquals(3000000, stats.getMaxTimeNanos());
        assertEquals(6000000, stats.getTotalTimeNanos());
    }

    @Test
    void testTimeInMailboxStats() {
        registry.recordTimeInMailbox("/user/test-actor", 100000); // 0.1ms
        registry.recordTimeInMailbox("/user/test-actor", 200000); // 0.2ms
        registry.recordTimeInMailbox("/user/test-actor", 300000); // 0.3ms

        ActorMetricsRegistry.TimeInMailboxStats stats =
                registry.getTimeInMailboxStats("/user/test-actor");

        assertNotNull(stats);
        assertEquals(3, stats.getCount());
        assertEquals(200000, stats.getAverageTimeNanos());
        assertEquals(100000, stats.getMinTimeNanos());
        assertEquals(300000, stats.getMaxTimeNanos());
    }

    @Test
    void testMailboxSize() {
        registry.updateMailboxSize("/user/test-actor", 5);
        assertEquals(5, registry.getMailboxSize("/user/test-actor"));
        assertEquals(5, registry.getMaxMailboxSize("/user/test-actor"));

        registry.updateMailboxSize("/user/test-actor", 10);
        assertEquals(10, registry.getMailboxSize("/user/test-actor"));
        assertEquals(10, registry.getMaxMailboxSize("/user/test-actor"));

        registry.updateMailboxSize("/user/test-actor", 3);
        assertEquals(3, registry.getMailboxSize("/user/test-actor"));
        assertEquals(10, registry.getMaxMailboxSize("/user/test-actor")); // Max stays at 10
    }

    @Test
    void testMailboxOverflow() {
        registry.recordMailboxOverflow("/user/test-actor");
        registry.recordMailboxOverflow("/user/test-actor");

        assertEquals(2, registry.getMailboxOverflows());
    }

    @Test
    void testDeadLetters() {
        registry.recordDeadLetter();
        registry.recordDeadLetter();

        assertEquals(2, registry.getDeadLetters());
    }

    @Test
    void testUnhandledMessages() {
        registry.recordUnhandledMessage();
        registry.recordUnhandledMessage();
        registry.recordUnhandledMessage();

        assertEquals(3, registry.getUnhandledMessages());
    }

    @Test
    void testRemoveActorMailboxMetrics() {
        registry.updateMailboxSize("/user/test-actor", 5);
        registry.recordTimeInMailbox("/user/test-actor", 100000);

        assertEquals(5, registry.getMailboxSize("/user/test-actor"));
        assertNotNull(registry.getTimeInMailboxStats("/user/test-actor"));

        registry.removeActorMailboxMetrics("/user/test-actor");

        assertEquals(0, registry.getMailboxSize("/user/test-actor"));
        assertNull(registry.getTimeInMailboxStats("/user/test-actor"));
    }

    @Test
    void testReset() {
        registry.incrementActiveActors();
        registry.recordMessageProcessed("TestMessage");
        registry.recordProcessingError("TestMessage", "RuntimeException");
        registry.updateMailboxSize("/user/test-actor", 5);

        registry.reset();

        assertEquals(0, registry.getActiveActors());
        assertEquals(0, registry.getMessagesProcessed());
        assertEquals(0, registry.getProcessingErrors());
        assertEquals(0, registry.getMailboxSize("/user/test-actor"));
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int iterationsPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < iterationsPerThread; j++) {
                    registry.recordMessageProcessed("TestMessage");
                    registry.recordProcessingTime("TestMessage", 1000000);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(threadCount * iterationsPerThread, registry.getMessagesProcessed());
        assertEquals(threadCount * iterationsPerThread, registry.getMessagesByType("TestMessage"));

        ActorMetricsRegistry.ProcessingTimeStats stats =
                registry.getProcessingTimeStats("TestMessage");
        assertNotNull(stats);
        assertEquals(threadCount * iterationsPerThread, stats.getCount());
    }
}
