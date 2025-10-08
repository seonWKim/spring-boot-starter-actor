package io.github.seonwkim.metrics.impl;

import io.github.seonwkim.metrics.interceptor.ActorLifeCycleEventInterceptorsHolder;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActorLifecycleEventInterceptorImpl
        implements ActorLifeCycleEventInterceptorsHolder.ActorLifecycleEventInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(ActorLifecycleEventInterceptorImpl.class);

    // Actor path constants
    private static final String PEKKO_PREFIX = "pekko://";
    private static final String SYSTEM_PATH = "/system/";
    private static final String TEMP_PATH = "/temp/";
    private static final String STREAM_PATH = "/stream";

    public static class ActorSystemMetrics {
        private static final ActorSystemMetrics INSTANCE = new ActorSystemMetrics();

        private final AtomicLong activeActors = new AtomicLong(0);

        private ActorSystemMetrics() {}

        public static ActorSystemMetrics getInstance() {
            return INSTANCE;
        }

        public void incrementActiveActors() {
            activeActors.incrementAndGet();
        }

        public void decrementActiveActors() {
            activeActors.decrementAndGet();
        }

        public long getActiveActors() {
            return activeActors.get();
        }

        public void reset() {
            activeActors.set(0);
        }
    }

    @Override
    public void onActorCreated(Object actorCell) {
        try {
            if (!isTemporaryActor(actorCell)) {
                ActorSystemMetrics.getInstance().incrementActiveActors();
                logger.debug(
                        "Actor created, active actors: {}",
                        ActorSystemMetrics.getInstance().getActiveActors());
            }
        } catch (Exception e) {
            logger.error("Error in onActorCreated", e);
        }
    }

    @Override
    public void onActorTerminated(Object actorCell) {
        try {
            if (!isTemporaryActor(actorCell)) {
                ActorSystemMetrics.getInstance().decrementActiveActors();
                logger.debug(
                        "Actor terminated, active actors: {}",
                        ActorSystemMetrics.getInstance().getActiveActors());
            }
        } catch (Exception e) {
            logger.error("Error in onActorTerminated", e);
        }
    }

    @Override
    public void onUnstartedCellReplaced(Object unstartedCell, Object newCell) {
        // No increment here to avoid double counting if onActorCreated already handled ActorCell.
    }

    private boolean isTemporaryActor(Object cell) {
        try {
            Object self = cell.getClass().getMethod("self").invoke(cell);
            Object path = self.getClass().getMethod("path").invoke(self);
            String pathString = path.toString();

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
            logger.error("Error checking if actor is temporary", e);
            return false;
        }
    }
}
