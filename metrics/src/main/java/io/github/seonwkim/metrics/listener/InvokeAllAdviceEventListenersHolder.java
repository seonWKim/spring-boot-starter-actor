package io.github.seonwkim.metrics.listener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Registry for batch message invocation event listeners that monitors bulk message processing operations.
 *
 * <p>Unlike single message processing, this holder captures events when multiple messages are processed
 * together, providing insights into batch processing efficiency and helping optimize bulk operations.
 * This is particularly important for systems that handle high message volumes or use batching strategies.</p>
 */
public class InvokeAllAdviceEventListenersHolder {
    private static final Queue<InvokeAllAdviceEventListener> holder = new ConcurrentLinkedQueue<>();

    public interface InvokeAllAdviceEventListener {
        void onEnter(Object messages);

        void onExit(Object messages, long startTime);
    }

    public static void register(InvokeAllAdviceEventListener listener) {
        holder.add(listener);
    }

    public static void unregister(InvokeAllAdviceEventListener listener) {
        holder.remove(listener);
    }

    public static void invokeAllAdviceOnEnter(Object messages) {
        holder.forEach(it -> it.onEnter(messages));
    }

    public static void invokeAllAdviceOnExit(Object messages, long starTime) {
        holder.forEach(it -> it.onExit(messages, starTime));
    }

    public static void reset() {
        holder.clear();
    }
}
