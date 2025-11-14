package io.github.seonwkim.core.receptionist;

import io.github.seonwkim.core.SpringActorRef;
import io.github.seonwkim.core.SpringActorSystem;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.actor.typed.receptionist.Receptionist;

/**
 * Spring-friendly service for interacting with Pekko's Receptionist for actor discovery.
 *
 * <p>The receptionist provides a dynamic service registry for actors. Actors can register
 * themselves under ServiceKeys, and other actors can discover them by querying or subscribing to
 * those keys. This enables patterns like:
 *
 * <ul>
 *   <li>Worker pools - multiple workers registered under the same key
 *   <li>Service discovery - find actors providing a specific service
 *   <li>Dynamic load balancing - distribute work across available actors
 *   <li>Pub-sub patterns - subscribe to actor availability changes
 * </ul>
 *
 * <p>The receptionist works in both local and cluster modes. In cluster mode, registrations are
 * automatically replicated across the cluster.
 *
 * <p>Example usage:
 *
 * <pre>
 * &#64;Service
 * public class WorkerPoolService {
 *     private final SpringReceptionistService receptionist;
 *     private final SpringActorSystem actorSystem;
 *
 *     // Define a service key for workers
 *     private static final ServiceKey&lt;WorkerActor.Command&gt; WORKER_KEY =
 *         ServiceKey.create(WorkerActor.Command.class, "worker-pool");
 *
 *     // Register a worker actor
 *     public void addWorker(String workerId) {
 *         actorSystem.actor(WorkerActor.class)
 *             .withId(workerId)
 *             .spawn()
 *             .thenAccept(worker -&gt; receptionist.register(WORKER_KEY, worker));
 *     }
 *
 *     // Find all available workers
 *     public CompletionStage&lt;Listing&lt;WorkerActor.Command&gt;&gt; getWorkers() {
 *         return receptionist.find(WORKER_KEY);
 *     }
 *
 *     // Subscribe to worker availability changes
 *     public void monitorWorkers(Consumer&lt;Listing&lt;WorkerActor.Command&gt;&gt; callback) {
 *         receptionist.subscribe(WORKER_KEY, callback);
 *     }
 * }
 * </pre>
 */
public class SpringReceptionistService {

    private final SpringActorSystem actorSystem;
    private final Duration defaultTimeout;

    /**
     * Creates a new SpringReceptionistService.
     *
     * @param actorSystem The SpringActorSystem
     * @param defaultTimeout Default timeout for receptionist operations
     */
    public SpringReceptionistService(SpringActorSystem actorSystem, Duration defaultTimeout) {
        this.actorSystem = actorSystem;
        this.defaultTimeout = defaultTimeout;
    }

    /**
     * Registers an actor with the receptionist under the given service key.
     *
     * <p>Once registered, the actor can be discovered by other actors querying or subscribing to
     * this service key. The actor will automatically be deregistered when it stops.
     *
     * <p>Multiple actors can be registered under the same service key, enabling worker pool
     * patterns.
     *
     * @param serviceKey The service key to register under
     * @param actorRef The actor to register
     * @param <T> The type of messages the actor can handle
     */
    public <T> void register(ServiceKey<T> serviceKey, SpringActorRef<T> actorRef) {
        ActorRef<T> underlyingRef = actorRef.getUnderlying();
        actorSystem.getRaw().receptionist().tell(Receptionist.register(serviceKey.getUnderlying(), underlyingRef));
    }

    /**
     * Finds all actors currently registered under the given service key.
     *
     * <p>This operation returns a snapshot of the current registrations. To receive updates when
     * registrations change, use {@link #subscribe(ServiceKey, Consumer)} instead.
     *
     * @param serviceKey The service key to query
     * @param <T> The type of messages the actors can handle
     * @return A CompletionStage that completes with a Listing of registered actors
     */
    public <T> CompletionStage<Listing<T>> find(ServiceKey<T> serviceKey) {
        CompletionStage<Receptionist.Listing> pekkoListing = AskPattern.ask(
                actorSystem.getRaw().receptionist(),
                (ActorRef<Receptionist.Listing> replyTo) -> Receptionist.find(serviceKey.getUnderlying(), replyTo),
                defaultTimeout,
                actorSystem.getRaw().scheduler());

        return pekkoListing.thenApply(listing -> new Listing<>(listing, serviceKey, actorSystem.getRaw()));
    }

    /**
     * Subscribes to changes in actor registrations for the given service key.
     *
     * <p>The callback will be invoked whenever:
     *
     * <ul>
     *   <li>An actor registers under this service key
     *   <li>A registered actor terminates and is removed
     *   <li>Initially, with the current set of registered actors
     * </ul>
     *
     * <p>This is useful for maintaining a dynamic view of available actors, implementing load
     * balancing, or reacting to service availability changes.
     *
     * <p>Note: The subscriber actor must be spawned before calling this method. The subscription
     * will last for the lifetime of the subscriber actor.
     *
     * @param serviceKey The service key to subscribe to
     * @param subscriber The actor that will receive listing updates
     * @param <T> The type of messages the registered actors can handle
     */
    public <T> void subscribe(ServiceKey<T> serviceKey, ActorRef<Listing<T>> subscriber) {
        // Create an adapter that converts Pekko Listing to Spring Listing
        ActorRef<Receptionist.Listing> adapter = actorSystem
                .getRaw()
                .systemActorOf(
                        org.apache.pekko.actor.typed.javadsl.Behaviors.setup(context ->
                                        org.apache.pekko.actor.typed.javadsl.Behaviors.receiveMessage(msg -> {
                                            if (msg instanceof Receptionist.Listing) {
                                                Receptionist.Listing listing = (Receptionist.Listing) msg;
                                                subscriber.tell(
                                                        new Listing<>(listing, serviceKey, actorSystem.getRaw()));
                                            }
                                            return org.apache.pekko.actor.typed.javadsl.Behaviors.same();
                                        }))
                                .narrow(),
                        "receptionist-subscriber-" + serviceKey.getId() + "-" + System.currentTimeMillis(),
                        org.apache.pekko.actor.typed.Props.empty());

        actorSystem.getRaw().receptionist().tell(Receptionist.subscribe(serviceKey.getUnderlying(), adapter));
    }

    /**
     * Subscribes to changes in actor registrations for the given service key with a callback.
     *
     * <p>This is a convenience method that creates a subscriber actor internally and invokes the
     * callback whenever the listing changes.
     *
     * @param serviceKey The service key to subscribe to
     * @param callback The callback to invoke when the listing changes
     * @param <T> The type of messages the registered actors can handle
     */
    public <T> void subscribe(ServiceKey<T> serviceKey, Consumer<Listing<T>> callback) {
        ActorRef<Listing<T>> subscriber = actorSystem
                .getRaw()
                .systemActorOf(
                        org.apache.pekko.actor.typed.javadsl.Behaviors.setup(context ->
                                        org.apache.pekko.actor.typed.javadsl.Behaviors.receiveMessage(msg -> {
                                            if (msg instanceof Listing) {
                                                @SuppressWarnings("unchecked")
                                                Listing<T> listing = (Listing<T>) msg;
                                                callback.accept(listing);
                                            }
                                            return org.apache.pekko.actor.typed.javadsl.Behaviors.same();
                                        }))
                                .narrow(),
                        "receptionist-callback-" + serviceKey.getId() + "-" + System.currentTimeMillis(),
                        org.apache.pekko.actor.typed.Props.empty());

        subscribe(serviceKey, subscriber);
    }

    /**
     * Deregisters an actor from the receptionist for the given service key.
     *
     * <p>Note: Actors are automatically deregistered when they stop, so explicit deregistration is
     * usually not necessary.
     *
     * @param serviceKey The service key to deregister from
     * @param actorRef The actor to deregister
     * @param <T> The type of messages the actor can handle
     */
    public <T> void deregister(ServiceKey<T> serviceKey, SpringActorRef<T> actorRef) {
        ActorRef<T> underlyingRef = actorRef.getUnderlying();
        actorSystem.getRaw().receptionist().tell(Receptionist.deregister(serviceKey.getUnderlying(), underlyingRef));
    }
}
