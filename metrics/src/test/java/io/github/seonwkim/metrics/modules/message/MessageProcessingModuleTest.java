package io.github.seonwkim.metrics.modules.message;

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
 * Unit test for MessageProcessingModule.
 * Tests the module's initialization, configuration, and API.
 *
 * Note: This tests the module infrastructure, not the actual ByteBuddy instrumentation.
 * Instrumentation testing requires the Java agent and will be done in integration tests.
 */
class MessageProcessingModuleTest {

    private TestMetricsBackend metricsBackend;
    private MetricsRegistry metricsRegistry;
    private MessageProcessingModule module;

    @BeforeEach
    void setUp() {
        // Create test metrics backend
        metricsBackend = new TestMetricsBackend();

        // Create configuration
        MetricsConfiguration config = MetricsConfiguration.builder()
                .enabled(true)
                .tag("test", "message-processing")
                .build();

        // Create registry
        metricsRegistry = MetricsRegistry.builder()
                .configuration(config)
                .backend(metricsBackend)
                .build();

        // Create and register module
        module = new MessageProcessingModule();
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
        assertEquals("message-processing", module.moduleId());
        assertNotNull(module.description());
        assertFalse(module.description().isEmpty());
        assertTrue(module.description().contains("processing"));
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
    void testMetricsCreation() {
        // Simulate creating metrics that would be used during instrumentation
        Tags testTags = Tags.of("actor.class", "TestActor", "message.type", "TestMessage")
                .and(metricsRegistry.getGlobalTags());

        // Processing time timer
        Timer processingTimer = metricsBackend.timer("actor.message.processing.time", testTags);
        assertNotNull(processingTimer);

        // Processed counter
        Counter processedCounter = metricsBackend.counter("actor.message.processed", testTags);
        assertNotNull(processedCounter);

        // Error counter
        Tags errorTags = testTags.and("error.type", "NullPointerException");
        Counter errorCounter = metricsBackend.counter("actor.message.errors", errorTags);
        assertNotNull(errorCounter);

        // Verify metrics work
        processingTimer.record(100, TimeUnit.MILLISECONDS);
        assertEquals(1, processingTimer.count());

        processedCounter.increment();
        assertEquals(1.0, processedCounter.count());

        errorCounter.increment();
        assertEquals(1.0, errorCounter.count());
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
    void testMultipleMessageTypes() {
        // Simulate tracking different message types
        String[] messageTypes = {"SayHello", "Stop", "GetStatus", "UpdateState"};

        for (String messageType : messageTypes) {
            Tags tags = Tags.of("actor.class", "TestActor", "message.type", messageType)
                    .and(metricsRegistry.getGlobalTags());

            Timer timer = metricsBackend.timer("actor.message.processing.time", tags);
            timer.record(50, TimeUnit.MILLISECONDS);

            Counter counter = metricsBackend.counter("actor.message.processed", tags);
            counter.increment();
        }

        // Verify we have metrics for all message types
        assertEquals(4, metricsBackend.timerCount());
        assertEquals(4, metricsBackend.counterCount());
    }

    @Test
    void testErrorTracking() {
        Tags baseTags = Tags.of(
                        "actor.class", "FailingActor",
                        "message.type", "FailingMessage")
                .and(metricsRegistry.getGlobalTags());

        // Simulate successful processing
        Counter processedCounter = metricsBackend.counter("actor.message.processed", baseTags);
        processedCounter.increment();
        processedCounter.increment();

        // Simulate errors
        Tags errorTags1 = baseTags.and("error.type", "NullPointerException");
        Counter errorCounter1 = metricsBackend.counter("actor.message.errors", errorTags1);
        errorCounter1.increment();

        Tags errorTags2 = baseTags.and("error.type", "IllegalArgumentException");
        Counter errorCounter2 = metricsBackend.counter("actor.message.errors", errorTags2);
        errorCounter2.increment();

        // Verify counts
        assertEquals(2.0, processedCounter.count());
        assertEquals(1.0, errorCounter1.count());
        assertEquals(1.0, errorCounter2.count());
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

        public int counterCount() {
            return counters.size();
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
