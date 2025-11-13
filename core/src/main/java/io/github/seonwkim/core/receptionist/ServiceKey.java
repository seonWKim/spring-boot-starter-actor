package io.github.seonwkim.core.receptionist;

import java.util.Objects;

/**
 * A key that uniquely identifies a service in the actor system's receptionist registry.
 *
 * <p>ServiceKeys are used to register and discover actors that provide specific services. Multiple
 * actors can be registered under the same ServiceKey, allowing for patterns like worker pools and
 * load balancing.
 *
 * <p>Example usage:
 *
 * <pre>
 * // Create a service key for worker actors
 * ServiceKey&lt;WorkerActor.Command&gt; workerKey =
 *     ServiceKey.create(WorkerActor.Command.class, "worker-pool");
 *
 * // Register an actor with this key
 * receptionistService.register(workerKey, workerActorRef);
 *
 * // Find all actors registered under this key
 * CompletionStage&lt;Set&lt;SpringActorRef&lt;WorkerActor.Command&gt;&gt;&gt; workers =
 *     receptionistService.find(workerKey);
 * </pre>
 *
 * @param <T> The type of messages that actors registered under this key can handle
 */
public final class ServiceKey<T> {

    private final org.apache.pekko.actor.typed.receptionist.ServiceKey<T> underlying;

    private ServiceKey(org.apache.pekko.actor.typed.receptionist.ServiceKey<T> underlying) {
        this.underlying = underlying;
    }

    /**
     * Creates a new ServiceKey with the given message type and identifier.
     *
     * @param messageType The class of messages that actors under this key can handle
     * @param id A unique identifier for this service
     * @param <T> The type of messages
     * @return A new ServiceKey
     */
    public static <T> ServiceKey<T> create(Class<T> messageType, String id) {
        return new ServiceKey<>(org.apache.pekko.actor.typed.receptionist.ServiceKey.create(messageType, id));
    }

    /**
     * Returns the underlying Pekko ServiceKey.
     *
     * @return The Pekko ServiceKey
     */
    public org.apache.pekko.actor.typed.receptionist.ServiceKey<T> getUnderlying() {
        return underlying;
    }

    /**
     * Returns the unique identifier of this service key.
     *
     * @return The service key identifier
     */
    public String getId() {
        return underlying.id();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceKey<?> that = (ServiceKey<?>) o;
        return Objects.equals(underlying, that.underlying);
    }

    @Override
    public int hashCode() {
        return Objects.hash(underlying);
    }

    @Override
    public String toString() {
        return "ServiceKey{" + "id=" + getId() + '}';
    }
}
