package io.github.seonwkim.core.receptionist;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;

/**
 * Handle to an active receptionist subscription created by
 * {@link SpringReceptionist#subscribe(ServiceKey, java.util.function.Consumer)}.
 *
 * <p>Each subscription is backed by a small helper actor that receives listing updates from
 * Pekko's Receptionist and forwards them to the listener. Calling {@link #cancel()} stops
 * that helper actor; after it returns, the listener is not called again.
 *
 * @param <T> The message type of the actors registered under the subscribed key
 */
public final class SpringReceptionistSubscription<T> {

    private final ServiceKey<T> serviceKey;
    private final ActorRef<SpringReceptionist.HelperCommand> helper;
    private final AtomicBoolean cancelled;
    private final CompletableFuture<Void> terminated;

    SpringReceptionistSubscription(
            ServiceKey<T> serviceKey,
            ActorRef<SpringReceptionist.HelperCommand> helper,
            AtomicBoolean cancelled,
            CompletableFuture<Void> terminated) {
        this.serviceKey = serviceKey;
        this.helper = helper;
        this.cancelled = cancelled;
        this.terminated = terminated;
    }

    /**
     * Cancels this subscription and stops its helper actor.
     *
     * <p>Safe to call more than once. Once this method returns, no further callbacks run.
     */
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            helper.tell(SpringReceptionist.StopHelper.INSTANCE);
        }
    }

    /**
     * Returns whether {@link #cancel()} has been called.
     *
     * @return true if this subscription has been cancelled
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Returns a stage that completes when the helper actor behind this subscription has stopped.
     *
     * @return A stage completed after the helper actor stopped
     */
    public CompletionStage<Void> whenTerminated() {
        return terminated;
    }

    /**
     * Returns the service key this subscription listens to.
     *
     * @return The subscribed service key
     */
    public ServiceKey<T> getServiceKey() {
        return serviceKey;
    }

    /**
     * Returns the path of the helper actor, useful for logging and debugging.
     *
     * @return The helper actor's path as a string
     */
    public String getHelperPath() {
        return helper.path().toString();
    }
}
