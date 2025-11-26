package io.github.seonwkim.metrics.modules.actor;

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
 * Unit test for ActorLifecycleModule.
 * Tests the module's initialization, configuration, and API.
 *
 * Note: This tests the module infrastructure, not the actual ByteBuddy instrumentation.
 * Instrumentation testing requires the Java agent and will be done in integration tests.
 */
class ActorLifecycleModuleTest {

    private TestMetricsBackend metricsBackend;
    private MetricsRegistry metricsRegistry;
    private ActorLifecycleModule module;

    @BeforeEach
    void setUp() {
        // Create test metrics backend
        metricsBackend = new TestMetricsBackend();

        // Create configuration
        MetricsConfiguration config = MetricsConfiguration.builder()
                .enabled(true)
                .tag("test", "actor-lifecycle")
                .build();

        // Create registry
        metricsRegistry = MetricsRegistry.builder()
                .configuration(config)
                .backend(metricsBackend)
                .build();

        // Create and register module
        module = new ActorLifecycleModule();
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
        assertEquals("actor-lifecycle", module.moduleId());
        assertNotNull(module.description());
        assertFalse(module.description().isEmpty());
    }

    @Test
    void testModuleInitialization() {
        // Verify gauge was created during initialization
        // Note: Counters for created/terminated are created per-actor with specific tags,
        // not during module initialization
        assertTrue(metricsBackend.hasGauge("actor.lifecycle.active"));
    }

    @Test
    void testModuleShutdown() {
        // Module should shutdown cleanly
        assertDoesNotThrow(() -> module.shutdown());

        // Calling shutdown twice should be safe
        assertDoesNotThrow(() -> module.shutdown());
    }

    @Test
    void testMetricsBackendIntegration() {
        // Verify that metrics backend is properly used for the active gauge
        // Note: created/terminated counters are created dynamically when actors are spawned
        Gauge activeGauge = metricsBackend.getGauge("actor.lifecycle.active");
        assertNotNull(activeGauge);

        // Initial active count should be 0
        assertEquals(0.0, activeGauge.value());

        // Verify backend type
        assertEquals("test", metricsBackend.getBackendType());
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
            return counters.computeIfAbsent(name, k -> new TestCounter());
        }

        @Override
        public Gauge gauge(String name, Tags tags, Supplier<Number> valueSupplier) {
            return gauges.computeIfAbsent(name, k -> new TestGauge(valueSupplier));
        }

        @Override
        public Timer timer(String name, Tags tags) {
            return timers.computeIfAbsent(name, k -> new TestTimer());
        }

        @Override
        public DistributionSummary summary(String name, Tags tags) {
            return summaries.computeIfAbsent(name, k -> new TestDistributionSummary());
        }

        @Override
        public String getBackendType() {
            return "test";
        }

        public boolean hasCounter(String name) {
            return counters.containsKey(name);
        }

        public boolean hasGauge(String name) {
            return gauges.containsKey(name);
        }

        public double getCounterValue(String name) {
            TestCounter counter = counters.get(name);
            return counter != null ? counter.count() : 0.0;
        }

        public double getGaugeValue(String name) {
            TestGauge gauge = gauges.get(name);
            return gauge != null ? gauge.value() : 0.0;
        }

        public Counter getCounter(String name) {
            return counters.get(name);
        }

        public Gauge getGauge(String name) {
            return gauges.get(name);
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
