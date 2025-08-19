package io.github.seonwkim.example;

import io.github.seonwkim.metrics.interceptor.InvokeAdviceEventListenersHolder;
import io.github.seonwkim.metrics.interceptor.InvokeAdviceEventListenersHolder.InvokeAdviceEventListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.apache.pekko.dispatch.Envelope;
import org.springframework.stereotype.Component;

/**
 * Exports metrics from the actor system to Micrometer for monitoring.
 *
 * <p>This component registers an event listener with the actor instrumentation system to capture
 * timing and count metrics for specific actor message types. The metrics are then exported to
 * Prometheus via Spring Boot Actuator's Prometheus endpoint.
 *
 * <p>The metrics collected include: - Time spent processing each message type (timer) - Count of
 * messages processed by type (counter)
 */
@Component
public class ActorClusterMetricsExporter {

	/** The Micrometer registry used to register and manage metrics */
	private final MeterRegistry registry;

	/** Map of message type names to timers measuring processing duration */
	ConcurrentHashMap<String, Timer> invokeTimers = new ConcurrentHashMap<>();

	/** Map of message type names to counters tracking message frequency */
	ConcurrentHashMap<String, Counter> invokeCounters = new ConcurrentHashMap<>();

	/**
	 * Set of message classes that we want to collect metrics for. Only messages of these types will
	 * be measured and counted.
	 */
	private final Set<Class<?>> targetClasses =
			Set.of(ChatRoomActor.JoinRoom.class,
					ChatRoomActor.LeaveRoom.class,
					ChatRoomActor.SendMessage.class);

	public ActorClusterMetricsExporter(MeterRegistry registry) {
		this.registry = registry;
	}

	@PostConstruct
	public void registerMetrics() {
		InvokeAdviceEventListenersHolder.register(
				new InvokeAdviceEventListener() {
					@Override
					public void onEnter(Object envelope) {}

					@Override
					public void onExit(Object envelope, long startTime) {
						Envelope envelope1 = (Envelope) envelope;
						if (!targetClasses.contains(envelope1.message().getClass())) {
							return;
						}

						String messageType = envelopeMessageTypeSafe(envelope1);
						Timer timer =
								invokeTimers.computeIfAbsent(
										messageType,
										mt ->
												Timer.builder("pekko.actorcell.invoke.timer")
														.description("Time spent in ActorCell.invoke(Envelope)")
														.tags("messageType", mt)
														.register(registry));

						Counter counter =
								invokeCounters.computeIfAbsent(
										messageType,
										mt ->
												Counter.builder("pekko.actorcell.invoke.count")
														.description("Count of messages processed by ActorCell.invoke")
														.tags("messageType", mt)
														.register(registry));

						long duration = System.nanoTime() - startTime;
						timer.record(duration, TimeUnit.NANOSECONDS);
						counter.increment();
					}

					private String envelopeMessageTypeSafe(Envelope envelope) {
						try {
							Object msg = envelope.message();
							return msg != null ? msg.getClass().getSimpleName() : "null";
						} catch (Throwable t) {
							return "unknown";
						}
					}
				});
	}
}
