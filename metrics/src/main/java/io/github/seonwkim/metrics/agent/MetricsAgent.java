package io.github.seonwkim.metrics.agent;

import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
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

    @Nullable private static volatile MetricsRegistry registry;

    /**
     * Premain method called when the agent is loaded during JVM startup.
     * <p>
     * Applies bytecode instrumentation but does NOT initialize the registry.
     * The registry must be set later via setRegistry() once the application
     * framework (e.g., Spring) has initialized.
     * <p>
     * Users can control which modules to instrument via environment variables:
     * - ACTOR_METRICS_INSTRUMENT_ACTOR_LIFECYCLE=false (disable lifecycle instrumentation)
     * - ACTOR_METRICS_INSTRUMENT_MAILBOX=false (disable mailbox instrumentation)
     * - ACTOR_METRICS_INSTRUMENT_MESSAGE_PROCESSING=false (disable message processing instrumentation)
     *
     * @param arguments Agent arguments
     * @param instrumentation Instrumentation instance
     */
    public static void premain(String arguments, Instrumentation instrumentation) {
        logger.info("[MetricsAgent] Starting metrics agent initialization");

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
                // Check if this module should be instrumented
                if (!shouldInstrumentModule(module.moduleId())) {
                    logger.info("[MetricsAgent] Skipping instrumentation for module: {} (disabled via environment)",
                            module.moduleId());
                    skippedCount++;
                    continue;
                }

                try {
                    // Use reflection to call the static instrument() method
                    Method instrumentMethod = module.getClass().getMethod("instrument", AgentBuilder.class);
                    agentBuilder = (AgentBuilder) instrumentMethod.invoke(null, agentBuilder);
                    logger.info("[MetricsAgent] Applied instrumentation for module: {}", module.moduleId());
                    instrumentedCount++;
                } catch (Exception e) {
                    logger.warn("[MetricsAgent] Failed to apply instrumentation for module: {}", module.moduleId(), e);
                }
            }

            agentBuilder.installOn(instrumentation);

            logger.info("[MetricsAgent] Metrics agent installed successfully. Instrumented {} modules, skipped {}.",
                    instrumentedCount, skippedCount);
            logger.info("[MetricsAgent] Waiting for application to set MetricsRegistry via setRegistry()...");

        } catch (Exception e) {
            logger.error("[MetricsAgent] Failed to initialize metrics agent", e);
        }
    }

    /**
     * Check if a module should be instrumented based on environment variables.
     * <p>
     * Environment variable format: ACTOR_METRICS_INSTRUMENT_{MODULE_ID}
     * Example: ACTOR_METRICS_INSTRUMENT_MAILBOX=false to disable mailbox instrumentation
     * <p>
     * Default: true (instrument all modules unless explicitly disabled)
     */
    private static boolean shouldInstrumentModule(String moduleId) {
        String envKey = "ACTOR_METRICS_INSTRUMENT_" + moduleId.toUpperCase().replace("-", "_");
        String value = System.getenv(envKey);
        if (value == null) {
            value = System.getProperty(envKey);
        }

        // Default to true if not specified
        if (value == null) {
            return true;
        }

        return Boolean.parseBoolean(value);
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
    @Nullable
    public static MetricsRegistry getRegistry() {
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
                    "[MetricsAgent] MetricsRegistry set. Metrics collection enabled with {} modules.",
                    metricsRegistry.getModules().size());
        } else {
            logger.info("[MetricsAgent] MetricsRegistry cleared. Metrics collection disabled.");
        }
    }
}
