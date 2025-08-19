package io.github.seonwkim.metrics.listener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EnvelopeCopiedEventListenerHolder {
	private static final Queue<EnvelopeCopiedEventListener> holder = new ConcurrentLinkedQueue<>();

	public interface EnvelopeCopiedEventListener {
		void onEnvelopeCopied(Object oldEnvelope, Object newEnvelope, long timestamp);
	}

	public static void register(EnvelopeCopiedEventListener listener) {
		holder.add(listener);
	}

	public static void unregister(EnvelopeCopiedEventListener listener) {
		holder.remove(listener);
	}

	public static void onEnvelopeCopied(Object oldEnvelope, Object newEnvelope, long timestamp) {
		holder.forEach(it -> it.onEnvelopeCopied(oldEnvelope, newEnvelope, timestamp));
	}

	public static void reset() {
		holder.clear();
	}
}
