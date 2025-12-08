package io.github.seonwkim.metrics.modules.mailbox;

import io.github.seonwkim.metrics.agent.MetricsAgent;
import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
 * Tracks mailbox size and enqueue-to-dequeue time metrics.
 */
public class MailboxModule implements InstrumentationModule {

    private static final Logger logger = LoggerFactory.getLogger(MailboxModule.class);
    private static final String MODULE_ID = "mailbox";
    private static final String METRIC_MAILBOX_SIZE = "actor.mailbox.size";
    private static final String METRIC_MAILBOX_TIME = "actor.mailbox.time";

    public static final Map<Object, Long> envelopeTimestamps = Collections.synchronizedMap(new WeakHashMap<>());
    public static final Map<String, AtomicLong> mailboxSizes = new ConcurrentHashMap<>();

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public void initialize(MetricsRegistry metricsRegistry) {}

    @Override
    public void shutdown() {
        envelopeTimestamps.clear();
        mailboxSizes.clear();
    }

    public static void registerMailboxGauge(String actorClass, MetricsRegistry registry) {
        AtomicLong newSize = new AtomicLong(0);
        mailboxSizes.put(actorClass, newSize);

        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("actor.class", actorClass));
        registry.getGlobalTags().forEach(tags::add);

        final String clazz = actorClass;
        Gauge.builder(METRIC_MAILBOX_SIZE, () -> {
                    AtomicLong current = mailboxSizes.get(clazz);
                    return current != null ? current.get() : 0.0;
                })
                .tags(tags)
                .register(registry.getMeterRegistry());
    }

    public static AgentBuilder instrument(AgentBuilder builder) {
        return builder.type(ElementMatchers.named("org.apache.pekko.dispatch.Envelope"))
                .transform((builderParam, typeDescription, classLoader, module) -> builderParam
                        .visit(Advice.to(EnvelopeCreatedAdvice.class).on(ElementMatchers.isConstructor()))
                        .visit(Advice.to(EnvelopeCopiedAdvice.class).on(ElementMatchers.named("copy"))))
                .type(ElementMatchers.named("org.apache.pekko.actor.dungeon.Dispatch"))
                .transform((builderParam, typeDescription, classLoader, module) ->
                        builderParam.visit(Advice.to(SendMessageAdvice.class)
                                .on(ElementMatchers.named("sendMessage").and(ElementMatchers.takesArguments(1)))))
                .type(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                .transform((builderParam, typeDescription, classLoader, module) ->
                        builderParam.visit(Advice.to(MailboxProcessAdvice.class).on(ElementMatchers.named("invoke"))));
    }

    public static class EnvelopeCreatedAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Object envelope) {
            try {
                envelopeTimestamps.put(envelope, System.nanoTime());
            } catch (Exception e) {
            }
        }
    }

    public static class EnvelopeCopiedAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(@Advice.This Object oldEnvelope, @Advice.Return Object newEnvelope) {
            try {
                Long timestamp = envelopeTimestamps.get(oldEnvelope);
                if (timestamp != null) {
                    envelopeTimestamps.put(newEnvelope, timestamp);
                }
            } catch (Exception e) {
            }
        }
    }

    public static class SendMessageAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object actorCell, @Advice.Argument(0) Object envelope) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) return;

                if (!envelopeTimestamps.containsKey(envelope)) {
                    envelopeTimestamps.put(envelope, System.nanoTime());
                }

                ActorContext context = ActorContext.from(actorCell);
                if (!reg.shouldInstrument(context)) return;

                String actorClass = context.getActorClass();
                AtomicLong size = mailboxSizes.get(actorClass);
                if (size == null) {
                    registerMailboxGauge(actorClass, reg);
                    size = mailboxSizes.get(actorClass);
                }
                if (size != null) {
                    size.incrementAndGet();
                }
            } catch (Exception e) {
            }
        }
    }

    public static class MailboxProcessAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.This Object actorCell, @Advice.Argument(0) Object envelope) {
            try {
                MetricsRegistry reg = MetricsAgent.getRegistry();
                if (reg == null) return;

                ActorContext context = ActorContext.from(actorCell);
                if (!reg.shouldInstrument(context)) return;

                String actorClass = context.getActorClass();
                AtomicLong size = mailboxSizes.get(actorClass);
                if (size != null) {
                    size.decrementAndGet();
                }

                Long enqueueTime = envelopeTimestamps.remove(envelope);
                if (enqueueTime != null) {
                    String messageType;
                    try {
                        Object message =
                                envelope.getClass().getMethod("message").invoke(envelope);
                        messageType = message.getClass().getSimpleName();
                    } catch (Exception ex) {
                        messageType = "unknown";
                    }

                    List<Tag> tags = new ArrayList<>(context.toTags());
                    tags.add(Tag.of("message.type", messageType));
                    reg.getGlobalTags().forEach(tags::add);

                    Timer.builder(METRIC_MAILBOX_TIME)
                            .tags(tags)
                            .register(reg.getMeterRegistry())
                            .record(System.nanoTime() - enqueueTime, TimeUnit.NANOSECONDS);
                }
            } catch (Exception e) {
            }
        }
    }
}
