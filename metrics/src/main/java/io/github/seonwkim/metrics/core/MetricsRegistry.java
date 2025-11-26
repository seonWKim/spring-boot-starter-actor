package io.github.seonwkim.metrics.core;

import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.api.MetricsBackend;
import io.github.seonwkim.metrics.api.Tags;
import io.github.seonwkim.metrics.filter.FilterEngine;
import io.github.seonwkim.metrics.sampling.SamplingStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central registry for metrics configuration and instrumentation modules.
 * Thread-safe and supports runtime configuration updates.
 */
public class MetricsRegistry {

    private static final Logger logger = LoggerFactory.getLogger(MetricsRegistry.class);

    private final MetricsBackend backend;
    private final FilterEngine filterEngine;
    private final SamplingStrategy samplingStrategy;
    private final List<InstrumentationModule> modules = new CopyOnWriteArrayList<>();
    private volatile MetricsConfiguration config;
    private final Tags globalTags;

    private MetricsRegistry(Builder builder) {
        this.config = builder.config;
        this.backend = Objects.requireNonNull(builder.backend, "backend cannot be null");
        this.filterEngine = Objects.requireNonNull(builder.filterEngine, "filterEngine cannot be null");
        this.samplingStrategy = Objects.requireNonNull(builder.samplingStrategy, "samplingStrategy cannot be null");
        this.globalTags = Tags.of(config.getTags());

        // Register initial modules
        if (builder.modules != null) {
            builder.modules.forEach(this::registerModule);
        }
    }

    /**
     * Create a new builder for MetricsRegistry.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Register an instrumentation module.
     */
    public void registerModule(InstrumentationModule module) {
        logger.info("Registering instrumentation module: {}", module.moduleId());
        modules.add(module);
        module.initialize(this);
    }

    /**
     * Update configuration at runtime.
     */
    public void updateConfiguration(MetricsConfiguration newConfig) {
        MetricsConfiguration oldConfig = this.config;
        this.config = newConfig;
        logger.info("Configuration updated");

        // Notify modules
        modules.forEach(m -> {
            try {
                m.onConfigurationChanged(oldConfig, newConfig);
            } catch (Exception e) {
                logger.error("Error notifying module {} of configuration change", m.moduleId(), e);
            }
        });
    }

    /**
     * Check if an actor should be instrumented based on filters, sampling, and business rules.
     * This method consolidates all instrumentation checks:
     * - Skips system actors (e.g., /system/*)
     * - Skips temporary actors (e.g., /temp/* or actors with $ in path)
     * - Applies user-configured filters (include/exclude patterns)
     * - Applies sampling strategy
     */
    public boolean shouldInstrument(ActorContext context) {
        // Skip system and temporary actors (business rule)
        if (context.isSystemActor() || context.isTemporaryActor()) {
            return false;
        }

        // Apply user-configured filtering and sampling
        return filterEngine.matches(context) && samplingStrategy.shouldSample(context);
    }

    /**
     * Get the metrics backend.
     */
    public MetricsBackend getBackend() {
        return backend;
    }

    /**
     * Get global tags to apply to all metrics.
     */
    public Tags getGlobalTags() {
        return globalTags;
    }

    /**
     * Get the current configuration.
     */
    public MetricsConfiguration getConfiguration() {
        return config;
    }

    /**
     * Get all registered modules.
     */
    public List<InstrumentationModule> getModules() {
        return new ArrayList<>(modules);
    }

    /**
     * Get filter engine.
     */
    public FilterEngine getFilterEngine() {
        return filterEngine;
    }

    /**
     * Get sampling strategy.
     */
    public SamplingStrategy getSamplingStrategy() {
        return samplingStrategy;
    }

    /**
     * Shutdown the registry and all modules.
     */
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

    /**
     * Builder for MetricsRegistry.
     */
    public static class Builder {
        private MetricsConfiguration config = MetricsConfiguration.getDefault();

        @Nullable private MetricsBackend backend;

        @Nullable private FilterEngine filterEngine;

        @Nullable private SamplingStrategy samplingStrategy;

        @Nullable private List<InstrumentationModule> modules;

        public Builder configuration(MetricsConfiguration config) {
            this.config = config;
            return this;
        }

        public Builder backend(MetricsBackend backend) {
            this.backend = backend;
            return this;
        }

        public Builder filterEngine(FilterEngine filterEngine) {
            this.filterEngine = filterEngine;
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
            // Set defaults if not provided
            if (backend == null) {
                throw new IllegalStateException("MetricsBackend is required");
            }
            if (filterEngine == null) {
                filterEngine = FilterEngine.from(config.getFilters());
            }
            if (samplingStrategy == null) {
                samplingStrategy = SamplingStrategy.from(config.getSampling());
            }

            return new MetricsRegistry(this);
        }
    }
}
