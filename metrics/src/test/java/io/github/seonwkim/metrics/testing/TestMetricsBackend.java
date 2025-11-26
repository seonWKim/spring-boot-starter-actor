package io.github.seonwkim.metrics.testing;

import io.github.seonwkim.metrics.api.MetricsBackend;
import io.github.seonwkim.metrics.api.Tag;
import io.github.seonwkim.metrics.api.Tags;
import io.github.seonwkim.metrics.api.instruments.Counter;
import io.github.seonwkim.metrics.api.instruments.DistributionSummary;
import io.github.seonwkim.metrics.api.instruments.Gauge;
import io.github.seonwkim.metrics.api.instruments.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Test implementation of MetricsBackend for integration tests.
 * Provides in-memory tracking of metrics and helper methods for assertions.
 */
public class TestMetricsBackend implements MetricsBackend {
    private final Map<String, TestCounter> counters = new ConcurrentHashMap<>();
    private final Map<String, TestGauge> gauges = new ConcurrentHashMap<>();
    private final Map<String, TestTimer> timers = new ConcurrentHashMap<>();
    private final Map<String, TestDistributionSummary> summaries = new ConcurrentHashMap<>();

    @Override
    public Counter counter(String name, Tags tags) {
        String key = name + tags.toString();
        return counters.computeIfAbsent(key, k -> new TestCounter(name, tags));
    }

    @Override
    public Gauge gauge(String name, Tags tags, Supplier<Number> valueSupplier) {
        String key = name + tags.toString();
        return gauges.computeIfAbsent(key, k -> new TestGauge(name, tags, valueSupplier));
    }

    @Override
    public Timer timer(String name, Tags tags) {
        String key = name + tags.toString();
        return timers.computeIfAbsent(key, k -> new TestTimer(name, tags));
    }

    @Override
    public DistributionSummary summary(String name, Tags tags) {
        String key = name + tags.toString();
        return summaries.computeIfAbsent(key, k -> new TestDistributionSummary(name, tags));
    }

    @Override
    public String getBackendType() {
        return "test";
    }

    // Helper methods for test assertions

    public double getCounterValue(String name) {
        return counters.values().stream()
                .filter(c -> c.name.equals(name))
                .mapToDouble(Counter::count)
                .sum();
    }

    public double getGaugeValue(String name) {
        return gauges.values().stream()
                .filter(g -> g.name.equals(name))
                .mapToDouble(Gauge::value)
                .sum();
    }

    public long getTimerCount(String name) {
        return timers.values().stream()
                .filter(t -> t.name.equals(name))
                .mapToLong(Timer::count)
                .sum();
    }

    public long getTimerTotalTime(String name) {
        return timers.values().stream()
                .filter(t -> t.name.equals(name))
                .mapToLong(t -> t.totalTime(TimeUnit.NANOSECONDS))
                .sum();
    }

    public int gaugeCount() {
        return gauges.size();
    }

    public boolean hasMetricWithTag(String metricName, String tagKey) {
        return counters.values().stream().filter(c -> c.name.equals(metricName)).anyMatch(c -> hasTag(c.tags, tagKey))
                || gauges.values().stream()
                        .filter(g -> g.name.equals(metricName))
                        .anyMatch(g -> hasTag(g.tags, tagKey))
                || timers.values().stream()
                        .filter(t -> t.name.equals(metricName))
                        .anyMatch(t -> hasTag(t.tags, tagKey));
    }

    private boolean hasTag(Tags tags, String tagKey) {
        for (Tag tag : tags) {
            if (tag.getKey().equals(tagKey)) {
                return true;
            }
        }
        return false;
    }

    // Test instrument implementations

    static class TestCounter implements Counter {
        final String name;
        final Tags tags;
        private final AtomicLong count = new AtomicLong(0);

        TestCounter(String name, Tags tags) {
            this.name = name;
            this.tags = tags;
        }

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
        final String name;
        final Tags tags;
        private final Supplier<Number> valueSupplier;

        TestGauge(String name, Tags tags, Supplier<Number> valueSupplier) {
            this.name = name;
            this.tags = tags;
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
        final String name;
        final Tags tags;
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong totalTime = new AtomicLong(0);

        TestTimer(String name, Tags tags) {
            this.name = name;
            this.tags = tags;
        }

        @Override
        public void record(java.time.Duration duration) {
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
        final String name;
        final Tags tags;
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong total = new AtomicLong(0);

        TestDistributionSummary(String name, Tags tags) {
            this.name = name;
            this.tags = tags;
        }

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
