package io.github.seonwkim.metrics.modules.system;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.api.Tags;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.util.RateLimitedLog;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.pekko.actor.DeadLetter;
import org.apache.pekko.actor.UnhandledMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrumentation module for actor system-level metrics.
 *
 * Tracks:
 * - system.dead-letters (counter) - messages that could not be delivered
 * - system.unhandled-messages (counter) - messages received but not handled by actor
 *
 * Tags: from MetricsRegistry global tags
 */
public class SystemMetricsModule implements InstrumentationModule {

    private static final Logger logger = LoggerFactory.getLogger(SystemMetricsModule.class);
    private static final RateLimitedLog errorLog = new RateLimitedLog(5000);
    private static final String MODULE_ID = "system";

    private static final String METRIC_DEAD_LETTERS = "system.dead-letters";
    private static final String METRIC_UNHANDLED_MESSAGES = "system.unhandled-messages";

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public String description() {
        return "Actor system metrics (dead letters, unhandled messages)";
    }

    @Override
    public void initialize(MetricsRegistry metricsRegistry) {
        logger.info("System Metrics Module initialized");
    }

    /**
     * Instrument EventStream.publish - when DeadLetter or UnhandledMessage is published, increment counters.
     * Pekko publishes these events to the EventStream when messages cannot be delivered or are unhandled.
     */
    @Override
    public AgentBuilder instrument(AgentBuilder builder) {
        return builder.type(ElementMatchers.named("org.apache.pekko.event.EventStream"))
                .transform((builderParam, typeDescription, classLoader, module) ->
                        builderParam.visit(Advice.to(EventStreamPublishAdvice.class)
                                .on(ElementMatchers.named("publish").and(ElementMatchers.takesArguments(1)))));
    }

    /**
     * ByteBuddy advice for EventStream.publish - records system.dead-letters and
     * system.unhandled-messages when corresponding events are published.
     */
    public static class EventStreamPublishAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.Argument(0) Object event) {
            try {
                if (event == null) {
                    return;
                }

                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null || !reg.isModuleEnabled(MODULE_ID)) {
                    return;
                }

                Tags tags = reg.getGlobalTags();

                if (event instanceof DeadLetter) {
                    reg.getBackend().counter(METRIC_DEAD_LETTERS, tags).increment();
                } else if (event instanceof UnhandledMessage) {
                    reg.getBackend().counter(METRIC_UNHANDLED_MESSAGES, tags).increment();
                }
            } catch (Exception e) {
                errorLog.error(logger, "Error recording system metric", e);
            }
        }
    }
}
