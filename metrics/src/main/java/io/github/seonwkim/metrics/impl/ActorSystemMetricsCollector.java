package io.github.seonwkim.metrics.impl;

import io.github.seonwkim.metrics.interceptor.ActorLifeCycleEventInterceptorsHolder;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collector for actor system-level metrics.
 *
 * <p>This collector implements system-wide metrics:
 * <ul>
 *   <li>system.active-actors - Number of active actors (Gauge)</li>
 *   <li>system.created-actors.total - Total actors created (Counter)</li>
 *   <li>system.terminated-actors.total - Total actors terminated (Counter)</li>
 *   <li>mailbox.size.current - Current mailbox size per actor (Gauge)</li>
 * </ul>
 */
public class ActorSystemMetricsCollector
        implements ActorLifeCycleEventInterceptorsHolder.ActorLifecycleEventInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(ActorSystemMetricsCollector.class);

    // Actor path constants
    private static final String PEKKO_PREFIX = "pekko://";
    private static final String SYSTEM_PATH = "/system/";
    private static final String TEMP_PATH = "/temp/";
    private static final String STREAM_PATH = "/stream";

    // System metrics
    private final AtomicLong activeActors = new AtomicLong(0);
    private final LongAdder createdActors = new LongAdder();
    private final LongAdder terminatedActors = new LongAdder();

    // Mailbox size tracking by actor path
    private final ConcurrentHashMap<String, AtomicLong> mailboxSizes = new ConcurrentHashMap<>();

    @Override
    public void onActorCreated(Object actorCell) {
        try {
            String actorPath = getActorPath(actorCell);
            if (!isTemporaryActor(actorPath)) {
                activeActors.incrementAndGet();
                createdActors.increment();

                // Initialize mailbox size tracking
                mailboxSizes.computeIfAbsent(actorPath, k -> new AtomicLong(0));

                logger.debug(
                        "Actor created: {}, active actors: {}",
                        actorPath,
                        activeActors.get());
            }
        } catch (Exception e) {
            logger.error("Error in onActorCreated", e);
        }
    }

    @Override
    public void onActorTerminated(Object actorCell) {
        try {
            String actorPath = getActorPath(actorCell);
            if (!isTemporaryActor(actorPath)) {
                activeActors.decrementAndGet();
                terminatedActors.increment();

                // Remove mailbox size tracking
                mailboxSizes.remove(actorPath);

                logger.debug(
                        "Actor terminated: {}, active actors: {}",
                        actorPath,
                        activeActors.get());
            }
        } catch (Exception e) {
            logger.error("Error in onActorTerminated", e);
        }
    }

    @Override
    public void onUnstartedCellReplaced(Object unstartedCell, Object newCell) {
        // No action needed - onActorCreated handles ActorCell creation
    }

    /**
     * Update mailbox size for an actor. This should be called by instrumentation
     * when mailbox size changes.
     *
     * @param actorCell The actor cell
     * @param size The current mailbox size
     */
    public void updateMailboxSize(Object actorCell, long size) {
        try {
            String actorPath = getActorPath(actorCell);
            if (!isTemporaryActor(actorPath)) {
                AtomicLong mailboxSize = mailboxSizes.get(actorPath);
                if (mailboxSize != null) {
                    mailboxSize.set(size);
                }
            }
        } catch (Exception e) {
            logger.error("Error updating mailbox size", e);
        }
    }

    /**
     * Get current mailbox size for an actor.
     *
     * @param actorPath The actor path
     * @return Current mailbox size
     */
    public long getMailboxSize(String actorPath) {
        AtomicLong size = mailboxSizes.get(actorPath);
        return size != null ? size.get() : 0;
    }

    /**
     * Get number of active actors.
     *
     * @return Active actor count
     */
    public long getActiveActors() {
        return activeActors.get();
    }

    /**
     * Get total number of actors created since start.
     *
     * @return Created actors count
     */
    public long getCreatedActorsTotal() {
        return createdActors.sum();
    }

    /**
     * Get total number of actors terminated since start.
     *
     * @return Terminated actors count
     */
    public long getTerminatedActorsTotal() {
        return terminatedActors.sum();
    }

    /**
     * Reset all metrics (useful for testing).
     */
    public void reset() {
        activeActors.set(0);
        createdActors.reset();
        terminatedActors.reset();
        mailboxSizes.clear();
    }

    private String getActorPath(Object actorCell) {
        try {
            Object self = actorCell.getClass().getMethod("self").invoke(actorCell);
            Object path = self.getClass().getMethod("path").invoke(self);
            return path.toString();
        } catch (Exception e) {
            logger.debug("Could not extract actor path", e);
            return "unknown";
        }
    }

    private boolean isTemporaryActor(String pathString) {
        // Filter out system and temporary actors
        return pathString.startsWith(PEKKO_PREFIX)
                && (pathString.contains(SYSTEM_PATH)
                        || pathString.contains(TEMP_PATH)
                        || pathString.contains(STREAM_PATH));
    }

    /**
     * Get the number of messages currently in an actor's mailbox using reflection.
     * This is a helper method to retrieve mailbox size from an ActorCell.
     *
     * @param actorCell The actor cell
     * @return The number of messages in the mailbox, or -1 if unable to determine
     */
    public static long getMailboxSizeFromActorCell(Object actorCell) {
        try {
            // Get the mailbox from the actor cell
            Method mailboxMethod = actorCell.getClass().getMethod("mailbox");
            Object mailbox = mailboxMethod.invoke(actorCell);

            // Try to get numberOfMessages method
            Method numberOfMessagesMethod = mailbox.getClass().getMethod("numberOfMessages");
            Object result = numberOfMessagesMethod.invoke(mailbox);

            if (result instanceof Integer) {
                return ((Integer) result).longValue();
            } else if (result instanceof Long) {
                return (Long) result;
            }
        } catch (Exception e) {
            // Silently fail - mailbox inspection is optional
        }
        return -1;
    }
}
