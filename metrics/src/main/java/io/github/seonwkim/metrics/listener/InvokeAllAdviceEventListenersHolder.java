package io.github.seonwkim.metrics.listener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InvokeAllAdviceEventListenersHolder {
    private static final Queue<InvokeAllAdviceEventListener> holder = new ConcurrentLinkedQueue<>();

    public interface InvokeAllAdviceEventListener {
        void onEnter(Object messages);

        void onExit(Object messages, long startTime);
    }

    public static void register(InvokeAllAdviceEventListener listener) {
        holder.add(listener);
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
