package io.github.seonwkim.metrics.listener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvokeAdviceEventListenersHolder {
    private static final Queue<InvokeAdviceEventListener> holder = new ConcurrentLinkedQueue<>();

    public interface InvokeAdviceEventListener {
        void onEnter(Object envelope);

        void onExit(Object envelope, long startTime);
    }

    public static void register(InvokeAdviceEventListener listener) {
        holder.add(listener);
    }

    public static void unregister(InvokeAdviceEventListener listener) {
        holder.remove(listener);
    }

    public static void invokeAdviceOnEnter(Object envelope) {
        holder.forEach(it -> it.onEnter(envelope));
    }

    public static void invokeAdviceOnExit(Object envelope, long startTime) {
        holder.forEach(it -> it.onExit(envelope, startTime));
    }

    public static void reset() {
        holder.clear();
    }
}
