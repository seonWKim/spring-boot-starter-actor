package io.github.seonwkim.metrics.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.seonwkim.metrics.listener.ActorInstrumentationEventListener;

public class ActorProcessingTimeMetricsImpl implements ActorInstrumentationEventListener.InvokeAdviceEventListener {
	private static final Logger logger = LoggerFactory.getLogger(ActorProcessingTimeMetricsImpl.class);

	private final ConcurrentHashMap<String, TimerMetric> processingTimeMetrics = new ConcurrentHashMap<>();

	@Override
	public void onEnter(Object envelope) {
		// No action needed on enter for processing time calculation
	}
	
	@Override
	public void onExit(Object envelope, long startTime) {
		try {
			long endTime = System.nanoTime();
			long processingTimeNanos = endTime - startTime;
			
			String messageType = getMessageType(envelope);
			TimerMetric timer = processingTimeMetrics.computeIfAbsent(messageType, k -> new TimerMetric());
			timer.record(processingTimeNanos);
			
			logger.debug("Message processing time for {}: {} ms", messageType, 
					TimeUnit.NANOSECONDS.toMillis(processingTimeNanos));
		} catch (Exception e) {
			logger.error("Error recording processing time metric", e);
		}
	}
	
	private String getMessageType(Object envelope) {
		try {
			// Extract message from envelope
			Object message = envelope.getClass().getMethod("message").invoke(envelope);
			return message.getClass().getSimpleName();
		} catch (Exception e) {
			logger.debug("Could not extract message type from envelope", e);
			return "unknown";
		}
	}
	
	public TimerMetric getProcessingTimeMetric(String messageType) {
		return processingTimeMetrics.get(messageType);
	}
	
	public void reset() {
		processingTimeMetrics.clear();
	}
	
	public static class TimerMetric {
		private final LongAdder count = new LongAdder();
		private final LongAdder totalTimeNanos = new LongAdder();
		private final AtomicLong minTimeNanos = new AtomicLong(Long.MAX_VALUE);
		private final AtomicLong maxTimeNanos = new AtomicLong(0);
		
		public void record(long timeNanos) {
			count.increment();
			totalTimeNanos.add(timeNanos);
			
			long currentMin;
			do {
				currentMin = minTimeNanos.get();
				if (timeNanos >= currentMin) {
					break;
				}
			} while (!minTimeNanos.compareAndSet(currentMin, timeNanos));
			
			long currentMax;
			do {
				currentMax = maxTimeNanos.get();
				if (timeNanos <= currentMax) {
					break;
				}
			} while (!maxTimeNanos.compareAndSet(currentMax, timeNanos));
		}
		
		public long getCount() {
			return count.sum();
		}
		
		public long getTotalTimeMillis() {
			return TimeUnit.NANOSECONDS.toMillis(totalTimeNanos.sum());
		}
		
		public long getAverageTimeMillis() {
			long countValue = count.sum();
			if (countValue == 0) {
				return 0;
			}
			return TimeUnit.NANOSECONDS.toMillis(totalTimeNanos.sum() / countValue);
		}
		
		public long getMinTimeMillis() {
			long min = minTimeNanos.get();
			return min == Long.MAX_VALUE ? 0 : TimeUnit.NANOSECONDS.toMillis(min);
		}
		
		public long getMaxTimeMillis() {
			return TimeUnit.NANOSECONDS.toMillis(maxTimeNanos.get());
		}
	}
}
