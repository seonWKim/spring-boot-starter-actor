package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.ActorConstants;
import io.github.seonwkim.actor.SpringActorHandle;
import io.github.seonwkim.actor.SpringActorRef;
import io.github.seonwkim.actor.SpringActorSystem;
import io.github.seonwkim.actor.receptionist.ServiceName;
import io.github.seonwkim.actor.receptionist.SpringReceptionist;
import io.github.seonwkim.actor.receptionist.SpringReceptionistSubscription;
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

/** {@link SpringReceptionist} on Pekko's receptionist, which is cluster-aware in cluster mode. */
public final class PekkoReceptionist implements SpringReceptionist {

    private static final Logger log = LoggerFactory.getLogger(PekkoReceptionist.class);

    private final SpringActorSystem actorSystem;

    /**
     * Creates a new receptionist service for the given actor system.
     *
     * @param actorSystem The actor system whose Receptionist is used
     */
    public PekkoReceptionist(SpringActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /** The Pekko key for a service name. */
    public static <T> ServiceKey<T> serviceKey(ServiceName<T> name) {
        return ServiceKey.create(name.messageType(), name.id());
    }

    @Override
    public <T> void register(ServiceName<T> name, SpringActorRef<T> actor) {
        system().receptionist().tell(Receptionist.register(serviceKey(name), PekkoActorRef.toPekko(actor)));
    }

    @Override
    public <T> void deregister(ServiceName<T> name, SpringActorRef<T> actor) {
        system().receptionist().tell(Receptionist.deregister(serviceKey(name), PekkoActorRef.toPekko(actor)));
    }

    @Override
    public <T> CompletionStage<Set<SpringActorHandle<T>>> find(ServiceName<T> name) {
        return find(name, ActorConstants.DEFAULT_TIMEOUT);
    }

    @Override
    public <T> CompletionStage<Set<SpringActorHandle<T>>> find(ServiceName<T> name, Duration timeout) {
        ServiceKey<T> key = serviceKey(name);
        ActorSystem<?> system = system();
        return AskPattern.<Receptionist.Command, Receptionist.Listing>ask(
                        system.receptionist(), replyTo -> Receptionist.find(key, replyTo), timeout, system.scheduler())
                .thenApply(listing -> toHandles(key, listing, system.scheduler()));
    }

    @Override
    public <T> SpringReceptionistSubscription<T> subscribe(
            ServiceName<T> name, Consumer<Set<SpringActorHandle<T>>> listener) {
        ServiceKey<T> key = serviceKey(name);
        ActorSystem<?> system = system();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompletableFuture<Void> terminated = new CompletableFuture<>();
        Behavior<HelperCommand> behavior = helperBehavior(key, listener, cancelled, terminated, system.scheduler());
        ActorRef<HelperCommand> helper =
                system.systemActorOf(behavior, "receptionist-subscriber-" + UUID.randomUUID(), Props.empty());
        return new Subscription<>(name, helper, cancelled, terminated);
    }

    private ActorSystem<?> system() {
        return PekkoActorSystem.raw(actorSystem);
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
            handles.add(new PekkoActorHandle<>(scheduler, ref));
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

    private record Subscription<T>(
            ServiceName<T> serviceName,
            ActorRef<HelperCommand> helper,
            AtomicBoolean cancelled,
            CompletableFuture<Void> terminated)
            implements SpringReceptionistSubscription<T> {

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                helper.tell(StopHelper.INSTANCE);
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public CompletionStage<Void> whenTerminated() {
            return terminated;
        }
    }
}
