package io.github.seonwkim.example;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.pekko.dispatch.Envelope;
import org.springframework.stereotype.Component;

import io.github.seonwkim.metrics.ActorInstrumentationEventListener;
import io.github.seonwkim.metrics.ActorInstrumentationEventListener.InvokeAdviceEventListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class ActorClusterMetricsExporter {

    private final MeterRegistry registry;
    ConcurrentHashMap<String, Timer> invokeTimers = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, Counter> invokeCounters = new ConcurrentHashMap<>();

    private final Set<Class<?>> targetClasses = Set.of(
            ChatRoomActor.JoinRoom.class,
            ChatRoomActor.LeaveRoom.class,
            ChatRoomActor.SendMessage.class,
            ChatRoomActor.ChatEvent.class,
            ChatRoomActor.UserJoined.class,
            ChatRoomActor.UserLeft.class
    );

    public ActorClusterMetricsExporter(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void registerMetrics() {
        ActorInstrumentationEventListener.register(new InvokeAdviceEventListener() {
            @Override
            public void onEnter(Envelope envelope) {}

            @Override
            public void onExit(Envelope envelope, long startTime, Throwable throwable) {
                if (!targetClasses.contains(envelope.message().getClass())) {
                    return;
                }

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
    }
}
