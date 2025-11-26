package io.github.seonwkim.example.config;

import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.micrometer.MicrometerMetricsRegistryBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Configuration for actor metrics integration with Micrometer.
 * <p>
 * This configuration initializes the MetricsRegistry once Spring context is fully loaded
 * and wires it to the MetricsAgent.
 * <p>
 * Execution flow:
 * 1. JVM startup: MetricsAgent applies bytecode instrumentation
 * 2. Spring context: MeterRegistry bean is created
 * 3. Spring ready: This event listener creates MetricsRegistry and wires to agent
 * 4. Runtime: Instrumented code records metrics via the backend
 * <p>
 * Configuration via environment variables:
 * <ul>
 *   <li>ACTOR_METRICS_ENABLED - Enable/disable metrics (default: true)</li>
 *   <li>ACTOR_METRICS_TAG_APPLICATION - Application tag</li>
 *   <li>ACTOR_METRICS_TAG_ENVIRONMENT - Environment tag</li>
 *   <li>ACTOR_METRICS_SAMPLING_RATE - Sampling rate 0.0-1.0 (default: 1.0)</li>
 *   <li>ACTOR_METRICS_MODULE_MAILBOX_ENABLED - Enable/disable mailbox module</li>
 *   <li>ACTOR_METRICS_MODULE_MESSAGE_PROCESSING_ENABLED - Enable/disable message processing module</li>
 *   <li>ACTOR_METRICS_MODULE_ACTOR_LIFECYCLE_ENABLED - Enable/disable actor lifecycle module</li>
 * </ul>
 */
@Configuration
public class ActorMetricsConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ActorMetricsConfiguration.class);

    private final MeterRegistry meterRegistry;

    public ActorMetricsConfiguration(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Initialize metrics registry when Spring context is fully ready.
     * <p>
     * Start your application with the Java agent:
     * java -javaagent:metrics-{version}-agent.jar -jar app.jar
     * <p>
     * The builder automatically:
     * - Reads configuration from environment variables (or uses programmatic config)
     * - Creates Micrometer backend
     * - Auto-discovers and registers modules via ServiceLoader
     * - Wires registry to MetricsAgent
     * <p>
     * Set ACTOR_METRICS_ENABLED=false to skip all metrics initialization.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeMetrics() {
        // Check if metrics are enabled
        String enabled = getEnv("ACTOR_METRICS_ENABLED", "true");
        if (!Boolean.parseBoolean(enabled)) {
            logger.info("[ActorMetrics] Metrics disabled via ACTOR_METRICS_ENABLED=false. Skipping initialization.");
            return;
        }

        logger.info("[ActorMetrics] Initializing metrics registry");

        // Choose one of the following configuration approaches:

        // ==================== APPROACH 1: Environment Variables (Recommended) ====================
        // Reads all configuration from environment variables.
        // Set ACTOR_METRICS_TAG_APPLICATION, ACTOR_METRICS_SAMPLING_RATE, etc.
        MetricsRegistry registry =
                MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry).build();

        // ==================== APPROACH 2: Programmatic Configuration ====================
        // Override or supplement environment variables with programmatic configuration.
        // Uncomment to use:
        /*
        MetricsRegistry registry = MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry)
                .tag("service.name", "chat-service")           // Add custom tags
                .tag("region", "us-east-1")
                .sampling(SamplingConfig.rateBased(0.1))       // 10% sampling
                .module("mailbox", ModuleConfig.disabled())    // Disable mailbox metrics
                .build();
        */

        // ==================== APPROACH 3: Full Programmatic (No Environment) ====================
        // Start from scratch without reading environment variables.
        // Uncomment to use:
        /*
        MetricsRegistry registry = MicrometerMetricsRegistryBuilder.create(meterRegistry)
                .tag("application", "chat-example")
                .tag("environment", "production")
                .sampling(SamplingConfig.rateBased(0.5))
                .filters(FilterConfig.builder()
                        .excludeActors("io.github.seonwkim.example.actor.HealthCheckActor")
                        .excludeMessages("Ping", "HealthCheck")
                        .build())
                .module("message-processing", ModuleConfig.enabled())
                .module("actor-lifecycle", ModuleConfig.enabled())
                .module("mailbox", ModuleConfig.disabled())
                .build();
        */

        // ==================== APPROACH 4: Adaptive Sampling ====================
        // Use adaptive sampling to automatically adjust sampling rate based on throughput.
        // Uncomment to use:
        /*
        MetricsRegistry registry = MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry)
                .sampling(SamplingConfig.adaptive(
                        1000,    // Target: 1000 samples per second
                        0.01,    // Min sampling rate: 1%
                        1.0      // Max sampling rate: 100%
                ))
                .build();
        */

        logger.info(
                "[ActorMetrics] Metrics configured successfully. " + "Registered {} modules, backend: {}",
                registry.getModules().size(),
                registry.getBackend().getBackendType());
    }

    /**
     * Get environment variable or system property with fallback.
     */
    private String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null) {
            value = System.getProperty(key);
        }
        return value != null ? value : defaultValue;
    }
}
