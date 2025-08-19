package io.github.seonwkim.metrics.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.seonwkim.metrics.interceptor.EnvelopeSentEventInterceptorsHolder;

public class EnvelopeSentEventInterceptorImpl implements EnvelopeSentEventInterceptorsHolder.EnvelopeSentEventInterceptor {
	private static final Logger logger = LoggerFactory.getLogger(EnvelopeSentEventInterceptorImpl.class);

	private final ConcurrentHashMap<String, EnvelopeMetrics> envelopeMetrics = new ConcurrentHashMap<>();

	@Override
	public void onEnvelopeSent(Object envelope, long timestamp) {
		try {
			String messageType = getMessageType(envelope);
			EnvelopeMetrics metrics = envelopeMetrics.computeIfAbsent(messageType, k -> new EnvelopeMetrics());
			metrics.recordSent(timestamp);
			
			logger.debug("Envelope sent for message type: {} at timestamp: {}", messageType, timestamp);
		} catch (Exception e) {
			logger.error("Error recording envelope sent metric", e);
		}
	}

	private String getMessageType(Object envelope) {
		try {
			Object message = envelope.getClass().getMethod("message").invoke(envelope);
			return message.getClass().getSimpleName();
		} catch (Exception e) {
			logger.debug("Could not extract message type from envelope", e);
			return "unknown";
		}
	}

	public EnvelopeMetrics getEnvelopeMetrics(String messageType) {
		return envelopeMetrics.get(messageType);
	}

	public void reset() {
		envelopeMetrics.clear();
	}

	public static class EnvelopeMetrics {
		private final LongAdder sentCount = new LongAdder();
		private volatile long firstSentTimestamp = 0;
		private volatile long lastSentTimestamp = 0;

		public void recordSent(long timestamp) {
			sentCount.increment();
			
			if (firstSentTimestamp == 0) {
				synchronized (this) {
					if (firstSentTimestamp == 0) {
						firstSentTimestamp = timestamp;
					}
				}
			}
			
			lastSentTimestamp = timestamp;
		}

		public long getSentCount() {
			return sentCount.sum();
		}

		public long getFirstSentTimestamp() {
			return firstSentTimestamp;
		}

		public long getLastSentTimestamp() {
			return lastSentTimestamp;
		}

		public long getTimeBetweenFirstAndLast() {
			if (firstSentTimestamp == 0 || lastSentTimestamp == 0) {
				return 0;
			}
			return lastSentTimestamp - firstSentTimestamp;
		}
	}
}
