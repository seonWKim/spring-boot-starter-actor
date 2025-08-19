package io.github.seonwkim.metrics.listener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Registry for actor lifecycle event listeners that monitors actor creation, termination, and replacement events.
 * 
 * <p>The holder maintains a thread-safe collection of listeners that are notified when actor lifecycle events occur.
 * This is particularly valuable in production environments where understanding actor behavior is critical for
 * system reliability and performance optimization.</p>
 */
public class ActorLifeCycleEventListenersHolder {
	private static final Queue<ActorLifecycleEventListener> holder = new ConcurrentLinkedQueue<>();

	public interface ActorLifecycleEventListener {
		void onActorCreated(Object actorCell);

		void onActorTerminated(Object actorCell);
		
		void onUnstartedCellReplaced(Object unstartedCell, Object newCell);
	}

	public static void register(ActorLifecycleEventListener listener) {
		holder.add(listener);
	}

	public static void unregister(ActorLifecycleEventListener listener) {
		holder.remove(listener);
	}

	public static void reset() {
		holder.clear();
	}

	public static void onActorCreated(Object actorCell) {
		holder.forEach(it -> it.onActorCreated(actorCell));
	}

	public static void onActorTerminated(Object actorCell) {
		holder.forEach(it -> it.onActorTerminated(actorCell));
	}
	
	public static void onUnstartedCellReplaced(Object unstartedCell, Object newCell) {
		holder.forEach(it -> it.onUnstartedCellReplaced(unstartedCell, newCell));
	}
}
