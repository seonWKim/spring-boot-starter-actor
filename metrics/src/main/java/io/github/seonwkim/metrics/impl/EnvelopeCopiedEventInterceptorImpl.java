package io.github.seonwkim.metrics.impl;

import io.github.seonwkim.metrics.interceptor.EnvelopeCopiedEventInterceptorsHolder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnvelopeCopiedEventInterceptorImpl
        implements EnvelopeCopiedEventInterceptorsHolder.EnvelopeCopiedEventInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(EnvelopeCopiedEventInterceptorImpl.class);

    private final ConcurrentHashMap<String, EnvelopeMetrics> envelopeMetrics = new ConcurrentHashMap<>();

    @Override
    public void onEnvelopeCopied(Object oldEnvelope, Object newEnvelope, long timestamp) {
        try {
            String oldMessageType = getMessageType(oldEnvelope);
            String newMessageType = getMessageType(newEnvelope);

            EnvelopeMetrics metrics = envelopeMetrics.computeIfAbsent(
                    getCopyKey(oldMessageType, newMessageType), k -> new EnvelopeMetrics());
            metrics.recordCopy(timestamp);

            logger.debug("Envelope copied from {} to {} at timestamp: {}", oldMessageType, newMessageType, timestamp);
        } catch (Exception e) {
            logger.error("Error recording envelope copied metric", e);
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

    private String getCopyKey(String oldMessageType, String newMessageType) {
        return oldMessageType + "->" + newMessageType;
    }

    @Nullable
    public EnvelopeMetrics getEnvelopeMetrics(String copyKey) {
        return envelopeMetrics.get(copyKey);
    }

    @Nullable
    public EnvelopeMetrics getEnvelopeMetrics(String oldMessageType, String newMessageType) {
        return getEnvelopeMetrics(getCopyKey(oldMessageType, newMessageType));
    }

    public void reset() {
        envelopeMetrics.clear();
    }

    public static class EnvelopeMetrics {
        private final LongAdder copiedCount = new LongAdder();
        private volatile long firstCopiedTimestamp = 0;
        private volatile long lastCopiedTimestamp = 0;

        public void recordCopy(long timestamp) {
            copiedCount.increment();

            if (firstCopiedTimestamp == 0) {
                synchronized (this) {
                    if (firstCopiedTimestamp == 0) {
                        firstCopiedTimestamp = timestamp;
                    }
                }
            }

            lastCopiedTimestamp = timestamp;
        }

        public long getCopiedCount() {
            return copiedCount.sum();
        }

        public long getFirstCopiedTimestamp() {
            return firstCopiedTimestamp;
        }

        public long getLastCopiedTimestamp() {
            return lastCopiedTimestamp;
        }

        public long getTimeBetweenFirstAndLast() {
            if (firstCopiedTimestamp == 0 || lastCopiedTimestamp == 0) {
                return 0;
            }
            return lastCopiedTimestamp - firstCopiedTimestamp;
        }
    }
}
