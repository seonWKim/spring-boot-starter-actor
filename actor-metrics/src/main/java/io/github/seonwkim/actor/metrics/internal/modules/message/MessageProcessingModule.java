package io.github.seonwkim.actor.metrics.internal.modules.message;

import io.github.seonwkim.actor.metrics.agent.MetricsAgent;
import io.github.seonwkim.actor.metrics.api.ActorContext;
import io.github.seonwkim.actor.metrics.api.InstrumentationModule;
import io.github.seonwkim.actor.metrics.api.Tags;
import io.github.seonwkim.actor.metrics.core.MetricsContext;
import io.github.seonwkim.actor.metrics.core.MetricsRegistry;
import io.github.seonwkim.actor.metrics.internal.ActorCells;
import io.github.seonwkim.actor.metrics.util.RateLimitedLog;
import java.util.concurrent.TimeUnit;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrumentation module for message processing metrics.
 *
 * Tracks:
 * - actor.message.processing.time (timer)
 * - actor.message.processed (counter)
 *
 * Tags: actor.class, message.type (low cardinality)
 *
 * <p>Failures are counted by the starter's hooks as actor.message.failures, with or without the agent.
 */
public class MessageProcessingModule implements InstrumentationModule {

    private static final Logger logger = LoggerFactory.getLogger(MessageProcessingModule.class);
    private static final RateLimitedLog errorLog = new RateLimitedLog(5000);
    private static final String MODULE_ID = "message-processing";

    // Metric names
    private static final String METRIC_MESSAGE_PROCESSING_TIME = "actor.message.processing.time";
    private static final String METRIC_MESSAGE_PROCESSED = "actor.message.processed";

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public String description() {
        return "Message processing metrics (processing time, processed count)";
    }

    @Override
    public void initialize(MetricsRegistry metricsRegistry) {
        logger.info("Message Processing Module initialized");
    }

    /**
     * Apply instrumentation to AgentBuilder.
     * This is called by the MetricsAgent during bytecode transformation.
     */
    @Override
    public AgentBuilder instrument(AgentBuilder builder) {
        return builder.type(ElementMatchers.hasSuperType(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                        .and(ElementMatchers.not(ElementMatchers.isInterface())))
                .transform((builderParam, typeDescription, classLoader, module, protectionDomain) -> builderParam
                        // Instrument invoke(Envelope) for message processing (time, processed count)
                        .visit(Advice.to(InvokeAdvice.class)
                        .on(ElementMatchers.named("invoke").and(ElementMatchers.takesArguments(1)))));
    }

    /**
     * Try message() then msg() - Pekko Envelope field names vary by Scala version.
     * Must be public for ByteBuddy advice inlining: when advice is inlined into
     * ActorCell, the inlined bytecode must be able to access this method.
     */
    public static String extractMessageType(Object envelope) {
        try {
            Object message = null;
            for (String methodName : new String[] {"message", "msg"}) {
                try {
                    message = envelope.getClass().getMethod(methodName).invoke(envelope);
                    break;
                } catch (NoSuchMethodException e) {
                    // try next
                }
            }
            return message != null ? message.getClass().getSimpleName() : "unknown";
        } catch (Exception ex) {
            return "unknown";
        }
    }

    /**
     * ByteBuddy advice for message processing (invoke method).
     *
     * <p>Uses a ThreadLocal in MetricsContext instead of {@code @Advice.Local}
     * to pass the message type from onEnter to onExit, because {@code @Advice.Local} is
     * unreliable across classloader boundaries and bytecode transformations.
     */
    public static class InvokeAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static long onEnter(@Advice.This Object actorCell, @Advice.Argument(0) Object envelope) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) {
                    return System.nanoTime();
                }
                ActorContext context = ActorCells.contextOf(actorCell);
                if (!reg.shouldInstrument(context)) {
                    return System.nanoTime();
                }

                // Extract message type and store in ThreadLocal for onExit
                String messageType = extractMessageType(envelope);
                reg.getContext().getCurrentMessageType().set(messageType);

                return System.nanoTime();
            } catch (Exception e) {
                errorLog.error(logger, "Error in message processing onEnter", e);
                return System.nanoTime();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void onExit(
                @Advice.This Object actorCell, @Advice.Argument(0) Object envelope, @Advice.Enter long startTime) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) {
                    return;
                }
                ActorContext context = ActorCells.contextOf(actorCell);
                if (!reg.shouldInstrument(context)) {
                    return;
                }

                // Always retrieve and clear ThreadLocal to prevent leaks
                MetricsContext ctx = reg.getContext();
                String messageType = ctx.getCurrentMessageType().get();
                ctx.getCurrentMessageType().remove();

                // Fallback if ThreadLocal was not set (e.g. onEnter threw)
                if (messageType == null) {
                    messageType = extractMessageType(envelope);
                }

                Tags tags = context.toTags().and("message.type", messageType).and(reg.getGlobalTags());
                long durationNanos = System.nanoTime() - startTime;

                reg.getBackend()
                        .timer(METRIC_MESSAGE_PROCESSING_TIME, tags)
                        .record(durationNanos, TimeUnit.NANOSECONDS);
                reg.getBackend().counter(METRIC_MESSAGE_PROCESSED, tags).increment();
            } catch (Exception e) {
                errorLog.error(logger, "Error recording message processing metrics", e);
            }
        }
    }
}
