package io.github.seonwkim.actor.receptionist;

import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorRef;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Finds actors by service name, on this node or, in cluster mode, on any node. Inject it as a bean.
 *
 * <pre>{@code
 * ServiceName<Command> workers = ServiceName.of(Command.class, "workers");
 * receptionist.register(workers, worker);
 * receptionist.find(workers).thenAccept(found -> found.forEach(w -> w.tell(new Work())));
 * }</pre>
 */
public interface SpringReceptionist {

    /** Registers {@code actor} under {@code name}. It is removed when the actor stops. */
    <T> void register(ServiceName<T> name, SpringActorRef<T> actor);

    /** Removes {@code actor} from {@code name}. */
    <T> void deregister(ServiceName<T> name, SpringActorRef<T> actor);

    /** The actors registered under {@code name} now, waiting up to 3 seconds. */
    <T> CompletionStage<Set<SpringActorHandle<T>>> find(ServiceName<T> name);

    /** The actors registered under {@code name} now, waiting up to {@code timeout}. */
    <T> CompletionStage<Set<SpringActorHandle<T>>> find(ServiceName<T> name, Duration timeout);

    /**
     * Calls {@code listener} with the current registrations and again whenever they change, until
     * the subscription is cancelled. A listener that throws is logged and keeps its subscription.
     */
    <T> SpringReceptionistSubscription<T> subscribe(ServiceName<T> name, Consumer<Set<SpringActorHandle<T>>> listener);
}
