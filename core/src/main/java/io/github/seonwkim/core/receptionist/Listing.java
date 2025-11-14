package io.github.seonwkim.core.receptionist;

import io.github.seonwkim.core.SpringActorRef;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;

/**
 * Represents the current set of actors registered for a specific ServiceKey.
 *
 * <p>A Listing is returned when querying or subscribing to the receptionist for actors registered
 * under a ServiceKey. It contains all actors currently registered for that key.
 *
 * <p>Example usage:
 *
 * <pre>
 * receptionistService.find(workerKey).thenAccept(listing -&gt; {
 *     Set&lt;SpringActorRef&lt;Command&gt;&gt; workers = listing.getServiceInstances();
 *     System.out.println("Found " + workers.size() + " workers");
 *
 *     // Send work to all workers
 *     workers.forEach(worker -&gt; worker.tell(new ProcessTask(taskId)));
 * });
 * </pre>
 *
 * @param <T> The type of messages that the registered actors can handle
 */
public final class Listing<T> {

    private final org.apache.pekko.actor.typed.receptionist.Receptionist.Listing underlying;
    private final ServiceKey<T> serviceKey;
    private final ActorSystem<?> actorSystem;

    /**
     * Creates a new Listing from a Pekko Receptionist.Listing.
     *
     * @param underlying The Pekko Listing
     * @param serviceKey The ServiceKey for this listing
     * @param actorSystem The actor system for creating SpringActorRef instances
     */
    public Listing(
            org.apache.pekko.actor.typed.receptionist.Receptionist.Listing underlying,
            ServiceKey<T> serviceKey,
            ActorSystem<?> actorSystem) {
        this.underlying = underlying;
        this.serviceKey = serviceKey;
        this.actorSystem = actorSystem;
    }

    /**
     * Returns the ServiceKey associated with this listing.
     *
     * @return The ServiceKey
     */
    public ServiceKey<T> getServiceKey() {
        return serviceKey;
    }

    /**
     * Returns the set of actors currently registered under this service key.
     *
     * @return An immutable set of SpringActorRef instances
     */
    public Set<SpringActorRef<T>> getServiceInstances() {
        Set<ActorRef<T>> actors = underlying.getServiceInstances(serviceKey.getUnderlying());
        if (actors.isEmpty()) {
            return Collections.emptySet();
        }

        return actors.stream()
                .map(actorRef -> new SpringActorRef<>(actorSystem.scheduler(), actorRef))
                .collect(Collectors.toSet());
    }

    /**
     * Checks if there are any actors registered under this service key.
     *
     * @return true if at least one actor is registered, false otherwise
     */
    public boolean isEmpty() {
        return underlying.getServiceInstances(serviceKey.getUnderlying()).isEmpty();
    }

    /**
     * Returns the number of actors registered under this service key.
     *
     * @return The number of registered actors
     */
    public int size() {
        return underlying.getServiceInstances(serviceKey.getUnderlying()).size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Listing<?> listing = (Listing<?>) o;
        return Objects.equals(underlying, listing.underlying) && Objects.equals(serviceKey, listing.serviceKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(underlying, serviceKey);
    }

    @Override
    public String toString() {
        return "Listing{" + "serviceKey=" + serviceKey + ", count=" + size() + '}';
    }
}
