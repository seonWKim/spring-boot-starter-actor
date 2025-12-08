package io.github.seonwkim.metrics.modules.actor;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks actor lifecycle metrics: created, terminated, and active counts.
 */
public class ActorLifecycleModule implements InstrumentationModule {

    private static final Logger logger = LoggerFactory.getLogger(ActorLifecycleModule.class);
    private static final String MODULE_ID = "actor-lifecycle";
    private static final String METRIC_LIFECYCLE_ACTIVE = "actor.lifecycle.active";
    private static final String METRIC_LIFECYCLE_CREATED = "actor.lifecycle.created";
    private static final String METRIC_LIFECYCLE_TERMINATED = "actor.lifecycle.terminated";

    @Nullable private static Gauge activeGauge;

    public static final AtomicLong activeActorCount = new AtomicLong(0);

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public void initialize(MetricsRegistry metricsRegistry) {
        List<Tag> tags = new ArrayList<>();
        metricsRegistry.getGlobalTags().forEach(tags::add);

        activeGauge = Gauge.builder(METRIC_LIFECYCLE_ACTIVE, activeActorCount::get)
                .tags(tags)
                .register(metricsRegistry.getMeterRegistry());
    }

    @Override
    public void shutdown() {
        activeGauge = null;
        activeActorCount.set(0);
    }

    public static AgentBuilder instrument(AgentBuilder builder) {
        return builder.type(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                .transform((builderParam, typeDescription, classLoader, module) -> builderParam
                        .visit(Advice.to(ActorCreatedAdvice.class).on(ElementMatchers.named("newActor")))
                        .visit(Advice.to(ActorTerminatedAdvice.class).on(ElementMatchers.named("terminate"))));
    }

    public static class ActorCreatedAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Object actorCell) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) return;

                ActorContext context = ActorContext.from(actorCell);
                if (!reg.shouldInstrument(context)) return;

                activeActorCount.incrementAndGet();

                List<Tag> tags = new ArrayList<>(context.toTags());
                reg.getGlobalTags().forEach(tags::add);

                Counter.builder(METRIC_LIFECYCLE_CREATED)
                        .tags(tags)
                        .register(reg.getMeterRegistry())
                        .increment();
            } catch (Exception e) {
                logger.error("Error recording actor creation metric", e);
            }
        }
    }

    public static class ActorTerminatedAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object actorCell) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) return;

                ActorContext context = ActorContext.from(actorCell);
                if (!reg.shouldInstrument(context)) return;

                activeActorCount.decrementAndGet();

                List<Tag> tags = new ArrayList<>(context.toTags());
                reg.getGlobalTags().forEach(tags::add);

                Counter.builder(METRIC_LIFECYCLE_TERMINATED)
                        .tags(tags)
                        .register(reg.getMeterRegistry())
                        .increment();
            } catch (Exception e) {
                logger.error("Error recording actor termination metric", e);
            }
        }
    }
}
