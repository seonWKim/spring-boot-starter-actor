package io.github.seonwkim.metrics.interceptor;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Registry for message invocation event interceptors that monitors individual message processing in
 * actors.
 *
 * <p>The holder captures both entry and exit points of message processing, enabling precise timing
 * measurements and correlation of processing events. This granular visibility is essential for
 * understanding actual message processing behavior in production systems.
 */
public class InvokeAdviceEventInterceptorsHolder {
    private static final Queue<InvokeAdviceEventInterceptor> holder = new ConcurrentLinkedQueue<>();

    public interface InvokeAdviceEventInterceptor {
        void onEnter(Object envelope);

        void onExit(Object envelope, long startTime);

        default void onError(Object envelope, long startTime, Throwable throwable) {
            // Default implementation for backward compatibility
        }
    }

    public static void register(InvokeAdviceEventInterceptor interceptor) {
        holder.add(interceptor);
    }

    public static void unregister(InvokeAdviceEventInterceptor interceptor) {
        holder.remove(interceptor);
    }

    public static void invokeAdviceOnEnter(Object envelope) {
        holder.forEach(it -> it.onEnter(envelope));
    }

    public static void invokeAdviceOnExit(Object envelope, long startTime) {
        holder.forEach(it -> it.onExit(envelope, startTime));
    }

    public static void invokeAdviceOnError(Object envelope, long startTime, Throwable throwable) {
        holder.forEach(it -> it.onError(envelope, startTime, throwable));
    }

    public static void reset() {
        holder.clear();
    }
}
