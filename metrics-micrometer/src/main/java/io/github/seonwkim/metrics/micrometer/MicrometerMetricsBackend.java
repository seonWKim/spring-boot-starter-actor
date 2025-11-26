package io.github.seonwkim.metrics.micrometer;

import io.github.seonwkim.metrics.api.MetricsBackend;
import io.github.seonwkim.metrics.api.Tags;
import io.github.seonwkim.metrics.api.instruments.Counter;
import io.github.seonwkim.metrics.api.instruments.DistributionSummary;
import io.github.seonwkim.metrics.api.instruments.Gauge;
import io.github.seonwkim.metrics.api.instruments.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Micrometer-based implementation of MetricsBackend.
 * Bridges the actor metrics API to Micrometer's MeterRegistry for integration with
 * monitoring systems like Prometheus, Grafana, DataDog, etc.
 * <p>
 * This is a lightweight adapter with no framework dependencies - just pure Micrometer integration.
 * Users are responsible for wiring it up in their applications.
 * <p>
 * <b>Example usage with Spring Boot:</b>
 * <pre>{@code
 * @Configuration
 * public class ActorMetricsConfig {
 *     @Bean
 *     public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
 *         MetricsBackend backend = new MicrometerMetricsBackend(meterRegistry);
 *
 *         MetricsConfiguration config = MetricsConfiguration.builder()
 *             .enabled(true)
 *             .tag("application", "my-app")
 *             .build();
 *
 *         MetricsRegistry registry = MetricsRegistry.builder()
 *             .configuration(config)
 *             .backend(backend)
 *             .build();
 *
 *         // Register modules via SPI
 *         ServiceLoader.load(InstrumentationModule.class)
 *             .forEach(registry::registerModule);
 *
 *         // Wire to the agent
 *         MetricsAgent.setRegistry(registry);
 *
 *         return registry;
 *     }
 * }
 * }</pre>
 *
 * @see io.github.seonwkim.metrics.api.MetricsBackend
 */
public class MicrometerMetricsBackend implements MetricsBackend {

    private static final Logger logger = LoggerFactory.getLogger(MicrometerMetricsBackend.class);

    private final MeterRegistry registry;

    /**
     * Creates a new MicrometerMetricsBackend with the given MeterRegistry.
     *
     * @param registry The Micrometer registry to use for metrics
     */
    public MicrometerMetricsBackend(MeterRegistry registry) {
        this.registry = registry;
        logger.info(
                "[MicrometerMetricsBackend] Initialized with MeterRegistry: {}",
                registry.getClass().getSimpleName());
    }

    @Override
    public Counter counter(String name, Tags tags) {
        io.micrometer.core.instrument.Counter micrometerCounter = io.micrometer.core.instrument.Counter.builder(name)
                .tags(convertTags(tags))
                .register(registry);

        return new Counter() {
            @Override
            public void increment() {
                micrometerCounter.increment();
            }

            @Override
            public void increment(double amount) {
                micrometerCounter.increment(amount);
            }

            @Override
            public double count() {
                return micrometerCounter.count();
            }
        };
    }

    @Override
    public Gauge gauge(String name, Tags tags, Supplier<Number> valueSupplier) {
        io.micrometer.core.instrument.Gauge micrometerGauge = io.micrometer.core.instrument.Gauge.builder(
                        name, valueSupplier, supplier -> supplier.get().doubleValue())
                .tags(convertTags(tags))
                .register(registry);

        return new Gauge() {
            @Override
            public double value() {
                return micrometerGauge.value();
            }

            @Override
            public Supplier<Number> valueSupplier() {
                return valueSupplier;
            }
        };
    }

    @Override
    public Timer timer(String name, Tags tags) {
        io.micrometer.core.instrument.Timer micrometerTimer = io.micrometer.core.instrument.Timer.builder(name)
                .tags(convertTags(tags))
                .register(registry);

        return new Timer() {
            @Override
            public void record(Duration duration) {
                micrometerTimer.record(duration);
            }

            @Override
            public void record(long amount, TimeUnit unit) {
                micrometerTimer.record(amount, unit);
            }

            @Override
            public void recordNanos(long nanos) {
                micrometerTimer.record(nanos, TimeUnit.NANOSECONDS);
            }

            @Override
            public <T> T recordCallable(Callable<T> f) throws Exception {
                return micrometerTimer.recordCallable(f);
            }

            @Override
            public long count() {
                return micrometerTimer.count();
            }

            @Override
            public long totalTime(TimeUnit unit) {
                return (long) micrometerTimer.totalTime(unit);
            }

            @Override
            public double max(TimeUnit unit) {
                return micrometerTimer.max(unit);
            }

            @Override
            public double mean(TimeUnit unit) {
                return micrometerTimer.mean(unit);
            }
        };
    }

    @Override
    public DistributionSummary summary(String name, Tags tags) {
        io.micrometer.core.instrument.DistributionSummary micrometerSummary =
                io.micrometer.core.instrument.DistributionSummary.builder(name)
                        .tags(convertTags(tags))
                        .register(registry);

        return new DistributionSummary() {
            @Override
            public void record(double amount) {
                micrometerSummary.record(amount);
            }

            @Override
            public long count() {
                return micrometerSummary.count();
            }

            @Override
            public double totalAmount() {
                return micrometerSummary.totalAmount();
            }

            @Override
            public double max() {
                return micrometerSummary.max();
            }

            @Override
            public double mean() {
                return micrometerSummary.mean();
            }
        };
    }

    @Override
    public String getBackendType() {
        return "micrometer";
    }

    /**
     * Converts actor metrics Tags to Micrometer Tags.
     */
    private List<Tag> convertTags(Tags tags) {
        return StreamSupport.stream(tags.spliterator(), false)
                .map(tag -> Tag.of(tag.getKey(), tag.getValue()))
                .collect(Collectors.toList());
    }
}
