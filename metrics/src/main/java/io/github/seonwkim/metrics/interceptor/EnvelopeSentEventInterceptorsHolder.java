package io.github.seonwkim.metrics.interceptor;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Registry for envelope sent event interceptors that monitors when message envelopes are dispatched to target actors.
 *
 * <p>The envelope sent event represents the point where a message leaves its origin and enters the delivery
 * infrastructure. This is crucial for understanding message flow dynamics, detecting communication bottlenecks,
 * and ensuring reliable message delivery across the actor system.</p>
 */
public class EnvelopeSentEventInterceptorsHolder {
	private static final Queue<EnvelopeSentEventInterceptor> holder = new ConcurrentLinkedQueue<>();

	public interface EnvelopeSentEventInterceptor {
		void onEnvelopeSent(Object envelope, long timestamp);
	}

	public static void register(EnvelopeSentEventInterceptor interceptor) {
		holder.add(interceptor);
	}

	public static void unregister(EnvelopeSentEventInterceptor interceptor) {
		holder.remove(interceptor);
	}

	public static void onEnvelopeSent(Object envelope, long timestamp) {
		holder.forEach(it -> it.onEnvelopeSent(envelope, timestamp));
	}

	public static void reset() {
		holder.clear();
	}
}
