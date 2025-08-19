package io.github.seonwkim.metrics.listener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EnvelopeCreatedEventListenerHolder {
	private static final Queue<EnvelopeCreatedEventListener> holder = new ConcurrentLinkedQueue<>();

	public interface EnvelopeCreatedEventListener {
		void onEnvelopeCreated(Object envelope, long timestamp);
	}

	public static void register(EnvelopeCreatedEventListener listener) {
		holder.add(listener);
	}

	public static void unregister(EnvelopeCreatedEventListener listener) {
		holder.remove(listener);
	}

	public static void onEnvelopeCreated(Object envelope, long timestamp) {
		holder.forEach(it -> it.onEnvelopeCreated(envelope, timestamp));
	}

	public static void reset() {
		holder.clear();
	}
}
