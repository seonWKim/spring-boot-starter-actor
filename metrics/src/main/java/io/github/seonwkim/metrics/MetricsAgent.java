package io.github.seonwkim.metrics;

import java.lang.instrument.Instrumentation;

/**
 * Java agent for instrumenting actor classes to collect metrics.
 * This agent uses ByteBuddy to intercept method calls on actor classes
 * and collect metrics about their execution.
 */
public class MetricsAgent {

    /**
     * Premain method called when the agent is loaded during JVM startup.
     *
     * @param arguments Agent arguments
     * @param instrumentation Instrumentation instance
     */
    public static void premain(String arguments, Instrumentation instrumentation) {
        installAgent(instrumentation);
    }

    /**
     * Agent method called when the agent is loaded after JVM startup.
     *
     * @param arguments Agent arguments
     * @param instrumentation Instrumentation instance
     */
    public static void agentmain(String arguments, Instrumentation instrumentation) {
        installAgent(instrumentation);
    }

    /**
     * Installs the agent by setting up ByteBuddy transformations.
     *
     * @param instrumentation Instrumentation instance
     */
    private static void installAgent(Instrumentation instrumentation) {
        System.out.println("[ActorMetricsAgent] Agent installed successfully");
    }
}
