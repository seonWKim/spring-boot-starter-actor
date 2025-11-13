package io.github.seonwkim.metrics;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nullable;

/**
 * Comprehensive metrics registry for actor system observability.
 *
 * <p>This class provides centralized access to all actor system metrics including:
 * - Actor lifecycle metrics (created, terminated, restarts, stops)
 * - Message processing metrics (processed, errors, processing time)
 * - Mailbox metrics (size, time-in-mailbox, overflow)
 * - System-level metrics (active actors, dead letters, unhandled messages)
 *
 * <p>All metrics are thread-safe and designed for high-throughput actor systems.
 */
public class ActorMetricsRegistry {
    private static final ActorMetricsRegistry INSTANCE = new ActorMetricsRegistry();

    // Actor Lifecycle Metrics
    private final AtomicLong activeActors = new AtomicLong(0);
    private final LongAdder actorsCreated = new LongAdder();
    private final LongAdder actorsTerminated = new LongAdder();
    private final LongAdder actorRestarts = new LongAdder();
    private final LongAdder actorStops = new LongAdder();

    // Message Processing Metrics
    private final LongAdder messagesProcessed = new LongAdder();
    private final LongAdder processingErrors = new LongAdder();
    private final ConcurrentHashMap<String, LongAdder> errorsByType = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> messagesByType = new ConcurrentHashMap<>();

    // System-Level Metrics
    private final LongAdder deadLetters = new LongAdder();
    private final LongAdder unhandledMessages = new LongAdder();

    // Mailbox Metrics
    private final ConcurrentHashMap<String, AtomicLong> mailboxSizes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> maxMailboxSizes = new ConcurrentHashMap<>();
    private final LongAdder mailboxOverflows = new LongAdder();

    // Processing Time Statistics (per message type)
    private final ConcurrentHashMap<String, ProcessingTimeStats> processingTimeByType =
            new ConcurrentHashMap<>();

    // Time in Mailbox Statistics (per actor path)
    private final ConcurrentHashMap<String, TimeInMailboxStats> timeInMailboxByActor =
            new ConcurrentHashMap<>();

    private ActorMetricsRegistry() {}

    public static ActorMetricsRegistry getInstance() {
        return INSTANCE;
    }

    // ========================
    // Actor Lifecycle Methods
    // ========================

    public void incrementActiveActors() {
        activeActors.incrementAndGet();
        actorsCreated.increment();
    }

    public void decrementActiveActors() {
        activeActors.decrementAndGet();
        actorsTerminated.increment();
    }

    public void recordActorRestart(String actorPath) {
        actorRestarts.increment();
    }

    public void recordActorStop(String actorPath) {
        actorStops.increment();
    }

    // ========================
    // Message Processing Methods
    // ========================

    public void recordMessageProcessed(String messageType) {
        messagesProcessed.increment();
        messagesByType.computeIfAbsent(messageType, k -> new LongAdder()).increment();
    }

    public void recordProcessingError(String messageType, String errorType) {
        processingErrors.increment();
        errorsByType.computeIfAbsent(errorType, k -> new LongAdder()).increment();
    }

    public void recordProcessingTime(String messageType, long timeNanos) {
        processingTimeByType
                .computeIfAbsent(messageType, k -> new ProcessingTimeStats())
                .record(timeNanos);
    }

    // ========================
    // System-Level Methods
    // ========================

    public void recordDeadLetter() {
        deadLetters.increment();
    }

    public void recordUnhandledMessage() {
        unhandledMessages.increment();
    }

    // ========================
    // Mailbox Methods
    // ========================

    public void updateMailboxSize(String actorPath, long size) {
        AtomicLong currentSize = mailboxSizes.computeIfAbsent(actorPath, k -> new AtomicLong(0));
        currentSize.set(size);

        // Update max mailbox size
        AtomicLong maxSize = maxMailboxSizes.computeIfAbsent(actorPath, k -> new AtomicLong(0));
        long currentMax;
        do {
            currentMax = maxSize.get();
            if (size <= currentMax) {
                break;
            }
        } while (!maxSize.compareAndSet(currentMax, size));
    }

    public void recordTimeInMailbox(String actorPath, long timeNanos) {
        timeInMailboxByActor
                .computeIfAbsent(actorPath, k -> new TimeInMailboxStats())
                .record(timeNanos);
    }

    public void recordMailboxOverflow(String actorPath) {
        mailboxOverflows.increment();
    }

    public void removeActorMailboxMetrics(String actorPath) {
        mailboxSizes.remove(actorPath);
        maxMailboxSizes.remove(actorPath);
        timeInMailboxByActor.remove(actorPath);
    }

    // ========================
    // Getters
    // ========================

    public long getActiveActors() {
        return activeActors.get();
    }

    public long getActorsCreated() {
        return actorsCreated.sum();
    }

    public long getActorsTerminated() {
        return actorsTerminated.sum();
    }

    public long getActorRestarts() {
        return actorRestarts.sum();
    }

    public long getActorStops() {
        return actorStops.sum();
    }

    public long getMessagesProcessed() {
        return messagesProcessed.sum();
    }

    public long getProcessingErrors() {
        return processingErrors.sum();
    }

    public long getErrorsByType(String errorType) {
        LongAdder counter = errorsByType.get(errorType);
        return counter != null ? counter.sum() : 0;
    }

    public long getMessagesByType(String messageType) {
        LongAdder counter = messagesByType.get(messageType);
        return counter != null ? counter.sum() : 0;
    }

    public long getDeadLetters() {
        return deadLetters.sum();
    }

    public long getUnhandledMessages() {
        return unhandledMessages.sum();
    }

    public long getMailboxSize(String actorPath) {
        AtomicLong size = mailboxSizes.get(actorPath);
        return size != null ? size.get() : 0;
    }

    public long getMaxMailboxSize(String actorPath) {
        AtomicLong maxSize = maxMailboxSizes.get(actorPath);
        return maxSize != null ? maxSize.get() : 0;
    }

    public long getMailboxOverflows() {
        return mailboxOverflows.sum();
    }

    @Nullable public ProcessingTimeStats getProcessingTimeStats(String messageType) {
        return processingTimeByType.get(messageType);
    }

    @Nullable public TimeInMailboxStats getTimeInMailboxStats(String actorPath) {
        return timeInMailboxByActor.get(actorPath);
    }

    public ConcurrentHashMap<String, ProcessingTimeStats> getAllProcessingTimeStats() {
        return processingTimeByType;
    }

    public ConcurrentHashMap<String, TimeInMailboxStats> getAllTimeInMailboxStats() {
        return timeInMailboxByActor;
    }

    public ConcurrentHashMap<String, LongAdder> getAllErrorsByType() {
        return errorsByType;
    }

    public ConcurrentHashMap<String, LongAdder> getAllMessagesByType() {
        return messagesByType;
    }

    public ConcurrentHashMap<String, AtomicLong> getAllMailboxSizes() {
        return mailboxSizes;
    }

    /** Reset all metrics (primarily for testing). */
    public void reset() {
        activeActors.set(0);
        actorsCreated.reset();
        actorsTerminated.reset();
        actorRestarts.reset();
        actorStops.reset();
        messagesProcessed.reset();
        processingErrors.reset();
        deadLetters.reset();
        unhandledMessages.reset();
        mailboxOverflows.reset();

        errorsByType.clear();
        messagesByType.clear();
        mailboxSizes.clear();
        maxMailboxSizes.clear();
        processingTimeByType.clear();
        timeInMailboxByActor.clear();
    }

    /**
     * Statistics for processing time measurements.
     */
    public static class ProcessingTimeStats {
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

    /**
     * Statistics for time-in-mailbox measurements.
     */
    public static class TimeInMailboxStats {
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
