package io.github.seonwkim.metrics.listener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Registry for envelope creation event listeners that monitors when new message envelopes are created in the actor system.
 *
 * <p>Envelope creation events mark the beginning of a message's lifecycle, providing critical data points
 * for understanding message flow patterns, system behavior, and performance characteristics. This is
 * particularly valuable for debugging message routing issues and optimizing message creation patterns.</p>
 */
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
