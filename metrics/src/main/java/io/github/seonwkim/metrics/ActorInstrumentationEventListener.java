package io.github.seonwkim.metrics;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.pekko.dispatch.Envelope;

public class ActorInstrumentationEventListener {
    private static final Queue<InvokeAdviceEventListener> invokeAdviceEventListeners =
            new ConcurrentLinkedQueue<>();
    private static final Queue<SystemInvokeAdviceEventListener> systemInvokeAdviceEventListeners =
            new ConcurrentLinkedQueue<>();

    public interface InvokeAdviceEventListener {
        void onEnter(Envelope envelope);

        void onExit(long startTime, Throwable throwable);
    }

    public interface SystemInvokeAdviceEventListener {
        void onEnter(Object systemMessage);

        void onExit(long startTime, Throwable throwable);
    }

    public static void register(InvokeAdviceEventListener listener) {
        invokeAdviceEventListeners.add(listener);
    }

    public static void register(SystemInvokeAdviceEventListener listener) {
        systemInvokeAdviceEventListeners.add(listener);
    }

    public static void invokeAdviceOnEnter(Envelope envelope) {
        invokeAdviceEventListeners.forEach(it -> it.onEnter(envelope));
    }

    public static void invokeAdviceOnExit(long startTime, Throwable throwable) {
        invokeAdviceEventListeners.forEach(it -> it.onExit(startTime, throwable));
    }

    public static void systemInvokeAdviceOnEnter(Object systemMessage) {
        systemInvokeAdviceEventListeners.forEach(it -> it.onEnter(systemMessage));
    }

    public static void systemInvokeAdviceOnExit(long startTime, Throwable throwable) {
        systemInvokeAdviceEventListeners.forEach(it -> it.onExit(startTime, throwable));
    }
}
