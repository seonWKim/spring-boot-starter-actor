package io.github.seonwkim.actor.receptionist;

import java.util.concurrent.CompletionStage;

/**
 * A subscription to a service's registrations, from {@link SpringReceptionist#subscribe}.
 *
 * @param <T> the messages the registered actors accept
 */
public interface SpringReceptionistSubscription<T> {

    /** Stops calling the listener. Safe to call more than once. */
    void cancel();

    boolean isCancelled();

    /** Completes once the subscription has stopped and the listener won't be called again. */
    CompletionStage<Void> whenTerminated();

    ServiceName<T> serviceName();
}
