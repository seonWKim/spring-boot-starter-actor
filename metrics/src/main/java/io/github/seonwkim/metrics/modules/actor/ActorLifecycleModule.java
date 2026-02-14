package io.github.seonwkim.metrics.modules.actor;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.api.Tags;
import io.github.seonwkim.metrics.core.MetricsContext;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.util.RateLimitedLog;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrumentation module for actor lifecycle metrics.
 *
 * Tracks:
 * - actor.lifecycle.created (counter)
 * - actor.lifecycle.terminated (counter)
 * - actor.lifecycle.restarts (counter)
 * - actor.lifecycle.resumes (counter) - supervision Resume decision
 * - actor.lifecycle.active (gauge)
 *
 * Tags: actor.class
 */
public class ActorLifecycleModule implements InstrumentationModule {

    private static final Logger logger = LoggerFactory.getLogger(ActorLifecycleModule.class);
    private static final RateLimitedLog errorLog = new RateLimitedLog(5000);
    private static final String MODULE_ID = "actor-lifecycle";

    // Metric names
    private static final String METRIC_LIFECYCLE_ACTIVE = "actor.lifecycle.active";
    private static final String METRIC_LIFECYCLE_CREATED = "actor.lifecycle.created";
    private static final String METRIC_LIFECYCLE_TERMINATED = "actor.lifecycle.terminated";
    private static final String METRIC_LIFECYCLE_RESTARTS = "actor.lifecycle.restarts";
    private static final String METRIC_LIFECYCLE_RESUMES = "actor.lifecycle.resumes";

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public String description() {
        return "Actor lifecycle metrics (created, terminated, active)";
    }

    @Override
    public void initialize(MetricsRegistry metricsRegistry) {
        MetricsContext ctx = metricsRegistry.getContext();
        Tags tags = metricsRegistry.getGlobalTags();
        metricsRegistry.getBackend().gauge(METRIC_LIFECYCLE_ACTIVE, tags, ctx.getActiveActorCount()::get);
        logger.info("Actor Lifecycle Module initialized");
    }

    /**
     * Apply instrumentation to AgentBuilder.
     * This is called by the MetricsAgent during bytecode transformation.
     */
    @Override
    public AgentBuilder instrument(AgentBuilder builder) {
        return builder.type(ElementMatchers.hasSuperType(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                        .and(ElementMatchers.not(ElementMatchers.isInterface())))
                .transform((builderParam, typeDescription, classLoader, module) -> builderParam
                        // Instrument newActor() for actor creation (called after actor instance is created)
                        .visit(Advice.to(ActorCreatedAdvice.class).on(ElementMatchers.named("newActor")))
                        // Instrument terminate() for actor termination
                        .visit(Advice.to(ActorTerminatedAdvice.class).on(ElementMatchers.named("terminate")))
                        // Instrument faultRecreate() for restarts (Pekko FaultHandling trait)
                        .visit(Advice.to(FaultRecreateAdvice.class)
                                .on(ElementMatchers.named("faultRecreate").and(ElementMatchers.takesArguments(1))))
                        // Instrument faultResume() for Resume supervision decision
                        .visit(Advice.to(FaultResumeAdvice.class)
                                .on(ElementMatchers.named("faultResume").and(ElementMatchers.takesArguments(1)))));
    }

    /**
     * ByteBuddy advice for actor creation.
     */
    public static class ActorCreatedAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Object actorCell) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null || !reg.isModuleEnabled(MODULE_ID)) return;
                ActorContext context = ActorContext.from(actorCell);
                if (!reg.shouldInstrument(context)) return;

                reg.getContext().getActiveActorCount().incrementAndGet();

                Tags tags = context.toTags().and(reg.getGlobalTags());
                reg.getBackend().counter(METRIC_LIFECYCLE_CREATED, tags).increment();
            } catch (Exception e) {
                errorLog.error(logger, "Error recording actor creation metric", e);
            }
        }
    }

    /**
     * ByteBuddy advice for actor termination.
     */
    public static class ActorTerminatedAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object actorCell) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null || !reg.isModuleEnabled(MODULE_ID)) return;
                ActorContext context = ActorContext.from(actorCell);
                if (!reg.shouldInstrument(context)) return;

                reg.getContext().getActiveActorCount().decrementAndGet();

                Tags tags = context.toTags().and(reg.getGlobalTags());
                reg.getBackend().counter(METRIC_LIFECYCLE_TERMINATED, tags).increment();
            } catch (Exception e) {
                errorLog.error(logger, "Error recording actor termination metric", e);
            }
        }
    }

    /** Records actor.lifecycle.restarts on supervision Restart (faultRecreate). */
    public static class FaultRecreateAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object actorCell) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null || !reg.isModuleEnabled(MODULE_ID)) return;
                ActorContext context = ActorContext.from(actorCell);
                if (!reg.shouldInstrument(context)) return;

                Tags tags = context.toTags().and(reg.getGlobalTags());
                reg.getBackend().counter(METRIC_LIFECYCLE_RESTARTS, tags).increment();
            } catch (Exception e) {
                errorLog.error(logger, "Error recording actor restart metric", e);
            }
        }
    }

    /** Records actor.lifecycle.resumes on supervision Resume (faultResume). */
    public static class FaultResumeAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object actorCell) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null || !reg.isModuleEnabled(MODULE_ID)) return;
                ActorContext context = ActorContext.from(actorCell);
                if (!reg.shouldInstrument(context)) return;

                Tags tags = context.toTags().and(reg.getGlobalTags());
                reg.getBackend().counter(METRIC_LIFECYCLE_RESUMES, tags).increment();
            } catch (Exception e) {
                errorLog.error(logger, "Error recording actor resume metric", e);
            }
        }
    }
}
