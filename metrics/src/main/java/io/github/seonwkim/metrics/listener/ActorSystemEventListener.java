package io.github.seonwkim.metrics.listener;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ActorSystemEventListener {
	private static final Queue<ActorLifecycleEventListener> lifecycleEventListeners =
			new ConcurrentLinkedQueue<>();

	public interface ActorLifecycleEventListener {
		void onActorCreated(Object actorCell);

		void onActorTerminated(Object actorCell);
		
		void onUnstartedCellReplaced(Object unstartedCell, Object newCell);
	}

	public static void register(ActorLifecycleEventListener listener) {
		lifecycleEventListeners.add(listener);
	}

	public static void unregister(ActorLifecycleEventListener listener) {
		lifecycleEventListeners.remove(listener);
	}

	public static void onActorCreated(Object actorCell) {
		lifecycleEventListeners.forEach(it -> it.onActorCreated(actorCell));
	}

	public static void onActorTerminated(Object actorCell) {
		lifecycleEventListeners.forEach(it -> it.onActorTerminated(actorCell));
	}
	
	public static void onUnstartedCellReplaced(Object unstartedCell, Object newCell) {
		lifecycleEventListeners.forEach(it -> it.onUnstartedCellReplaced(unstartedCell, newCell));
	}
}
