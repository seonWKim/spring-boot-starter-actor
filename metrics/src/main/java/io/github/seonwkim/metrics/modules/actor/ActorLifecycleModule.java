package io.github.seonwkim.metrics.modules.actor;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.api.Tags;
import io.github.seonwkim.metrics.api.instruments.Counter;
import io.github.seonwkim.metrics.api.instruments.Gauge;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
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
 * - actor.lifecycle.active (gauge)
 *
 * Tags: actor.path, actor.class
 */
public class ActorLifecycleModule implements InstrumentationModule {

    private static final Logger logger = LoggerFactory.getLogger(ActorLifecycleModule.class);
    private static final String MODULE_ID = "actor-lifecycle";

    // Metric names
    private static final String METRIC_LIFECYCLE_ACTIVE = "actor.lifecycle.active";
    private static final String METRIC_LIFECYCLE_CREATED = "actor.lifecycle.created";
    private static final String METRIC_LIFECYCLE_TERMINATED = "actor.lifecycle.terminated";

    // Metrics
    @Nullable private static Gauge activeGauge;

    public static final AtomicLong activeActorCount = new AtomicLong(0);

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
        logger.info("Initializing Actor Lifecycle Module");

        // Create active gauge (created/terminated counters are created per-actor with specific tags)
        Tags tags = metricsRegistry.getGlobalTags();
        activeGauge = metricsRegistry.getBackend().gauge(METRIC_LIFECYCLE_ACTIVE, tags, activeActorCount::get);

        logger.info("Actor Lifecycle Module initialized");
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down Actor Lifecycle Module");
        activeGauge = null;
        activeActorCount.set(0);
    }

    /**
     * Apply instrumentation to AgentBuilder.
     * This is called by the MetricsAgent during bytecode transformation.
     */
    public static AgentBuilder instrument(AgentBuilder builder) {
        return builder.type(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                .transform((builderParam, typeDescription, classLoader, module) -> builderParam
                        // Instrument newActor() for actor creation (called after actor instance is created)
                        .visit(Advice.to(ActorCreatedAdvice.class).on(ElementMatchers.named("newActor")))
                        // Instrument terminate() for actor termination
                        .visit(Advice.to(ActorTerminatedAdvice.class).on(ElementMatchers.named("terminate"))));
    }

    /**
     * ByteBuddy advice for actor creation.
     */
    public static class ActorCreatedAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Object actorCell) {
            try {
                // Get registry from MetricsAgent (not from static field)
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) {
                    return;
                }

                ActorContext context = ActorContext.from(actorCell);

                // Check filtering, sampling, and business rules (skips system/temporary actors)
                if (!reg.shouldInstrument(context)) {
                    return;
                }

                // Increment metrics
                activeActorCount.incrementAndGet();

                Tags tags = context.toTags().and(reg.getGlobalTags());
                Counter counter = reg.getBackend().counter(METRIC_LIFECYCLE_CREATED, tags);
                counter.increment();

                logger.debug("Actor created: {} (class: {})", context.getPath(), context.getActorClass());
            } catch (Exception e) {
                logger.error("Error recording actor creation metric", e);
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
                // Get registry from MetricsAgent (not from static field)
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) {
                    return;
                }

                ActorContext context = ActorContext.from(actorCell);

                // Check filtering, sampling, and business rules (skips system/temporary actors)
                if (!reg.shouldInstrument(context)) {
                    return;
                }

                // Decrement active count
                activeActorCount.decrementAndGet();

                Tags tags = context.toTags().and(reg.getGlobalTags());
                Counter counter = reg.getBackend().counter(METRIC_LIFECYCLE_TERMINATED, tags);
                counter.increment();

                logger.debug("Actor terminated: {}", context.getPath());
            } catch (Exception e) {
                logger.error("Error recording actor termination metric", e);
            }
        }
    }
}
