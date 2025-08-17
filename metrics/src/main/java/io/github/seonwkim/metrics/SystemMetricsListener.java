package io.github.seonwkim.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemMetricsListener implements ActorSystemEventListener.ActorLifecycleEventListener {
	private static final Logger logger = LoggerFactory.getLogger(SystemMetricsListener.class);

	@Override
	public void onActorCreated(Object actorCell) {
		try {
			if (!isTemporaryActor(actorCell)) {
				SystemMetrics.getInstance().incrementActiveActors();
				logger.debug("Actor created, active actors: {}", SystemMetrics.getInstance().getActiveActors());
			}
		} catch (Exception e) {
			logger.error("Error in onActorCreated", e);
		}
	}
	
	@Override
	public void onActorTerminated(Object actorCell) {
		try {
			if (!isTemporaryActor(actorCell)) {
				SystemMetrics.getInstance().decrementActiveActors();
				logger.debug("Actor terminated, active actors: {}", SystemMetrics.getInstance().getActiveActors());
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
			// System actors start with /system
			// Stream actors contain /stream
			// Temporary actors usually have $ in their path
			boolean isTemp = pathString.startsWith("pekko://") && (
				   pathString.contains("/system/") || 
				   pathString.contains("/temp/") ||
				   pathString.contains("/stream"));
			
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
