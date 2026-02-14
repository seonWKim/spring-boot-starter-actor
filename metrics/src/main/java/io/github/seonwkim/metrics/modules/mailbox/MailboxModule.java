package io.github.seonwkim.metrics.modules.mailbox;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.api.Tags;
import io.github.seonwkim.metrics.core.MetricsContext;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.github.seonwkim.metrics.util.RateLimitedLog;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instrumentation module for mailbox metrics.
 *
 * Tracks:
 * - actor.mailbox.size (gauge) - current size, aggregated per actor class
 * - actor.mailbox.size.max (gauge) - peak size ever reached, per actor class
 * - actor.mailbox.time (timer - time from enqueue to dequeue)
 * - actor.mailbox.overflow (counter) - messages dropped when bounded mailbox is full
 *
 * Tags: actor.class, message.type (low cardinality to avoid time-series explosion)
 */
public class MailboxModule implements InstrumentationModule {

    private static final Logger logger = LoggerFactory.getLogger(MailboxModule.class);
    private static final RateLimitedLog errorLog = new RateLimitedLog(5000);
    private static final String MODULE_ID = "mailbox";

    // Metric names
    private static final String METRIC_MAILBOX_SIZE = "actor.mailbox.size";
    private static final String METRIC_MAILBOX_SIZE_MAX = "actor.mailbox.size.max";
    private static final String METRIC_MAILBOX_TIME = "actor.mailbox.time";
    private static final String METRIC_MAILBOX_OVERFLOW = "actor.mailbox.overflow";

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public String description() {
        return "Mailbox metrics (size, enqueue-to-dequeue time)";
    }

    @Override
    public void initialize(MetricsRegistry metricsRegistry) {
        logger.info("Mailbox Module initialized");
    }

    /**
     * Helper method to register mailbox size and max gauges for an actor class.
     * Uses putIfAbsent to ensure exactly one gauge registration per actor class,
     * avoiding duplicate gauges under concurrent actor creation.
     * Must be public for ByteBuddy advice inlining.
     */
    public static void registerMailboxGauge(String actorClass, MetricsRegistry registry) {
        MetricsContext ctx = registry.getContext();
        ConcurrentHashMap<String, AtomicLong> sizes = ctx.getMailboxSizes();
        ConcurrentHashMap<String, AtomicLong> maxSizes = ctx.getMailboxSizesMax();

        // Only register gauges if we won the race (first to add this actor class)
        if (sizes.putIfAbsent(actorClass, new AtomicLong(0)) != null) return;
        maxSizes.putIfAbsent(actorClass, new AtomicLong(0));

        Tags tags = Tags.of("actor.class", actorClass).and(registry.getGlobalTags());
        registry.getBackend().gauge(METRIC_MAILBOX_SIZE, tags, () -> {
            AtomicLong current = sizes.get(actorClass);
            return current != null ? current.get() : 0L;
        });
        registry.getBackend().gauge(METRIC_MAILBOX_SIZE_MAX, tags, () -> {
            AtomicLong max = maxSizes.get(actorClass);
            return max != null ? max.get() : 0L;
        });
    }

    /**
     * Apply instrumentation to AgentBuilder.
     * This is called by the MetricsAgent during bytecode transformation.
     */
    @Override
    public AgentBuilder instrument(AgentBuilder builder) {
        return builder
                // Instrument Envelope constructor to track when messages are enqueued
                .type(ElementMatchers.named("org.apache.pekko.dispatch.Envelope"))
                .transform((builderParam, typeDescription, classLoader, module) -> builderParam
                        .visit(Advice.to(EnvelopeCreatedAdvice.class).on(ElementMatchers.isConstructor()))
                        .visit(Advice.to(EnvelopeCopiedAdvice.class).on(ElementMatchers.named("copy"))))
                // Instrument Dispatch.sendMessage to track enqueue
                .type(ElementMatchers.named("org.apache.pekko.actor.dungeon.Dispatch"))
                .transform((builderParam, typeDescription, classLoader, module) ->
                        builderParam.visit(Advice.to(SendMessageAdvice.class)
                                .on(ElementMatchers.named("sendMessage").and(ElementMatchers.takesArguments(1)))))
                // Instrument ActorCell.invoke(Envelope) to track dequeue and processing
                .type(ElementMatchers.hasSuperType(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                        .and(ElementMatchers.not(ElementMatchers.isInterface())))
                .transform((builderParam, typeDescription, classLoader, module) ->
                        builderParam.visit(Advice.to(MailboxProcessAdvice.class)
                                .on(ElementMatchers.named("invoke").and(ElementMatchers.takesArguments(1)))))
                // Instrument AbstractBoundedNodeQueue.add - when false, mailbox overflow (NonBlockingBoundedMailbox)
                .type(ElementMatchers.named("org.apache.pekko.dispatch.AbstractBoundedNodeQueue"))
                .transform((builderParam, typeDescription, classLoader, module) ->
                        builderParam.visit(Advice.to(BoundedQueueAddAdvice.class)
                                .on(ElementMatchers.named("add").and(ElementMatchers.takesArguments(1)))));
    }

    /**
     * ByteBuddy advice for envelope creation.
     */
    public static class EnvelopeCreatedAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Object envelope) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) return;
                reg.getContext().getEnvelopeTimestamps().put(envelope, System.nanoTime());
            } catch (Exception e) {
                errorLog.error(logger, "Error recording envelope timestamp", e);
            }
        }
    }

    /**
     * ByteBuddy advice for envelope copy.
     */
    public static class EnvelopeCopiedAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Object oldEnvelope, @Advice.Return Object newEnvelope) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) return;
                Map<Object, Long> timestamps = reg.getContext().getEnvelopeTimestamps();
                Long timestamp = timestamps.get(oldEnvelope);
                if (timestamp != null) {
                    timestamps.put(newEnvelope, timestamp);
                }
            } catch (Exception e) {
                errorLog.error(logger, "Error copying envelope timestamp", e);
            }
        }
    }

    /** Records envelope timestamp on enqueue and increments per-class mailbox size. */
    public static class SendMessageAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object actorCell, @Advice.Argument(0) Object envelope) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) return;

                MetricsContext ctx = reg.getContext();
                ctx.getEnvelopeTimestamps().putIfAbsent(envelope, System.nanoTime());

                ActorContext context = ActorContext.from(actorCell);
                if (!reg.shouldInstrument(context)) return;

                String actorClass = context.getActorClass();
                ConcurrentHashMap<String, AtomicLong> mailboxSizes = ctx.getMailboxSizes();
                AtomicLong size = mailboxSizes.get(actorClass);
                if (size == null) {
                    registerMailboxGauge(actorClass, reg);
                    size = mailboxSizes.get(actorClass);
                }
                if (size != null) {
                    long current = size.incrementAndGet();
                    AtomicLong max = ctx.getMailboxSizesMax().get(actorClass);
                    if (max != null) max.accumulateAndGet(current, Math::max);
                }
            } catch (Exception e) {
                errorLog.error(logger, "Error recording mailbox enqueue metric", e);
            }
        }
    }

    /**
     * ByteBuddy advice for message processing (dequeue).
     * Performs its own registry lookup — simple and reliable.
     */
    public static class MailboxProcessAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object actorCell, @Advice.Argument(0) Object envelope) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) return;
                ActorContext actorContext = ActorContext.from(actorCell);
                if (!reg.shouldInstrument(actorContext)) {
                    return;
                }

                MetricsContext ctx = reg.getContext();
                String actorClass = actorContext.getActorClass();

                // Decrement mailbox size (per actor class)
                AtomicLong size = ctx.getMailboxSizes().get(actorClass);
                if (size != null) {
                    size.decrementAndGet();
                }

                // Calculate mailbox time (enqueue to dequeue)
                Long enqueueTime = ctx.getEnvelopeTimestamps().remove(envelope);
                if (enqueueTime != null) {
                    long dequeueTime = System.nanoTime();
                    long mailboxTimeNanos = dequeueTime - enqueueTime;

                    // Extract message type inline (ByteBuddy can't inline method calls properly)
                    String messageType;
                    try {
                        Object message =
                                envelope.getClass().getMethod("message").invoke(envelope);
                        messageType = message.getClass().getSimpleName();
                    } catch (Exception ex) {
                        messageType = "unknown";
                    }

                    Tags tags = actorContext
                            .toTags()
                            .and("message.type", messageType)
                            .and(reg.getGlobalTags());
                    reg.getBackend().timer(METRIC_MAILBOX_TIME, tags).record(mailboxTimeNanos, TimeUnit.NANOSECONDS);
                }
            } catch (Exception e) {
                errorLog.error(logger, "Error recording mailbox time/dequeue metric", e);
            }
        }
    }

    /**
     * ByteBuddy advice for AbstractBoundedNodeQueue.add - records overflow when add returns false.
     * Used by NonBlockingBoundedMailbox (BoundedNodeMessageQueue) when full.
     */
    public static class BoundedQueueAddAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.Return boolean added) {
            try {
                if (added) {
                    return;
                }
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) return;
                reg.getBackend()
                        .counter(METRIC_MAILBOX_OVERFLOW, reg.getGlobalTags())
                        .increment();
            } catch (Exception e) {
                errorLog.error(logger, "Error recording mailbox overflow metric", e);
            }
        }
    }
}
