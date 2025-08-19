package io.github.seonwkim.metrics.listener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EnvelopeSentEventListenerHolder {
	private static final Queue<EnvelopeSentEventListener> holder = new ConcurrentLinkedQueue<>();

	public interface EnvelopeSentEventListener {
		void onEnvelopeSent(Object envelope, long timestamp);
	}

	public static void register(EnvelopeSentEventListener listener) {
		holder.add(listener);
	}

	public static void unregister(EnvelopeSentEventListener listener) {
		holder.remove(listener);
	}

	public static void onEnvelopeSent(Object envelope, long timestamp) {
		holder.forEach(it -> it.onEnvelopeSent(envelope, timestamp));
	}

	public static void reset() {
		holder.clear();
	}
}
