package io.github.seonwkim.metrics.modules.stash;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.api.Tags;
import io.github.seonwkim.metrics.core.MetricsContext;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.util.RateLimitedLog;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrumentation module for actor stash metrics (Pekko Typed StashBuffer).
 *
 * <p>Tracks:
 * <ul>
 *   <li>actor.stash.size (gauge) - current stashed message count per actor class
 * </ul>
 *
 * <p>Uses ThreadLocal to propagate actor context from ActorCell.invoke to StashBuffer.stash.
 * Tags: actor.class (low cardinality)
 */
public class StashModule implements InstrumentationModule {

    private static final Logger logger = LoggerFactory.getLogger(StashModule.class);
    private static final RateLimitedLog errorLog = new RateLimitedLog(5000);
    private static final String MODULE_ID = "stash";

    private static final String METRIC_STASH_SIZE = "actor.stash.size";

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public String description() {
        return "Stash metrics (stashed message count)";
    }

    @Override
    public void initialize(MetricsRegistry metricsRegistry) {
        logger.info("Stash Module initialized");
    }

    /**
     * Register stash size gauge for an actor class.
     * Must be public for ByteBuddy advice inlining.
     */
    public static void registerStashGauge(String actorClass, MetricsRegistry registry) {
        ConcurrentHashMap<String, AtomicLong> stashSizes = registry.getContext().getStashSizes();
        stashSizes.putIfAbsent(actorClass, new AtomicLong(0));
        Tags tags = Tags.of("actor.class", actorClass).and(registry.getGlobalTags());
        registry.getBackend().gauge(METRIC_STASH_SIZE, tags, () -> {
            AtomicLong size = stashSizes.get(actorClass);
            return size != null ? size.get() : 0L;
        });
    }

    @Override
    public AgentBuilder instrument(AgentBuilder builder) {
        return builder
                // Set actor class when processing messages (for stash context)
                .type(ElementMatchers.hasSuperType(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                        .and(ElementMatchers.not(ElementMatchers.isInterface())))
                .transform((builderParam, typeDescription, classLoader, module) ->
                        builderParam.visit(Advice.to(ActorCellInvokeAdvice.class)
                                .on(ElementMatchers.named("invoke").and(ElementMatchers.takesArguments(1)))))
                // StashBufferImpl - Typed stash
                .type(ElementMatchers.named("org.apache.pekko.actor.typed.internal.StashBufferImpl"))
                .transform((builderParam, typeDescription, classLoader, module) -> builderParam
                        .visit(Advice.to(StashAdvice.class)
                                .on(ElementMatchers.named("stash").and(ElementMatchers.takesArguments(1))))
                        .visit(Advice.to(UnstashAllAdvice.class)
                                .on(ElementMatchers.named("unstashAll").and(ElementMatchers.takesArguments(1))))
                        .visit(Advice.to(UnstashAdvice.class)
                                .on(ElementMatchers.named("unstash").and(ElementMatchers.takesArguments(3)))));
    }

    /**
     * Set/clear actor class around ActorCell.invoke so that stash advice
     * (which runs on StashBufferImpl, not ActorCell) can identify the actor.
     */
    public static class ActorCellInvokeAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object actorCell) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null || !reg.isModuleEnabled(MODULE_ID)) return;
                ActorContext context = ActorContext.from(actorCell);
                if (!reg.shouldInstrument(context)) return;
                reg.getContext().getCurrentActorClass().set(context.getActorClass());
            } catch (Exception e) {
                errorLog.error(logger, "Error setting stash actor context", e);
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onExit() {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg != null) {
                    reg.getContext().getCurrentActorClass().remove();
                }
            } catch (Exception e) {
                // suppress
            }
        }
    }

    /** Increment stash size on stash(). */
    public static class StashAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter() {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null || !reg.isModuleEnabled(MODULE_ID)) return;
                MetricsContext ctx = reg.getContext();
                String actorClass = ctx.getCurrentActorClass().get();
                if (actorClass == null) actorClass = "unknown";
                ConcurrentHashMap<String, AtomicLong> stashSizes = ctx.getStashSizes();
                AtomicLong size = stashSizes.get(actorClass);
                if (size == null) {
                    registerStashGauge(actorClass, reg);
                    size = stashSizes.get(actorClass);
                }
                if (size != null) size.incrementAndGet();
            } catch (Exception e) {
                errorLog.error(logger, "Error recording stash metric", e);
            }
        }
    }

    /** Decrement stash size on unstashAll (all messages processed). */
    public static class UnstashAllAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object stashBuffer) {
            try {
                // We need to decrement by current size - use reflection to call size()
                int n = 0;
                try {
                    Object result = stashBuffer.getClass().getMethod("size").invoke(stashBuffer);
                    if (result instanceof Integer) n = (Integer) result;
                    else if (result instanceof Number) n = ((Number) result).intValue();
                } catch (Exception e) {
                    errorLog.error(logger, "Error reading stash buffer size", e);
                }
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null || !reg.isModuleEnabled(MODULE_ID)) return;
                MetricsContext ctx = reg.getContext();
                String actorClass = ctx.getCurrentActorClass().get();
                if (actorClass == null) actorClass = "unknown";
                AtomicLong size = ctx.getStashSizes().get(actorClass);
                if (size != null && n > 0) {
                    long updated = size.addAndGet(-n);
                    if (updated < 0) size.set(0); // Guard against underflow
                }
            } catch (Exception e) {
                errorLog.error(logger, "Error recording unstashAll metric", e);
            }
        }
    }

    /** Decrement stash size on unstash(behavior, n, wrap). */
    public static class UnstashAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.Argument(1) int numberOfMessages) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null || !reg.isModuleEnabled(MODULE_ID)) return;
                MetricsContext ctx = reg.getContext();
                String actorClass = ctx.getCurrentActorClass().get();
                if (actorClass == null) actorClass = "unknown";
                AtomicLong size = ctx.getStashSizes().get(actorClass);
                if (size != null && numberOfMessages > 0) {
                    long updated = size.addAndGet(-numberOfMessages);
                    if (updated < 0) size.set(0);
                }
            } catch (Exception e) {
                errorLog.error(logger, "Error recording unstash metric", e);
            }
        }
    }
}
