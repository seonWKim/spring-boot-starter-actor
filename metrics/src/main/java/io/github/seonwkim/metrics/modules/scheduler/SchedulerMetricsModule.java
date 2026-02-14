package io.github.seonwkim.metrics.modules.scheduler;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.util.RateLimitedLog;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrumentation module for Pekko Scheduler metrics.
 *
 * Tracks:
 * - scheduler.tasks.scheduled (counter) - tasks scheduled via scheduleOnce, scheduleWithFixedDelay, scheduleAtFixedRate
 *
 * Tags: from MetricsRegistry global tags
 */
public class SchedulerMetricsModule implements InstrumentationModule {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerMetricsModule.class);
    private static final RateLimitedLog errorLog = new RateLimitedLog(5000);
    private static final String MODULE_ID = "scheduler";

    private static final String METRIC_TASKS_SCHEDULED = "scheduler.tasks.scheduled";

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public String description() {
        return "Scheduler metrics (tasks scheduled)";
    }

    @Override
    public void initialize(MetricsRegistry metricsRegistry) {
        logger.info("Scheduler Metrics Module initialized");
    }

    /**
     * Instrument LightArrayRevolverScheduler for scheduler.tasks.scheduled.
     *
     * <p>NOTE: Only {@code scheduleOnce} is instrumented because Scala trait dispatch makes
     * matching {@code scheduleWithFixedDelay}/{@code scheduleAtFixedRate} unreliable across
     * Pekko/Scala versions. If those metrics are needed in the future, verify the concrete
     * method signatures on the target Pekko version first.
     */
    @Override
    public AgentBuilder instrument(AgentBuilder builder) {
        return builder.type(ElementMatchers.named("org.apache.pekko.actor.LightArrayRevolverScheduler"))
                .transform((builderParam, typeDescription, classLoader, module) -> builderParam.visit(
                        Advice.to(ScheduleOnceAdvice.class).on(ElementMatchers.named("scheduleOnce"))));
    }

    /** Advice for scheduleOnce - one task per call. */
    public static class ScheduleOnceAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter() {
            recordScheduled();
        }
    }

    private static void recordScheduled() {
        try {
            MetricsRegistry reg = MetricsAgent.getRegistry();
            if (reg == null || !reg.isModuleEnabled(MODULE_ID)) return;
            reg.getBackend()
                    .counter(METRIC_TASKS_SCHEDULED, reg.getGlobalTags())
                    .increment();
        } catch (Exception e) {
            errorLog.error(logger, "Error recording scheduler metric", e);
        }
    }
}
