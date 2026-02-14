package io.github.seonwkim.metrics.micrometer;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.api.MetricsBackend;
import io.github.seonwkim.metrics.core.MetricsConfiguration;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ServiceLoader;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder for creating MetricsRegistry with Micrometer backend.
 * Automatically reads configuration from environment variables.
 * <p>
 * Supported environment variables:
 * <ul>
 *   <li>ACTOR_METRICS_ENABLED - Enable/disable metrics (default: true)</li>
 *   <li>ACTOR_METRICS_TAG_{NAME} - Global tags (e.g., ACTOR_METRICS_TAG_APPLICATION=my-app)</li>
 *   <li>ACTOR_METRICS_SAMPLING_RATE - Sampling rate 0.0-1.0 (default: 1.0)</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * @Bean
 * public MetricsRegistry actorMetricsRegistry(MeterRegistry meterRegistry) {
 *     return MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry).build();
 * }
 * }</pre>
 */
public class MicrometerMetricsRegistryBuilder {

    private static final Logger logger = LoggerFactory.getLogger(MicrometerMetricsRegistryBuilder.class);

    private static final String ENV_METRICS_ENABLED = "ACTOR_METRICS_ENABLED";
    private static final String ENV_TAG_PREFIX = "ACTOR_METRICS_TAG_";
    private static final String ENV_SAMPLING_RATE = "ACTOR_METRICS_SAMPLING_RATE";

    private final MeterRegistry meterRegistry;
    private final MetricsConfiguration.Builder configBuilder;

    /**
     * Create a builder with environment variable configuration.
     */
    public static MicrometerMetricsRegistryBuilder fromEnvironment(MeterRegistry meterRegistry) {
        MicrometerMetricsRegistryBuilder builder = new MicrometerMetricsRegistryBuilder(meterRegistry);
        builder.loadFromEnvironment();
        return builder;
    }

    /**
     * Create a builder with default configuration.
     */
    public static MicrometerMetricsRegistryBuilder create(MeterRegistry meterRegistry) {
        return new MicrometerMetricsRegistryBuilder(meterRegistry);
    }

    private MicrometerMetricsRegistryBuilder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.configBuilder = MetricsConfiguration.builder();
    }

    /**
     * Load configuration from environment variables.
     */
    private void loadFromEnvironment() {
        // Enabled flag
        String enabled = getEnvOrNull(ENV_METRICS_ENABLED);
        if (enabled != null && !enabled.isEmpty()) {
            configBuilder.enabled(Boolean.parseBoolean(enabled));
            logger.info("Metrics enabled from env: {}", enabled);
        }

        // Global tags - scan all env vars starting with ACTOR_METRICS_TAG_
        System.getenv().forEach((key, value) -> {
            if (key.startsWith(ENV_TAG_PREFIX)) {
                String tagName =
                        key.substring(ENV_TAG_PREFIX.length()).toLowerCase().replace('_', '.');
                configBuilder.tag(tagName, value);
                logger.info("Added tag from env: {}={}", tagName, value);
            }
        });

        // Sampling rate
        String samplingRate = getEnvOrNull(ENV_SAMPLING_RATE);
        if (samplingRate != null && !samplingRate.isEmpty()) {
            double rate = Double.parseDouble(samplingRate);
            configBuilder.sampling(MetricsConfiguration.SamplingConfig.rateBased(rate));
            logger.info("Sampling rate from env: {}", rate);
        }
    }

    /**
     * Add a global tag.
     */
    public MicrometerMetricsRegistryBuilder tag(String key, String value) {
        configBuilder.tag(key, value);
        return this;
    }

    /**
     * Set enabled flag.
     */
    public MicrometerMetricsRegistryBuilder enabled(boolean enabled) {
        configBuilder.enabled(enabled);
        return this;
    }

    /**
     * Set sampling configuration.
     */
    public MicrometerMetricsRegistryBuilder sampling(MetricsConfiguration.SamplingConfig sampling) {
        configBuilder.sampling(sampling);
        return this;
    }

    /**
     * Set filters configuration.
     */
    public MicrometerMetricsRegistryBuilder filters(MetricsConfiguration.FilterConfig filters) {
        configBuilder.filters(filters);
        return this;
    }

    /**
     * Build the MetricsRegistry, auto-discover modules, and wire to agent.
     */
    public MetricsRegistry build() {
        MetricsBackend backend = new MicrometerMetricsBackend(meterRegistry);
        MetricsConfiguration config = configBuilder.build();
        MetricsRegistry registry =
                MetricsRegistry.builder().configuration(config).backend(backend).build();

        // Auto-discover and register modules via ServiceLoader
        int moduleCount = 0;
        for (InstrumentationModule module : ServiceLoader.load(InstrumentationModule.class)) {
            logger.info("Registering module: {} - {}", module.moduleId(), module.description());
            registry.registerModule(module);
            moduleCount++;
        }

        MetricsAgent.setRegistry(registry);

        if (!MetricsAgent.isAgentLoaded()) {
            logger.warn("Metrics agent was NOT loaded. Start with: java -javaagent:metrics-agent.jar -jar app.jar");
        }

        logger.info("MetricsRegistry ready: {} modules, backend={}", moduleCount, backend.getBackendType());
        return registry;
    }

    @Nullable private String getEnvOrNull(String key) {
        String value = System.getenv(key);
        return value != null ? value : System.getProperty(key);
    }
}
