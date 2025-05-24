package io.github.seonwkim.metrics;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ActorInstrumentationEventListener {
	private static final Queue<InvokeAdviceEventListener> invokeAdviceEventListeners =
			new ConcurrentLinkedQueue<>();
	private static final Queue<InvokeAllAdviceEventListener> invokeAllAdviceEventListeners =
			new ConcurrentLinkedQueue<>();

	public interface InvokeAdviceEventListener {
		void onEnter(Object envelope);

		void onExit(Object envelope, long startTime);
	}

	public interface InvokeAllAdviceEventListener {
		void onEnter(Object messages);

		void onExit(Object messages, long startTime);
	}

	public static void register(InvokeAdviceEventListener listener) {
		invokeAdviceEventListeners.add(listener);
	}

	public static void register(InvokeAllAdviceEventListener listener) {
		invokeAllAdviceEventListeners.add(listener);
	}

	public static void invokeAdviceOnEnter(Object envelope) {
		invokeAdviceEventListeners.forEach(it -> it.onEnter(envelope));
	}

	public static void invokeAdviceOnExit(Object envelope, long startTime) {
		invokeAdviceEventListeners.forEach(it -> it.onExit(envelope, startTime));
	}

	public static void invokeAllAdviceOnEnter(Object messages) {
		invokeAllAdviceEventListeners.forEach(it -> it.onEnter(messages));
	}

	public static void invokeAllAdviceOnExit(Object messages, long starTime) {
		invokeAllAdviceEventListeners.forEach(it -> it.onExit(messages, starTime));
	}
}
