package io.github.seonwkim.metrics.modules.message;

import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.api.Tags;
import io.github.seonwkim.metrics.api.instruments.Counter;
import io.github.seonwkim.metrics.api.instruments.Timer;
import io.github.seonwkim.metrics.core.MetricsRegistry;
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
 * Tags: actor.class, message.type (low cardinality to avoid time-series explosion)
 *
 * Note: Error/exception tracking is intentionally NOT included here because Pekko's
 * supervisor catches exceptions before invoke() completes. Error tracking should be
 * implemented in a separate Supervision module that instruments ActorCell.handleInvokeFailure()
 * or supervision strategy decision points. See ENHANCEMENT.md Phase 1 for details.
 */
public class MessageProcessingModule implements InstrumentationModule {

    private static final Logger logger = LoggerFactory.getLogger(MessageProcessingModule.class);
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
        logger.info("Initializing Message Processing Module");
        logger.info("Message Processing Module initialized");
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down Message Processing Module");
    }

    /**
     * Apply instrumentation to AgentBuilder.
     * This is called by the MetricsAgent during bytecode transformation.
     */
    public static AgentBuilder instrument(AgentBuilder builder) {
        return builder.type(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                .transform((builderParam, typeDescription, classLoader, module) -> builderParam
                        // Instrument invoke() for message processing
                        .visit(Advice.to(InvokeAdvice.class).on(ElementMatchers.named("invoke"))));
    }

    /**
     * ByteBuddy advice for message processing (invoke method).
     */
    public static class InvokeAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static long onEnter(
                @Advice.This Object actorCell,
                @Advice.Argument(0) Object envelope,
                @Advice.Local("messageType") String messageType) {
            try {
                // Get registry from MetricsAgent (not from static field)
                MetricsRegistry reg = io.github.seonwkim.metrics.agent.MetricsAgent.getRegistry();
                if (reg == null) {
                    return System.nanoTime();
                }

                ActorContext context = ActorContext.from(actorCell);

                // Check filtering, sampling, and business rules (skips system/temporary actors)
                if (!reg.shouldInstrument(context)) {
                    return System.nanoTime();
                }

                // Extract message type inline (ByteBuddy can't inline method calls properly)
                try {
                    Object message = envelope.getClass().getMethod("message").invoke(envelope);
                    messageType = message.getClass().getSimpleName();
                } catch (Exception ex) {
                    messageType = "unknown";
                }

                return System.nanoTime();
            } catch (Exception e) {
                return System.nanoTime();
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(
                @Advice.This Object actorCell,
                @Advice.Argument(0) Object envelope,
                @Advice.Enter long startTime,
                @Advice.Local("messageType") String messageType) {
            try {
                // Get registry from MetricsAgent (not from static field)
                MetricsRegistry reg = io.github.seonwkim.metrics.agent.MetricsAgent.getRegistry();
                if (reg == null) {
                    return;
                }

                long endTime = System.nanoTime();
                long durationNanos = endTime - startTime;

                ActorContext context = ActorContext.from(actorCell);

                // Check filtering, sampling, and business rules (skips system/temporary actors)
                if (!reg.shouldInstrument(context)) {
                    return;
                }

                // Extract messageType if not set in onEnter
                // (can happen if filtering logic differs between enter/exit)
                if (messageType == null) {
                    try {
                        Object message =
                                envelope.getClass().getMethod("message").invoke(envelope);
                        messageType = message.getClass().getSimpleName();
                    } catch (Exception ex) {
                        messageType = "unknown";
                    }
                }

                // Use low cardinality tags: actor.class and message.type
                Tags baseTags =
                        context.toTags().and("message.type", messageType).and(reg.getGlobalTags());

                // Record processing time
                Timer processingTimer = reg.getBackend().timer(METRIC_MESSAGE_PROCESSING_TIME, baseTags);
                processingTimer.record(durationNanos, TimeUnit.NANOSECONDS);

                // Record processed message count
                Counter processedCounter = reg.getBackend().counter(METRIC_MESSAGE_PROCESSED, baseTags);
                processedCounter.increment();
            } catch (Exception e) {
                // Silently fail - don't disrupt actor system
            }
        }
    }
}
