package io.github.seonwkim.metrics.impl;

import io.github.seonwkim.metrics.interceptor.EnvelopeCreatedEventInterceptorsHolder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnvelopeCreatedEventInterceptorImpl
        implements EnvelopeCreatedEventInterceptorsHolder.EnvelopeCreatedEventInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(EnvelopeCreatedEventInterceptorImpl.class);

    private final ConcurrentHashMap<String, EnvelopeMetrics> envelopeMetrics = new ConcurrentHashMap<>();

    @Override
    public void onEnvelopeCreated(Object envelope, long timestamp) {
        try {
            String messageType = getMessageType(envelope);
            EnvelopeMetrics metrics = envelopeMetrics.computeIfAbsent(messageType, k -> new EnvelopeMetrics());
            metrics.recordCreation(timestamp);

            logger.debug("Envelope created for message type: {} at timestamp: {}", messageType, timestamp);
        } catch (Exception e) {
            logger.error("Error recording envelope creation metric", e);
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

    @Nullable
    public EnvelopeMetrics getEnvelopeMetrics(String messageType) {
        return envelopeMetrics.get(messageType);
    }

    public void reset() {
        envelopeMetrics.clear();
    }

    public static class EnvelopeMetrics {
        private final LongAdder createdCount = new LongAdder();
        private volatile long firstCreatedTimestamp = 0;
        private volatile long lastCreatedTimestamp = 0;

        public void recordCreation(long timestamp) {
            createdCount.increment();

            if (firstCreatedTimestamp == 0) {
                synchronized (this) {
                    if (firstCreatedTimestamp == 0) {
                        firstCreatedTimestamp = timestamp;
                    }
                }
            }

            lastCreatedTimestamp = timestamp;
        }

        public long getCreatedCount() {
            return createdCount.sum();
        }

        public long getFirstCreatedTimestamp() {
            return firstCreatedTimestamp;
        }

        public long getLastCreatedTimestamp() {
            return lastCreatedTimestamp;
        }

        public long getTimeBetweenFirstAndLast() {
            if (firstCreatedTimestamp == 0 || lastCreatedTimestamp == 0) {
                return 0;
            }
            return lastCreatedTimestamp - firstCreatedTimestamp;
        }
    }
}
