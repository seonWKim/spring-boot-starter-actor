package io.github.seonwkim.metrics.interceptor;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Registry for envelope copy event interceptors that monitors when message envelopes are duplicated
 * or transformed.
 *
 * <p>Envelope copying events indicate message duplication or transformation points, which are
 * critical for understanding message propagation patterns, identifying potential performance issues
 * from excessive copying, and ensuring message integrity during transformations. This is especially
 * important in systems with complex message routing or transformation requirements.
 */
public class EnvelopeCopiedEventInterceptorsHolder {
    private static final Queue<EnvelopeCopiedEventInterceptor> holder = new ConcurrentLinkedQueue<>();

    public interface EnvelopeCopiedEventInterceptor {
        void onEnvelopeCopied(Object oldEnvelope, Object newEnvelope, long timestamp);
    }

    public static void register(EnvelopeCopiedEventInterceptor interceptor) {
        holder.add(interceptor);
    }

    public static void unregister(EnvelopeCopiedEventInterceptor interceptor) {
        holder.remove(interceptor);
    }

    public static void onEnvelopeCopied(Object oldEnvelope, Object newEnvelope, long timestamp) {
        holder.forEach(it -> it.onEnvelopeCopied(oldEnvelope, newEnvelope, timestamp));
    }

    public static void reset() {
        holder.clear();
    }
}
