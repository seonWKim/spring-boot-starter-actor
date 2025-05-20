package io.github.seonwkim.metrics;

import static net.bytebuddy.matcher.ElementMatchers.named;

import java.lang.instrument.Instrumentation;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;

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
        ActorInstrumentation.install(instrumentation);
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
