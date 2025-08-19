package io.github.seonwkim.metrics.listener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Registry for envelope copy event listeners that monitors when message envelopes are duplicated or transformed.
 *
 * <p>Envelope copying events indicate message duplication or transformation points, which are critical for
 * understanding message propagation patterns, identifying potential performance issues from excessive copying,
 * and ensuring message integrity during transformations. This is especially important in systems with
 * complex message routing or transformation requirements.</p>
 */
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
