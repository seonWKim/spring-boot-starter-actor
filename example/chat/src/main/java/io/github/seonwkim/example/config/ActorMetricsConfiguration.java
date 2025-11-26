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
     * - Reads configuration from environment variables
     * - Creates Micrometer backend
     * - Auto-discovers and registers modules via ServiceLoader
     * - Wires registry to MetricsAgent
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeMetrics() {
        logger.info("[ActorMetrics] Initializing metrics registry");

        // Build and configure metrics registry from environment variables
        MetricsRegistry registry = MicrometerMetricsRegistryBuilder.fromEnvironment(meterRegistry).build();

        logger.info(
                "[ActorMetrics] Metrics configured successfully. "
                        + "Registered {} modules, backend: {}",
                registry.getModules().size(),
                registry.getBackend().getBackendType());
    }
}
