package io.github.seonwkim.metrics.core;

import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * Placeholder for future bytecode instrumentation builder.
 */
public class InstrumentationBuilder {

    private InstrumentationBuilder() {}

    public static InstrumentationBuilder create() {
        return new InstrumentationBuilder();
    }

    public AgentBuilder apply(AgentBuilder agentBuilder) {
        return agentBuilder;
    }

    public InstrumentationBuilder build() {
        return this;
    }
}
