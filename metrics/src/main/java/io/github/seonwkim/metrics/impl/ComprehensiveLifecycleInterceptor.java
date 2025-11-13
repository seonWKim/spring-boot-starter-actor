package io.github.seonwkim.metrics.impl;

import io.github.seonwkim.metrics.ActorMetricsRegistry;
import io.github.seonwkim.metrics.interceptor.ActorLifeCycleEventInterceptorsHolder;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Comprehensive interceptor for actor lifecycle events that tracks all lifecycle-related metrics.
 *
 * <p>This interceptor captures:
 * - Active actors count
 * - Actors created count
 * - Actors terminated count
 * - Actor restarts
 * - Actor stops
 * - Mailbox size tracking
 */
public class ComprehensiveLifecycleInterceptor
        implements ActorLifeCycleEventInterceptorsHolder.ActorLifecycleEventInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(ComprehensiveLifecycleInterceptor.class);

    private final ActorMetricsRegistry metricsRegistry = ActorMetricsRegistry.getInstance();

    // Actor path constants
    private static final String PEKKO_PREFIX = "pekko://";
    private static final String SYSTEM_PATH = "/system/";
    private static final String TEMP_PATH = "/temp/";
    private static final String STREAM_PATH = "/stream";

    @Override
    public void onActorCreated(Object actorCell) {
        try {
            if (!isTemporaryActor(actorCell)) {
                String actorPath = getActorPath(actorCell);
                if (actorPath != null) {
                    metricsRegistry.incrementActiveActors();

                    logger.debug(
                            "Actor created: {}, active actors: {}",
                            actorPath,
                            metricsRegistry.getActiveActors());
                }
            }
        } catch (Exception e) {
            logger.debug("Error in onActorCreated (non-critical): {}", e.getMessage());
        }
    }

    @Override
    public void onActorTerminated(Object actorCell) {
        try {
            if (!isTemporaryActor(actorCell)) {
                String actorPath = getActorPath(actorCell);
                if (actorPath != null) {
                    metricsRegistry.decrementActiveActors();
                    metricsRegistry.recordActorStop(actorPath);

                    // Clean up mailbox metrics for this actor
                    metricsRegistry.removeActorMailboxMetrics(actorPath);

                    logger.debug(
                            "Actor terminated: {}, active actors: {}",
                            actorPath,
                            metricsRegistry.getActiveActors());
                }
            }
        } catch (Exception e) {
            logger.debug("Error in onActorTerminated (non-critical): {}", e.getMessage());
        }
    }

    @Override
    public void onUnstartedCellReplaced(Object unstartedCell, Object newCell) {
        // No action needed - avoid double counting
    }

    private boolean isTemporaryActor(Object cell) {
        try {
            String pathString = getActorPath(cell);
            if (pathString == null) {
                return true; // Treat null paths as temporary
            }

            // Filter out system and temporary actors
            boolean isTemp = pathString.startsWith(PEKKO_PREFIX)
                    && (pathString.contains(SYSTEM_PATH)
                            || pathString.contains(TEMP_PATH)
                            || pathString.contains(STREAM_PATH));

            if (!isTemp) {
                logger.debug("Tracking actor: {}", pathString);
            }

            return isTemp;
        } catch (Exception e) {
            logger.debug("Error checking if actor is temporary (treating as temporary): {}", e.getMessage());
            return true; // Treat as temporary if we can't determine
        }
    }

    @Nullable
    private String getActorPath(Object cell) {
        try {
            Object self = cell.getClass().getMethod("self").invoke(cell);
            Object path = self.getClass().getMethod("path").invoke(self);
            return path.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
