package io.github.seonwkim.metrics;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Java agent for instrumenting actor classes to collect metrics. This agent uses ByteBuddy to
 * intercept method calls on actor classes and collect metrics about their execution.
 */
public class MetricsAgent {

    private static final Logger logger = LoggerFactory.getLogger(MetricsAgent.class);

    /**
     * Premain method called when the agent is loaded during JVM startup.
     *
     * @param arguments Agent arguments
     * @param instrumentation Instrumentation instance
     */
    public static void premain(String arguments, Instrumentation instrumentation) {
        AgentBuilder agentBuilder = new AgentBuilder.Default();
        agentBuilder = EnvelopeInstrumentation.decorate(agentBuilder);
        agentBuilder = ActorInstrumentation.decorate(agentBuilder);

        agentBuilder.installOn(instrumentation);
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
        logger.info("[ActorMetricsAgent] Agent installed successfully");
    }
}
