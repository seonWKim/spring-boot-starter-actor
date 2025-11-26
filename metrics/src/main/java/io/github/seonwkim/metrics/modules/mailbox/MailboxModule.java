package io.github.seonwkim.metrics.modules.mailbox;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.api.Tags;
import io.github.seonwkim.metrics.api.instruments.Timer;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
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
 * - actor.mailbox.size (gauge) - aggregated per actor class
 * - actor.mailbox.time (timer - time from enqueue to dequeue)
 *
 * Tags: actor.class, message.type (low cardinality to avoid time-series explosion)
 */
public class MailboxModule implements InstrumentationModule {

    private static final Logger logger = LoggerFactory.getLogger(MailboxModule.class);
    private static final String MODULE_ID = "mailbox";

    // Metric names
    private static final String METRIC_MAILBOX_SIZE = "actor.mailbox.size";
    private static final String METRIC_MAILBOX_TIME = "actor.mailbox.time";

    // WeakHashMap to store envelope timestamps without preventing GC
    // Must be public for ByteBuddy inline advice access
    // Synchronized for thread-safety in concurrent actor system
    public static final Map<Object, Long> envelopeTimestamps = Collections.synchronizedMap(new WeakHashMap<>());

    // Track mailbox sizes per actor class (not per instance to avoid high cardinality)
    // Must be public for ByteBuddy inline advice access
    public static final Map<String, AtomicLong> mailboxSizes = new ConcurrentHashMap<>();

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
        logger.info("Initializing Mailbox Module");
        // Note: We don't store registry as static field - use MetricsAgent.getRegistry() instead
        logger.info("Mailbox Module initialized");
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down Mailbox Module");
        envelopeTimestamps.clear();
        mailboxSizes.clear();
    }

    /**
     * Helper method to register a mailbox size gauge for an actor class.
     * This is called from ByteBuddy advice and MUST be public static.
     */
    public static void registerMailboxGauge(String actorClass, MetricsRegistry registry) {
        AtomicLong newSize = new AtomicLong(0);
        mailboxSizes.put(actorClass, newSize);

        Tags tags = Tags.of("actor.class", actorClass).and(registry.getGlobalTags());

        // Register gauge that reads from the static map
        final String clazz = actorClass;
        registry.getBackend().gauge(METRIC_MAILBOX_SIZE, tags, () -> {
            AtomicLong current = mailboxSizes.get(clazz);
            return current != null ? current.get() : 0L;
        });
    }

    /**
     * Apply instrumentation to AgentBuilder.
     * This is called by the MetricsAgent during bytecode transformation.
     */
    public static AgentBuilder instrument(AgentBuilder builder) {
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
                // Instrument ActorCell.invoke to track dequeue and processing
                .type(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                .transform((builderParam, typeDescription, classLoader, module) ->
                        builderParam.visit(Advice.to(MailboxProcessAdvice.class).on(ElementMatchers.named("invoke"))));
    }

    /**
     * ByteBuddy advice for envelope creation.
     */
    public static class EnvelopeCreatedAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Object envelope) {
            try {
                long timestamp = System.nanoTime();
                envelopeTimestamps.put(envelope, timestamp);
            } catch (Exception e) {
                // Silently fail - don't disrupt actor system
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
                Long timestamp = envelopeTimestamps.get(oldEnvelope);
                if (timestamp != null) {
                    envelopeTimestamps.put(newEnvelope, timestamp);
                }
            } catch (Exception e) {
                // Silently fail - don't disrupt actor system
            }
        }
    }

    /**
     * ByteBuddy advice for message send (enqueue).
     */
    public static class SendMessageAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object actorCell, @Advice.Argument(0) Object envelope) {
            try {
                // Get registry from MetricsAgent (not from static field)
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) {
                    return;
                }

                // Ensure envelope has a timestamp
                if (!envelopeTimestamps.containsKey(envelope)) {
                    envelopeTimestamps.put(envelope, System.nanoTime());
                }

                ActorContext context = ActorContext.from(actorCell);

                // Check filtering, sampling, and business rules (skips system/temporary actors)
                if (!reg.shouldInstrument(context)) {
                    return;
                }

                String actorClass = context.getActorClass();

                // Increment mailbox size (per actor class, aggregating all instances)
                AtomicLong size = mailboxSizes.get(actorClass);
                if (size == null) {
                    // Register gauge via helper method (avoids lambda issues in ByteBuddy)
                    registerMailboxGauge(actorClass, reg);
                    size = mailboxSizes.get(actorClass);
                }
                if (size != null) {
                    size.incrementAndGet();
                }
            } catch (Exception e) {
                // Silently fail - don't disrupt actor system
            }
        }
    }

    /**
     * ByteBuddy advice for message processing (dequeue).
     */
    public static class MailboxProcessAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object actorCell, @Advice.Argument(0) Object envelope) {
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

                String actorClass = context.getActorClass();

                // Decrement mailbox size (per actor class)
                AtomicLong size = mailboxSizes.get(actorClass);
                if (size != null) {
                    size.decrementAndGet();
                }

                // Calculate mailbox time (enqueue to dequeue)
                Long enqueueTime = envelopeTimestamps.remove(envelope);
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

                    // Use low cardinality tags: actor.class and message.type
                    Tags tags =
                            context.toTags().and("message.type", messageType).and(reg.getGlobalTags());

                    Timer mailboxTimer = reg.getBackend().timer(METRIC_MAILBOX_TIME, tags);
                    mailboxTimer.record(mailboxTimeNanos, TimeUnit.NANOSECONDS);
                }
            } catch (Exception e) {
                // Silently fail - don't disrupt actor system
            }
        }
    }
}
