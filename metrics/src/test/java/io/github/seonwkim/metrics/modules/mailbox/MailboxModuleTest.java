package io.github.seonwkim.metrics.modules.mailbox;

import static org.junit.jupiter.api.Assertions.*;

import io.github.seonwkim.metrics.api.MetricsBackend;
import io.github.seonwkim.metrics.api.Tags;
import io.github.seonwkim.metrics.api.instruments.Counter;
import io.github.seonwkim.metrics.api.instruments.DistributionSummary;
import io.github.seonwkim.metrics.api.instruments.Gauge;
import io.github.seonwkim.metrics.api.instruments.Timer;
import io.github.seonwkim.metrics.core.MetricsConfiguration;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit test for MailboxModule.
 * Tests the module's initialization, configuration, and API.
 *
 * Note: This tests the module infrastructure, not the actual ByteBuddy instrumentation.
 * Instrumentation testing requires the Java agent and will be done in integration tests.
 */
class MailboxModuleTest {

    private TestMetricsBackend metricsBackend;
    private MetricsRegistry metricsRegistry;
    private MailboxModule module;

    @BeforeEach
    void setUp() {
        // Create test metrics backend
        metricsBackend = new TestMetricsBackend();

        // Create configuration
        MetricsConfiguration config = MetricsConfiguration.builder()
                .enabled(true)
                .tag("test", "mailbox")
                .build();

        // Create registry
        metricsRegistry = MetricsRegistry.builder()
                .configuration(config)
                .backend(metricsBackend)
                .build();

        // Create and register module
        module = new MailboxModule();
        metricsRegistry.registerModule(module);
    }

    @AfterEach
    void tearDown() {
        if (module != null) {
            module.shutdown();
        }
    }

    @Test
    void testModuleMetadata() {
        assertEquals("mailbox", module.moduleId());
        assertNotNull(module.description());
        assertFalse(module.description().isEmpty());
        assertTrue(
                module.description().contains("mailbox") || module.description().contains("Mailbox"));
    }

    @Test
    void testModuleInitialization() {
        // Module should initialize without errors
        assertNotNull(module);
    }

    @Test
    void testModuleShutdown() {
        // Module should shutdown cleanly
        assertDoesNotThrow(() -> module.shutdown());

        // Calling shutdown twice should be safe
        assertDoesNotThrow(() -> module.shutdown());
    }

    @Test
    void testMailboxSizeGaugeCreation() {
        // Simulate creating mailbox size gauge
        AtomicLong mailboxSize = new AtomicLong(0);
        Tags tags = Tags.of("actor.class", "TestActor").and(metricsRegistry.getGlobalTags());

        Gauge sizeGauge = metricsBackend.gauge("actor.mailbox.size", tags, mailboxSize::get);

        assertNotNull(sizeGauge);
        assertEquals(0.0, sizeGauge.value());

        // Simulate messages being enqueued
        mailboxSize.set(5);
        assertEquals(5.0, sizeGauge.value());

        // Simulate messages being processed
        mailboxSize.set(2);
        assertEquals(2.0, sizeGauge.value());
    }

    @Test
    void testMailboxTimeTimerCreation() {
        // Simulate creating mailbox time timer
        Tags tags = Tags.of(
                        "actor.class", "TestActor",
                        "message.type", "TestMessage")
                .and(metricsRegistry.getGlobalTags());

        Timer mailboxTimer = metricsBackend.timer("actor.mailbox.time", tags);
        assertNotNull(mailboxTimer);

        // Record some mailbox times
        mailboxTimer.record(10, TimeUnit.MILLISECONDS);
        mailboxTimer.record(25, TimeUnit.MILLISECONDS);
        mailboxTimer.record(15, TimeUnit.MILLISECONDS);

        assertEquals(3, mailboxTimer.count());
        assertEquals(50, mailboxTimer.totalTime(TimeUnit.MILLISECONDS));
    }

    @Test
    void testMultipleActorMailboxes() {
        // Simulate tracking mailbox sizes for different actor classes
        String[] actorClasses = {"Actor1", "Actor2", "Actor3"};
        Map<String, AtomicLong> mailboxSizes = new ConcurrentHashMap<>();

        for (String actorClass : actorClasses) {
            AtomicLong size = new AtomicLong(0);
            mailboxSizes.put(actorClass, size);

            Tags tags = Tags.of("actor.class", actorClass).and(metricsRegistry.getGlobalTags());
            Gauge gauge = metricsBackend.gauge("actor.mailbox.size", tags, () -> size.get());
            assertNotNull(gauge);

            // Set different sizes
            size.set((long) (Math.random() * 10));
        }

        // Verify we have gauges for all actor classes
        assertEquals(3, metricsBackend.gaugeCount());
    }

    @Test
    void testMailboxTimeForDifferentMessageTypes() {
        String actorClass = "TestActor";
        String[] messageTypes = {"SayHello", "Stop", "GetStatus"};

        for (String messageType : messageTypes) {
            Tags tags = Tags.of(
                            "actor.class", actorClass,
                            "message.type", messageType)
                    .and(metricsRegistry.getGlobalTags());

            Timer timer = metricsBackend.timer("actor.mailbox.time", tags);
            timer.record((long) (Math.random() * 100), TimeUnit.MILLISECONDS);
        }

        // Verify we have timers for all message types
        assertEquals(3, metricsBackend.timerCount());
    }

    @Test
    void testConfigurationIntegration() {
        // Verify registry configuration is accessible
        MetricsConfiguration config = metricsRegistry.getConfiguration();
        assertNotNull(config);
        assertTrue(config.isEnabled());

        // Verify global tags are applied
        Tags globalTags = metricsRegistry.getGlobalTags();
        assertNotNull(globalTags);
    }

    @Test
    void testModuleRegistration() {
        // Verify module is in registry's module list
        var modules = metricsRegistry.getModules();
        assertTrue(modules.contains(module));
        assertEquals(1, modules.size());
    }

    @Test
    void testMailboxSizeDynamicUpdates() {
        AtomicLong mailboxSize = new AtomicLong(0);
        Tags tags = Tags.of("actor.class", "DynamicActor").and(metricsRegistry.getGlobalTags());

        Gauge gauge = metricsBackend.gauge("actor.mailbox.size", tags, () -> mailboxSize.get());

        // Simulate enqueue operations
        mailboxSize.incrementAndGet();
        assertEquals(1.0, gauge.value());

        mailboxSize.incrementAndGet();
        mailboxSize.incrementAndGet();
        assertEquals(3.0, gauge.value());

        // Simulate dequeue operations
        mailboxSize.decrementAndGet();
        assertEquals(2.0, gauge.value());

        mailboxSize.set(0);
        assertEquals(0.0, gauge.value());
    }

    /**
     * Test implementation of MetricsBackend for testing.
     */
    static class TestMetricsBackend implements MetricsBackend {
        private final Map<String, TestCounter> counters = new ConcurrentHashMap<>();
        private final Map<String, TestGauge> gauges = new ConcurrentHashMap<>();
        private final Map<String, TestTimer> timers = new ConcurrentHashMap<>();
        private final Map<String, TestDistributionSummary> summaries = new ConcurrentHashMap<>();

        @Override
        public Counter counter(String name, Tags tags) {
            String key = name + tags.toString();
            return counters.computeIfAbsent(key, k -> new TestCounter());
        }

        @Override
        public Gauge gauge(String name, Tags tags, Supplier<Number> valueSupplier) {
            String key = name + tags.toString();
            return gauges.computeIfAbsent(key, k -> new TestGauge(valueSupplier));
        }

        @Override
        public Timer timer(String name, Tags tags) {
            String key = name + tags.toString();
            return timers.computeIfAbsent(key, k -> new TestTimer());
        }

        @Override
        public DistributionSummary summary(String name, Tags tags) {
            String key = name + tags.toString();
            return summaries.computeIfAbsent(key, k -> new TestDistributionSummary());
        }

        @Override
        public String getBackendType() {
            return "test";
        }

        public int gaugeCount() {
            return gauges.size();
        }

        public int timerCount() {
            return timers.size();
        }
    }

    static class TestCounter implements Counter {
        private final AtomicLong count = new AtomicLong(0);

        @Override
        public void increment() {
            count.incrementAndGet();
        }

        @Override
        public void increment(double amount) {
            count.addAndGet((long) amount);
        }

        @Override
        public double count() {
            return count.get();
        }
    }

    static class TestGauge implements Gauge {
        private final Supplier<Number> valueSupplier;

        TestGauge(Supplier<Number> valueSupplier) {
            this.valueSupplier = valueSupplier;
        }

        @Override
        public double value() {
            return valueSupplier.get().doubleValue();
        }

        @Override
        public Supplier<Number> valueSupplier() {
            return valueSupplier;
        }
    }

    static class TestTimer implements Timer {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalTime = new AtomicLong(0);

        @Override
        public void record(Duration duration) {
            count.incrementAndGet();
            totalTime.addAndGet(duration.toNanos());
        }

        @Override
        public void record(long amount, TimeUnit unit) {
            count.incrementAndGet();
            totalTime.addAndGet(unit.toNanos(amount));
        }

        @Override
        public void recordNanos(long nanos) {
            count.incrementAndGet();
            totalTime.addAndGet(nanos);
        }

        @Override
        public <T> T recordCallable(java.util.concurrent.Callable<T> f) throws Exception {
            long start = System.nanoTime();
            try {
                return f.call();
            } finally {
                recordNanos(System.nanoTime() - start);
            }
        }

        @Override
        public long count() {
            return count.get();
        }

        @Override
        public long totalTime(TimeUnit unit) {
            return unit.convert(totalTime.get(), TimeUnit.NANOSECONDS);
        }

        @Override
        public double max(TimeUnit unit) {
            return 0;
        }

        @Override
        public double mean(TimeUnit unit) {
            long c = count.get();
            return c == 0 ? 0 : unit.convert(totalTime.get(), TimeUnit.NANOSECONDS) / (double) c;
        }
    }

    static class TestDistributionSummary implements DistributionSummary {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong total = new AtomicLong(0);

        @Override
        public void record(double amount) {
            count.incrementAndGet();
            total.addAndGet((long) amount);
        }

        @Override
        public long count() {
            return count.get();
        }

        @Override
        public double totalAmount() {
            return total.get();
        }

        @Override
        public double max() {
            return 0;
        }

        @Override
        public double mean() {
            long c = count.get();
            return c == 0 ? 0 : total.get() / (double) c;
        }
    }
}
