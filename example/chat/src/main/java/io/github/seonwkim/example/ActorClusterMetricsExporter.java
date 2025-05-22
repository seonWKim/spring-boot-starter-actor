package io.github.seonwkim.example;

import io.github.seonwkim.core.SpringActorSystem;
import io.github.seonwkim.metrics.ActorInstrumentationEventListener;
import io.github.seonwkim.metrics.ActorInstrumentationEventListener.InvokeAdviceEventListener;
import io.github.seonwkim.metrics.ActorInstrumentationEventListener.SystemInvokeAdviceEventListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;

import org.apache.pekko.dispatch.Envelope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class ActorClusterMetricsExporter {

    private final SpringActorSystem springActorSystem;
    private final MeterRegistry registry;
    ConcurrentHashMap<String, Timer> invokeTimers = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Counter> invokeCounters = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Timer> systemInvokeTimers = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Counter> systemInvokeCounters = new ConcurrentHashMap<>();

    public ActorClusterMetricsExporter(SpringActorSystem springActorSystem, MeterRegistry registry) {
        this.springActorSystem = springActorSystem;
        this.registry = registry;
    }

    @PostConstruct
    public void registerMetrics() {
        ActorInstrumentationEventListener.register(new InvokeAdviceEventListener() {
            @Override
            public void onEnter(Envelope envelope) {}

            @Override
            public void onExit(Envelope envelope, long startTime, Throwable throwable) {
                String messageType = envelopeMessageTypeSafe(envelope);

                Timer timer = invokeTimers.computeIfAbsent(messageType, mt ->
                        Timer.builder("pekko.actorcell.invoke.timer")
                             .description("Time spent in ActorCell.invoke(Envelope)")
                             .tags("messageType", mt)
                             .register(registry)
                );

                Counter counter = invokeCounters.computeIfAbsent(messageType, mt ->
                        Counter.builder("pekko.actorcell.invoke.count")
                               .description("Count of messages processed by ActorCell.invoke")
                               .tags("messageType", mt)
                               .register(registry)
                );

                long duration = System.nanoTime() - startTime;
                timer.record(duration, TimeUnit.NANOSECONDS);
                counter.increment();
            }

            private String envelopeMessageTypeSafe(Envelope envelope) {
                try {
                    Object msg = envelope.message();
                    return msg != null ? msg.getClass().getSimpleName() : "null";
                } catch (Throwable t) {
                    return "unknown";
                }
            }
        });

        ActorInstrumentationEventListener.register(new SystemInvokeAdviceEventListener() {
            @Override
            public void onEnter(Object systemMessage) {}

            @Override
            public void onExit(Object systemMessage, long startTime, Throwable throwable) {
                String messageType = systemMessageTypeSafe(systemMessage);

                Timer timer = systemInvokeTimers.computeIfAbsent(messageType, mt ->
                        Timer.builder("pekko.actorcell.systemInvoke.timer")
                             .description("Time spent in ActorCell.systemInvoke(SystemMessage)")
                             .tags("messageType", mt)
                             .register(registry)
                );

                Counter counter = systemInvokeCounters.computeIfAbsent(messageType, mt ->
                        Counter.builder("pekko.actorcell.systemInvoke.count")
                               .description("Count of system messages processed by ActorCell.systemInvoke")
                               .tags("messageType", mt)
                               .register(registry)
                );

                long duration = System.nanoTime() - startTime;
                timer.record(duration, TimeUnit.NANOSECONDS);
                counter.increment();
            }

            private String systemMessageTypeSafe(Object msg) {
                return msg != null ? msg.getClass().getSimpleName() : "null";
            }
        });
    }

}
