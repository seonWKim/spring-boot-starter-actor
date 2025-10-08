package io.github.seonwkim.metrics.interceptor;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Registry for actor lifecycle event interceptors that monitors actor creation, termination, and
 * replacement events.
 *
 * <p>The holder maintains a thread-safe collection of interceptors that are notified when actor
 * lifecycle events occur. This is particularly valuable in production environments where
 * understanding actor behavior is critical for system reliability and performance optimization.
 */
public class ActorLifeCycleEventInterceptorsHolder {
    private static final Queue<ActorLifecycleEventInterceptor> holder = new ConcurrentLinkedQueue<>();

    public interface ActorLifecycleEventInterceptor {
        void onActorCreated(Object actorCell);

        void onActorTerminated(Object actorCell);

        void onUnstartedCellReplaced(Object unstartedCell, Object newCell);
    }

    public static void register(ActorLifecycleEventInterceptor interceptor) {
        holder.add(interceptor);
    }

    public static void unregister(ActorLifecycleEventInterceptor interceptor) {
        holder.remove(interceptor);
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
