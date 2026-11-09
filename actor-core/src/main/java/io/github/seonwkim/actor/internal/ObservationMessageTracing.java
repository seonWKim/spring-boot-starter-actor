package io.github.seonwkim.actor.internal;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.SenderContext;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * {@link MessageTracing} through Micrometer Observation. A send is an {@code actor.send} observation
 * (producer) whose tracer writes the trace fields into the message; the receiving actor handles the
 * message inside an {@code actor.receive} observation (consumer) continued from those fields, so the
 * trace id is in the MDC of the actor's logs. Messages sent outside any observation stay untraced.
 */
public final class ObservationMessageTracing implements MessageTracing {

    private final ObservationRegistry registry;

    public ObservationMessageTracing(ObservationRegistry registry) {
        this.registry = registry;
    }

    @Override
    @Nullable public Sent send(Object message, String operation) {
        if (registry.getCurrentObservation() == null) {
            return null;
        }
        Map<String, String> headers = new HashMap<>();
        SenderContext<Map<String, String>> context = new SenderContext<>(
                (carrier, key, value) -> {
                    if (carrier != null) {
                        carrier.put(key, value);
                    }
                },
                Kind.PRODUCER);
        context.setCarrier(headers);
        Observation observation = Observation.createNotStarted("actor.send", () -> context, registry)
                .contextualName(operation + " " + message.getClass().getSimpleName())
                .lowCardinalityKeyValue("actor.operation", operation)
                .lowCardinalityKeyValue("actor.message", message.getClass().getName())
                .start();
        if (headers.isEmpty()) {
            // No tracer wrote a trace context: nothing to carry.
            observation.stop();
            return null;
        }
        TracedMessage traced = new TracedMessage(message, headers);
        return new Sent() {
            @Override
            public TracedMessage message() {
                return traced;
            }

            @Override
            public void stop(@Nullable Throwable error) {
                if (error != null) {
                    observation.error(error);
                }
                observation.stop();
            }
        };
    }

    @Override
    public <R> R receive(TracedMessage message, String actorPath, Supplier<R> handler) {
        ReceiverContext<Map<String, String>> context =
                new ReceiverContext<>((carrier, key) -> carrier.get(key), Kind.CONSUMER);
        context.setCarrier(message.headers());
        return Observation.createNotStarted("actor.receive", () -> context, registry)
                .contextualName("receive " + message.message().getClass().getSimpleName())
                .lowCardinalityKeyValue(
                        "actor.message", message.message().getClass().getName())
                .highCardinalityKeyValue("actor.path", actorPath)
                .observe(handler);
    }
}
