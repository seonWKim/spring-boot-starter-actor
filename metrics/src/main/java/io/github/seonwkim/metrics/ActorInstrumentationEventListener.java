package io.github.seonwkim.metrics;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.pekko.dispatch.Envelope;

public class ActorInstrumentationEventListener {
    private static final Queue<InvokeAdviceEventListener> invokeAdviceEventListeners =
            new ConcurrentLinkedQueue<>();

    public interface InvokeAdviceEventListener {
        void onEnter(Envelope envelope);

        void onExit(long startTime, Throwable throwable);
    }

    public static void register(InvokeAdviceEventListener listener) {
        invokeAdviceEventListeners.add(listener);
    }

    public static void invokeAdviceOnEnter(Envelope envelope) {
        invokeAdviceEventListeners.forEach(it -> it.onEnter(envelope));
    }

    public static void invokeAdviceOnExit(long startTime, Throwable throwable) {
        invokeAdviceEventListeners.forEach(it -> it.onExit(startTime, throwable));
    }
}
