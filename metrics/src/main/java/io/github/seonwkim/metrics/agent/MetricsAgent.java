package io.github.seonwkim.metrics.agent;

import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import java.lang.instrument.Instrumentation;
import java.util.ServiceLoader;
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java agent for instrumenting actor classes to collect metrics.
 * <p>
 * At JVM startup, the agent:
 * 1. Discovers InstrumentationModules via SPI
 * 2. Applies bytecode instrumentation to Pekko actor classes
 * 3. Waits for application to set MetricsRegistry via setRegistry()
 * <p>
 * The MetricsRegistry (with backend) must be provided by the application after startup
 * (e.g., in Spring's ApplicationReadyEvent) because backends typically need runtime
 * dependencies like Spring's MeterRegistry.
 * <p>
 * Modules are registered in META-INF/services/io.github.seonwkim.metrics.api.InstrumentationModule
 */
public class MetricsAgent {

    private static final Logger logger = LoggerFactory.getLogger(MetricsAgent.class);

    /** System property set when the agent's premain has run (i.e. -javaagent was used). */
    public static final String AGENT_LOADED_PROPERTY = "io.github.seonwkim.metrics.agent.loaded";

    @Nullable private static volatile MetricsRegistry registry;

    /**
     * Returns true if the metrics agent was loaded at JVM startup via -javaagent.
     */
    public static boolean isAgentLoaded() {
        return "true".equals(System.getProperty(AGENT_LOADED_PROPERTY));
    }

    /**
     * Premain method called when the agent is loaded during JVM startup.
     * <p>
     * Applies bytecode instrumentation but does NOT initialize the registry.
     * The registry must be set later via setRegistry() once the application
     * framework (e.g., Spring) has initialized.
     * <p>
     * Users can control instrumentation via environment variables:
     * - ACTOR_METRICS_ENABLED=false (disable all instrumentation)
     * - ACTOR_METRICS_INSTRUMENT_ACTOR_LIFECYCLE=false (disable specific module instrumentation)
     * - ACTOR_METRICS_INSTRUMENT_MAILBOX=false (disable specific module instrumentation)
     * - ACTOR_METRICS_INSTRUMENT_MESSAGE_PROCESSING=false (disable specific module instrumentation)
     *
     * @param arguments Agent arguments
     * @param instrumentation Instrumentation instance
     */
    public static void premain(String arguments, Instrumentation instrumentation) {
        System.setProperty(AGENT_LOADED_PROPERTY, "true");

        logger.info("Starting metrics agent initialization");

        if (!isMetricsEnabled()) {
            logger.info("Metrics disabled via ACTOR_METRICS_ENABLED=false; skipping all instrumentation");
            return;
        }

        try {
            // Apply bytecode instrumentation (registry will be set later)
            AgentBuilder agentBuilder = new AgentBuilder.Default()
                    .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                    .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                    .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE);

            // Discover all modules via SPI
            ServiceLoader<InstrumentationModule> moduleLoader = ServiceLoader.load(InstrumentationModule.class);
            int instrumentedCount = 0;
            int skippedCount = 0;

            for (InstrumentationModule module : moduleLoader) {
                if (!shouldInstrumentModule(module.moduleId())) {
                    logger.info("Skipping disabled module: {}", module.moduleId());
                    skippedCount++;
                    continue;
                }
                try {
                    agentBuilder = module.instrument(agentBuilder);
                    logger.info("Applied instrumentation: {}", module.moduleId());
                    instrumentedCount++;
                } catch (Exception e) {
                    logger.warn("Failed to instrument module: {}", module.moduleId(), e);
                }
            }

            agentBuilder.installOn(instrumentation);

            logger.info("Agent installed: {} modules instrumented, {} skipped", instrumentedCount, skippedCount);
            logger.info("Waiting for MetricsRegistry to be set via setRegistry()...");
        } catch (Exception e) {
            logger.error("Failed to initialize metrics agent", e);
        }
    }

    /** Reads an env var (or system property fallback), defaulting to true if absent. */
    private static boolean getBooleanConfig(String key) {
        String value = System.getenv(key);
        if (value == null) value = System.getProperty(key);
        return value == null || Boolean.parseBoolean(value);
    }

    private static boolean isMetricsEnabled() {
        return getBooleanConfig("ACTOR_METRICS_ENABLED");
    }

    private static boolean shouldInstrumentModule(String moduleId) {
        String key = "ACTOR_METRICS_INSTRUMENT_" + moduleId.toUpperCase().replace("-", "_");
        return getBooleanConfig(key);
    }

    /**
     * Agent method called when the agent is loaded after JVM startup.
     *
     * @param arguments Agent arguments
     * @param instrumentation Instrumentation instance
     */
    public static void agentmain(String arguments, Instrumentation instrumentation) {
        premain(arguments, instrumentation);
    }

    /**
     * Get the metrics registry instance.
     * <p>
     * Returns null until the application calls setRegistry().
     * Instrumented code checks this before recording metrics.
     */
    @Nullable public static MetricsRegistry getRegistry() {
        return registry;
    }

    /**
     * Set the metrics registry.
     * <p>
     * This is called by the application after the framework (e.g., Spring) has initialized
     * and the metrics backend (e.g., MicrometerMetricsBackend) has been created.
     * <p>
     * After this is called, instrumented code will start recording metrics.
     *
     * @param metricsRegistry the registry to use for recording metrics
     */
    public static void setRegistry(@Nullable MetricsRegistry metricsRegistry) {
        registry = metricsRegistry;
        if (metricsRegistry != null) {
            logger.info(
                    "MetricsRegistry set with {} modules",
                    metricsRegistry.getModules().size());
        } else {
            logger.info("MetricsRegistry cleared; metrics collection disabled");
        }
    }
}
