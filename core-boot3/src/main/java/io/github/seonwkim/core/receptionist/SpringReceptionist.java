package io.github.seonwkim.core.receptionist;

import io.github.seonwkim.core.ActorConstants;
import io.github.seonwkim.core.SpringActorHandle;
import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.actor.typed.Scheduler;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring-injectable service around Pekko's typed Receptionist.
 *
 * <p>The Receptionist lets actors be discovered by a {@link ServiceKey} instead of by name or
 * path. In cluster mode the registrations are replicated to every node, so an actor registered
 * on one node can be found from any other node. Replication is <b>eventually consistent</b>:
 * a lookup on another node may not see a registration right away.
 *
 * <p>Example:
 * <pre>{@code
 * ServiceKey<Command> key = SpringReceptionist.serviceKey(Command.class, "workers");
 *
 * receptionist.register(key, workerHandle);
 *
 * Set<SpringActorHandle<Command>> workers = receptionist.find(key)
 *         .toCompletableFuture().get();
 *
 * SpringReceptionistSubscription<Command> subscription =
 *         receptionist.subscribe(key, current -> log.info("workers: {}", current.size()));
 * subscription.cancel();
 * }</pre>
 */
public class SpringReceptionist {

    private static final Logger log = LoggerFactory.getLogger(SpringReceptionist.class);

    private final SpringActorSystem actorSystem;

    /**
     * Creates a new receptionist service for the given actor system.
     *
     * @param actorSystem The actor system whose Receptionist is used
     */
    public SpringReceptionist(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Creates a service key for the given message type and id.
     *
     * @param messageType The message type accepted by actors registered under the key
     * @param id The key id; keys with the same id and type are equal
     * @param <T> The message type
     * @return A new service key
     */
    public static <T> ServiceKey<T> serviceKey(Class<T> messageType, String id) {
        return ServiceKey.create(messageType, id);
    }

    /**
     * Registers an actor under the given service key. The actor is removed from the listing
     * automatically when it stops.
     *
     * @param key The service key
     * @param actor The actor to register
     * @param <T> The message type
     */
    public <T> void register(ServiceKey<T> key, SpringActorHandle<T> actor) {
        register(key, actor.getUnderlying());
    }

    /**
     * Registers a raw Pekko actor reference under the given service key.
     *
     * @param key The service key
     * @param actor The actor to register
     * @param <T> The message type
     */
    public <T> void register(ServiceKey<T> key, ActorRef<T> actor) {
        system().receptionist().tell(Receptionist.register(key, actor));
    }

    /**
     * Removes an actor from the given service key.
     *
     * @param key The service key
     * @param actor The actor to remove
     * @param <T> The message type
     */
    public <T> void deregister(ServiceKey<T> key, SpringActorHandle<T> actor) {
        system().receptionist().tell(Receptionist.deregister(key, actor.getUnderlying()));
    }

    /**
     * Looks up the actors currently registered under the given key, once, with the default timeout.
     *
     * @param key The service key
     * @param <T> The message type
     * @return A stage with the registered actors (possibly empty)
     */
    public <T> CompletionStage<Set<SpringActorHandle<T>>> find(ServiceKey<T> key) {
        return find(key, ActorConstants.DEFAULT_TIMEOUT);
    }

    /**
     * Looks up the actors currently registered under the given key, once.
     *
     * <p>In cluster mode the result reflects what this node knows now; registrations made on
     * other nodes appear eventually.
     *
     * @param key The service key
     * @param timeout How long to wait for the Receptionist to answer
     * @param <T> The message type
     * @return A stage with the registered actors (possibly empty)
     */
    public <T> CompletionStage<Set<SpringActorHandle<T>>> find(ServiceKey<T> key, Duration timeout) {
        ActorSystem<?> system = system();
        return AskPattern.<Receptionist.Command, Receptionist.Listing>ask(
                        system.receptionist(), replyTo -> Receptionist.find(key, replyTo), timeout, system.scheduler())
                .thenApply(listing -> toHandles(key, listing, system.scheduler()));
    }

    /**
     * Subscribes to changes of the actors registered under the given key.
     *
     * <p>The listener is called with the current set right after subscribing, then with the
     * updated set each time an actor registers, deregisters or stops. It runs on the helper
     * actor's thread, so it should return quickly and must not block.
     *
     * @param key The service key
     * @param listener Called with the full current set on each change
     * @param <T> The message type
     * @return A subscription that can be cancelled
     */
    public <T> SpringReceptionistSubscription<T> subscribe(
            ServiceKey<T> key, Consumer<Set<SpringActorHandle<T>>> listener) {
        ActorSystem<?> system = system();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompletableFuture<Void> terminated = new CompletableFuture<>();
        Behavior<HelperCommand> behavior = helperBehavior(key, listener, cancelled, terminated, system.scheduler());
        ActorRef<HelperCommand> helper =
                system.systemActorOf(behavior, "receptionist-subscriber-" + UUID.randomUUID(), Props.empty());
        return new SpringReceptionistSubscription<>(key, helper, cancelled, terminated);
    }

    private ActorSystem<?> system() {
        return actorSystem.getRaw();
    }

    private static <T> Behavior<HelperCommand> helperBehavior(
            ServiceKey<T> key,
            Consumer<Set<SpringActorHandle<T>>> listener,
            AtomicBoolean cancelled,
            CompletableFuture<Void> terminated,
            Scheduler scheduler) {
        return Behaviors.setup(ctx -> {
            ActorRef<Receptionist.Listing> adapter =
                    ctx.messageAdapter(Receptionist.Listing.class, ListingReceived::new);
            ctx.getSystem().receptionist().tell(Receptionist.subscribe(key, adapter));
            return Behaviors.receive(HelperCommand.class)
                    .onMessage(ListingReceived.class, msg -> {
                        if (!cancelled.get()) {
                            try {
                                listener.accept(toHandles(key, msg.listing, scheduler));
                            } catch (RuntimeException e) {
                                log.warn("Receptionist listener for key {} failed", key.id(), e);
                            }
                        }
                        return Behaviors.same();
                    })
                    .onMessage(StopHelper.class, msg -> Behaviors.stopped())
                    .onSignal(PostStop.class, signal -> {
                        cancelled.set(true);
                        terminated.complete(null);
                        return Behaviors.same();
                    })
                    .build();
        });
    }

    private static <T> Set<SpringActorHandle<T>> toHandles(
            ServiceKey<T> key, Receptionist.Listing listing, Scheduler scheduler) {
        Set<SpringActorHandle<T>> handles = new LinkedHashSet<>();
        for (ActorRef<T> ref : listing.getServiceInstances(key)) {
            handles.add(new SpringActorHandle<>(scheduler, ref));
        }
        return Collections.unmodifiableSet(handles);
    }

    /** Messages understood by the subscription helper actor. */
    interface HelperCommand {}

    /** Wraps a listing update from the Receptionist. */
    static final class ListingReceived implements HelperCommand {
        final Receptionist.Listing listing;

        ListingReceived(Receptionist.Listing listing) {
            this.listing = listing;
        }
    }

    /** Stops the helper actor. */
    static final class StopHelper implements HelperCommand {
        static final StopHelper INSTANCE = new StopHelper();

        private StopHelper() {}
    }
}
