package io.github.seonwkim.metrics.core;

import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * Builder for defining bytecode instrumentations.
 * Provides a fluent API for specifying what classes/methods to instrument.
 *
 * <p>This will be implemented in Phase 0 to provide ByteBuddy transformations.
 * For now, it's a placeholder to allow compilation.
 */
public class InstrumentationBuilder {

    private InstrumentationBuilder() {}

    /**
     * Create a new instrumentation builder.
     */
    public static InstrumentationBuilder create() {
        return new InstrumentationBuilder();
    }

    /**
     * Apply this instrumentation to an AgentBuilder.
     * @param agentBuilder the agent builder to modify
     * @return the modified agent builder
     */
    public AgentBuilder apply(AgentBuilder agentBuilder) {
        // TODO: Implement in Phase 0
        return agentBuilder;
    }

    /**
     * Build the instrumentation.
     */
    public InstrumentationBuilder build() {
        return this;
    }
}
