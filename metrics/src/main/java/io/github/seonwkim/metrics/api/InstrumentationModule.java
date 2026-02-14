package io.github.seonwkim.metrics.api;

import io.github.seonwkim.metrics.core.MetricsConfiguration;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * Base interface for all instrumentation modules.
 * Modules are discovered via ServiceLoader (SPI) or manually registered.
 *
 * <p>Implementations should be stateless where possible, with state managed
 * through the MetricsRegistry.
 */
public interface InstrumentationModule {

    /**
     * Unique identifier for this module (e.g., "actor-lifecycle", "message-processing").
     */
    String moduleId();

    /**
     * Human-readable description of what this module instruments.
     */
    String description();

    /**
     * Apply ByteBuddy bytecode instrumentation.
     * Override to add instrumentation; default returns builder unchanged (no-op).
     *
     * @param builder the AgentBuilder to chain transformations onto
     * @return the agent builder (possibly with additional transformations)
     */
    default AgentBuilder instrument(AgentBuilder builder) {
        return builder;
    }

    /**
     * Called when the module is registered with the registry.
     * This is where you should create and register metrics.
     *
     * @param registry the metrics registry
     */
    void initialize(MetricsRegistry registry);

    /**
     * Called when configuration changes at runtime.
     * Modules can react to configuration changes (e.g., enable/disable features).
     *
     * @param oldConfig the previous configuration
     * @param newConfig the new configuration
     */
    default void onConfigurationChanged(MetricsConfiguration oldConfig, MetricsConfiguration newConfig) {
        // Default: do nothing
    }

    /**
     * Shutdown hook for cleanup when the agent is stopped.
     */
    default void shutdown() {
        // Default: do nothing
    }
}
