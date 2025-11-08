package io.github.seonwkim.metrics.impl;

import io.github.seonwkim.metrics.EnvelopeInstrumentation;
import io.github.seonwkim.metrics.interceptor.InvokeAdviceEventInterceptorsHolder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-priority metrics collector for production monitoring of actor systems.
 * 
 * <p>This collector implements the most critical metrics needed for production systems:
 * <ul>
 *   <li>actor.processing-time - Time taken to process messages (Timer)</li>
 *   <li>actor.time-in-mailbox - Time from message enqueue to dequeue (Timer)</li>
 *   <li>actor.messages.processed - Total messages processed (Counter)</li>
 *   <li>actor.errors - Count of processing errors (Counter)</li>
 * </ul>
 * 
 * <p>These metrics can be exported to monitoring systems like Prometheus, Grafana, etc.
 */
public class HighPriorityMetricsCollector
        implements InvokeAdviceEventInterceptorsHolder.InvokeAdviceEventInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(HighPriorityMetricsCollector.class);

    // Processing time metrics by message type
    private final ConcurrentHashMap<String, TimerMetric> processingTimeMetrics = new ConcurrentHashMap<>();
    
    // Time in mailbox metrics by message type
    private final ConcurrentHashMap<String, TimerMetric> timeInMailboxMetrics = new ConcurrentHashMap<>();
    
    // Total messages processed counter by message type
    private final ConcurrentHashMap<String, LongAdder> messagesProcessedCounters = new ConcurrentHashMap<>();
    
    // Error counters by message type
    private final ConcurrentHashMap<String, LongAdder> errorCounters = new ConcurrentHashMap<>();
    
    // Total error counter across all message types
    private final LongAdder totalErrors = new LongAdder();

    @Override
    public void onEnter(Object envelope) {
        // Track time in mailbox when message starts processing
        try {
            long envelopeCreatedTime = EnvelopeInstrumentation.getEnvelopeTimestamp(envelope);
            long now = System.nanoTime();
            long timeInMailbox = now - envelopeCreatedTime;

            String messageType = getMessageType(envelope);
            TimerMetric mailboxTimer = timeInMailboxMetrics.computeIfAbsent(messageType, k -> new TimerMetric());
            mailboxTimer.record(timeInMailbox);

            logger.debug(
                    "Message time in mailbox for {}: {} ms",
                    messageType,
                    TimeUnit.NANOSECONDS.toMillis(timeInMailbox));
        } catch (Exception e) {
            logger.error("Error recording time in mailbox metric", e);
        }
    }

    @Override
    public void onExit(Object envelope, long startTime) {
        try {
            long endTime = System.nanoTime();
            long processingTimeNanos = endTime - startTime;
            String messageType = getMessageType(envelope);

            // Record processing time
            TimerMetric processingTimer = processingTimeMetrics.computeIfAbsent(messageType, k -> new TimerMetric());
            processingTimer.record(processingTimeNanos);

            // Increment messages processed counter
            LongAdder processedCounter = messagesProcessedCounters.computeIfAbsent(messageType, k -> new LongAdder());
            processedCounter.increment();

            logger.debug(
                    "Message processing time for {}: {} ms, total processed: {}",
                    messageType,
                    TimeUnit.NANOSECONDS.toMillis(processingTimeNanos),
                    processedCounter.sum());
        } catch (Exception e) {
            // Record error
            String messageType = getMessageType(envelope);
            LongAdder errorCounter = errorCounters.computeIfAbsent(messageType, k -> new LongAdder());
            errorCounter.increment();
            totalErrors.increment();
            
            logger.error("Error recording processing metrics for message type: {}", messageType, e);
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

    /**
     * Get processing time metric for a specific message type.
     * 
     * @param messageType The message type
     * @return TimerMetric or null if not found
     */
    @Nullable
    public TimerMetric getProcessingTimeMetric(String messageType) {
        return processingTimeMetrics.get(messageType);
    }

    /**
     * Get time in mailbox metric for a specific message type.
     * 
     * @param messageType The message type
     * @return TimerMetric or null if not found
     */
    @Nullable
    public TimerMetric getTimeInMailboxMetric(String messageType) {
        return timeInMailboxMetrics.get(messageType);
    }

    /**
     * Get total messages processed for a specific message type.
     * 
     * @param messageType The message type
     * @return Number of messages processed
     */
    public long getMessagesProcessedCount(String messageType) {
        LongAdder counter = messagesProcessedCounters.get(messageType);
        return counter != null ? counter.sum() : 0;
    }

    /**
     * Get error count for a specific message type.
     * 
     * @param messageType The message type
     * @return Number of errors
     */
    public long getErrorCount(String messageType) {
        LongAdder counter = errorCounters.get(messageType);
        return counter != null ? counter.sum() : 0;
    }

    /**
     * Get total error count across all message types.
     * 
     * @return Total number of errors
     */
    public long getTotalErrorCount() {
        return totalErrors.sum();
    }

    /**
     * Reset all metrics (useful for testing).
     */
    public void reset() {
        processingTimeMetrics.clear();
        timeInMailboxMetrics.clear();
        messagesProcessedCounters.clear();
        errorCounters.clear();
        totalErrors.reset();
    }

    /**
     * Timer metric that tracks count, total time, min, max, and average.
     */
    public static class TimerMetric {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalTimeNanos = new LongAdder();
        private final AtomicLong minTimeNanos = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong maxTimeNanos = new AtomicLong(0);

        public void record(long timeNanos) {
            count.increment();
            totalTimeNanos.add(timeNanos);

            // Update min
            long currentMin;
            do {
                currentMin = minTimeNanos.get();
                if (timeNanos >= currentMin) {
                    break;
                }
            } while (!minTimeNanos.compareAndSet(currentMin, timeNanos));

            // Update max
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

        public long getTotalTimeNanos() {
            return totalTimeNanos.sum();
        }

        public long getAverageTimeNanos() {
            long countValue = count.sum();
            if (countValue == 0) {
                return 0;
            }
            return totalTimeNanos.sum() / countValue;
        }

        public long getMinTimeNanos() {
            long min = minTimeNanos.get();
            return min == Long.MAX_VALUE ? 0 : min;
        }

        public long getMaxTimeNanos() {
            return maxTimeNanos.get();
        }
    }
}
