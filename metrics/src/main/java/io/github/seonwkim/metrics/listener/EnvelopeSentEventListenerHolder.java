package io.github.seonwkim.metrics.listener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Registry for envelope sent event listeners that monitors when message envelopes are dispatched to target actors.
 *
 * <p>The envelope sent event represents the point where a message leaves its origin and enters the delivery
 * infrastructure. This is crucial for understanding message flow dynamics, detecting communication bottlenecks,
 * and ensuring reliable message delivery across the actor system.</p>
 */
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
