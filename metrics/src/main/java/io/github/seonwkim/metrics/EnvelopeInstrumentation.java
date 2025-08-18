package io.github.seonwkim.metrics;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.seonwkim.metrics.listener.EnvelopeInstrumentationEventListener;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnvelopeInstrumentation {
	private static final Logger logger = LoggerFactory.getLogger(EnvelopeInstrumentation.class);
	
	// WeakHashMap to store timestamps for envelopes without preventing garbage collection
	private static final Map<Object, Long> envelopeTimestamps = new WeakHashMap<>();
	
	public static AgentBuilder decorate(AgentBuilder builder) {
		return builder
				.type(ElementMatchers.named("org.apache.pekko.dispatch.Envelope"))
				.transform(new EnvelopeTransformer())
				.type(ElementMatchers.named("org.apache.pekko.actor.dungeon.Dispatch"))
				.transform(new DispatchTransformer());
	}
	
	public static class EnvelopeTransformer implements Transformer {
		@Override
		public Builder<?> transform(
				Builder<?> builder,
				TypeDescription typeDescription,
				ClassLoader classLoader,
				JavaModule module) {
			return builder
					// Track when envelope is created
					.visit(
							Advice.to(EnvelopeConstructorAdvice.class)
									.on(ElementMatchers.isConstructor()))
					// Track when envelope is copied
					.visit(
							Advice.to(EnvelopeCopyAdvice.class)
									.on(ElementMatchers.named("copy")));
		}
	}
	
	public static class DispatchTransformer implements Transformer {
		@Override
		public Builder<?> transform(
				Builder<?> builder,
				TypeDescription typeDescription,
				ClassLoader classLoader,
				JavaModule module) {
			return builder
					// Track when message is sent to actor
					.visit(
							Advice.to(SendMessageAdvice.class)
									.on(ElementMatchers.named("sendMessage")
											.and(ElementMatchers.takesArguments(1))));
		}
	}
	
	public static class EnvelopeConstructorAdvice {
		@Advice.OnMethodExit(suppress = Throwable.class)
		public static void onExit(@Advice.This Object envelope) {
			// Store timestamp when envelope is created
			long timestamp = System.nanoTime();
			envelopeTimestamps.put(envelope, timestamp);
			// Notify listeners
			EnvelopeInstrumentationEventListener.onEnvelopeCreated(envelope, timestamp);
		}
	}
	
	public static class EnvelopeCopyAdvice {
		@Advice.OnMethodExit(suppress = Throwable.class)
		public static void onExit(@Advice.This Object oldEnvelope, @Advice.Return Object newEnvelope) {
			// Copy timestamp from old envelope to new envelope
			Long timestamp = envelopeTimestamps.get(oldEnvelope);
			if (timestamp != null) {
				envelopeTimestamps.put(newEnvelope, timestamp);
				// Notify listeners
				EnvelopeInstrumentationEventListener.onEnvelopeCopied(oldEnvelope, newEnvelope, timestamp);
			}
		}
	}
	
	public static class SendMessageAdvice {
		@Advice.OnMethodEnter(suppress = Throwable.class)
		public static void onEnter(@Advice.Argument(0) Object envelope) {
			// Ensure envelope has a timestamp when being sent
			long timestamp;
			if (!envelopeTimestamps.containsKey(envelope)) {
				timestamp = System.nanoTime();
				envelopeTimestamps.put(envelope, timestamp);
			} else {
				timestamp = envelopeTimestamps.get(envelope);
			}
			// Notify listeners
			EnvelopeInstrumentationEventListener.onEnvelopeSent(envelope, timestamp);
		}
	}
	
	/**
	 * Get the timestamp when the envelope was created/enqueued.
	 * @param envelope The envelope object
	 * @return The timestamp in nanoseconds, or current time if not found
	 */
	public static long getEnvelopeTimestamp(Object envelope) {
		Long timestamp = envelopeTimestamps.get(envelope);
		return timestamp != null ? timestamp : System.nanoTime();
	}
	
	/**
	 * Clear all stored timestamps and reset listeners (for testing purposes).
	 */
	public static void reset() {
		envelopeTimestamps.clear();
		EnvelopeInstrumentationEventListener.reset();
	}
}
