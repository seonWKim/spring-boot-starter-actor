package io.github.seonwkim.metrics.interceptor;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Registry for envelope creation event interceptors that monitors when new message envelopes are
 * created in the actor system.
 *
 * <p>Envelope creation events mark the beginning of a message's lifecycle, providing critical data
 * points for understanding message flow patterns, system behavior, and performance characteristics.
 * This is particularly valuable for debugging message routing issues and optimizing message
 * creation patterns.
 */
public class EnvelopeCreatedEventInterceptorsHolder {
    private static final Queue<EnvelopeCreatedEventInterceptor> holder = new ConcurrentLinkedQueue<>();

    public interface EnvelopeCreatedEventInterceptor {
        void onEnvelopeCreated(Object envelope, long timestamp);
    }

    public static void register(EnvelopeCreatedEventInterceptor interceptor) {
        holder.add(interceptor);
    }

    public static void unregister(EnvelopeCreatedEventInterceptor interceptor) {
        holder.remove(interceptor);
    }

    public static void onEnvelopeCreated(Object envelope, long timestamp) {
        holder.forEach(it -> it.onEnvelopeCreated(envelope, timestamp));
    }

    public static void reset() {
        holder.clear();
    }
}
