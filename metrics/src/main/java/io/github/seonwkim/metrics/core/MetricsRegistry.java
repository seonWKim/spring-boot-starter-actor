package io.github.seonwkim.metrics.core;

import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.sampling.SamplingStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central registry for metrics configuration and instrumentation modules.
 */
public class MetricsRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MetricsRegistry.class);

    private final MeterRegistry meterRegistry;
    private final SamplingStrategy samplingStrategy;
    private final List<InstrumentationModule> modules = new CopyOnWriteArrayList<>();
    private volatile MetricsConfiguration config;
    private final Iterable<Tag> globalTags;

    private MetricsRegistry(Builder builder) {
        this.config = builder.config;
        this.meterRegistry = Objects.requireNonNull(builder.meterRegistry, "meterRegistry cannot be null");
        this.samplingStrategy = Objects.requireNonNull(builder.samplingStrategy, "samplingStrategy cannot be null");
        this.globalTags = config.getTags().entrySet().stream()
                .map(e -> Tag.of(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        if (builder.modules != null) {
            builder.modules.forEach(this::registerModule);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public void registerModule(InstrumentationModule module) {
        logger.info("Registering instrumentation module: {}", module.moduleId());
        modules.add(module);
        module.initialize(this);
    }

    public boolean shouldInstrument(ActorContext context) {
        return samplingStrategy.shouldSample(context);
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    public Iterable<Tag> getGlobalTags() {
        return globalTags;
    }

    public MetricsConfiguration getConfiguration() {
        return config;
    }

    public List<InstrumentationModule> getModules() {
        return new ArrayList<>(modules);
    }

    public SamplingStrategy getSamplingStrategy() {
        return samplingStrategy;
    }

    public void shutdown() {
        logger.info("Shutting down metrics registry");
        modules.forEach(m -> {
            try {
                m.shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down module {}", m.moduleId(), e);
            }
        });
    }

    public static class Builder {
        private MetricsConfiguration config = MetricsConfiguration.getDefault();

        @Nullable private MeterRegistry meterRegistry;

        @Nullable private SamplingStrategy samplingStrategy;

        @Nullable private List<InstrumentationModule> modules;

        public Builder configuration(MetricsConfiguration config) {
            this.config = config;
            return this;
        }

        public Builder meterRegistry(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            return this;
        }

        public Builder samplingStrategy(SamplingStrategy samplingStrategy) {
            this.samplingStrategy = samplingStrategy;
            return this;
        }

        public Builder modules(List<InstrumentationModule> modules) {
            this.modules = modules;
            return this;
        }

        public MetricsRegistry build() {
            if (meterRegistry == null) {
                throw new IllegalStateException("MeterRegistry is required");
            }
            if (samplingStrategy == null) {
                samplingStrategy = SamplingStrategy.from(config.getSampling());
            }
            return new MetricsRegistry(this);
        }
    }
}
