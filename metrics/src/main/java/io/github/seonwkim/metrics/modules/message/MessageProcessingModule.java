package io.github.seonwkim.metrics.modules.message;

import io.github.seonwkim.metrics.api.ActorContext;
import io.github.seonwkim.metrics.api.InstrumentationModule;
import io.github.seonwkim.metrics.core.MetricsRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks message processing time and message counts.
 */
public class MessageProcessingModule implements InstrumentationModule {

    private static final Logger logger = LoggerFactory.getLogger(MessageProcessingModule.class);
    private static final String MODULE_ID = "message-processing";
    private static final String METRIC_MESSAGE_PROCESSING_TIME = "actor.message.processing.time";
    private static final String METRIC_MESSAGE_PROCESSED = "actor.message.processed";

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public void initialize(MetricsRegistry metricsRegistry) {}

    @Override
    public void shutdown() {}

    public static AgentBuilder instrument(AgentBuilder builder) {
        return builder.type(ElementMatchers.named("org.apache.pekko.actor.ActorCell"))
                .transform((builderParam, typeDescription, classLoader, module) ->
                        builderParam.visit(Advice.to(InvokeAdvice.class).on(ElementMatchers.named("invoke"))));
    }

    public static class InvokeAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static long onEnter(
                @Advice.This Object actorCell,
                @Advice.Argument(0) Object envelope,
                @Advice.Local("messageType") String messageType) {
            try {
                MetricsRegistry reg = io.github.seonwkim.metrics.agent.MetricsAgent.getRegistry();
                if (reg == null || !reg.shouldInstrument(ActorContext.from(actorCell))) {
                    return System.nanoTime();
                }

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
                MetricsRegistry reg = io.github.seonwkim.metrics.agent.MetricsAgent.getRegistry();
                if (reg == null) return;

                ActorContext context = ActorContext.from(actorCell);
                if (!reg.shouldInstrument(context)) return;

                if (messageType == null) {
                    try {
                        Object message =
                                envelope.getClass().getMethod("message").invoke(envelope);
                        messageType = message.getClass().getSimpleName();
                    } catch (Exception ex) {
                        messageType = "unknown";
                    }
                }

                List<Tag> tags = new ArrayList<>(context.toTags());
                tags.add(Tag.of("message.type", messageType));
                reg.getGlobalTags().forEach(tags::add);

                Timer.builder(METRIC_MESSAGE_PROCESSING_TIME)
                        .tags(tags)
                        .register(reg.getMeterRegistry())
                        .record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);

                Counter.builder(METRIC_MESSAGE_PROCESSED)
                        .tags(tags)
                        .register(reg.getMeterRegistry())
                        .increment();
            } catch (Exception e) {
            }
        }
    }
}
