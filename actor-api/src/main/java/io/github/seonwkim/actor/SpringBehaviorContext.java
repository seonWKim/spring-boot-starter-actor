package io.github.seonwkim.actor;

import io.github.seonwkim.actor.receptionist.ServiceName;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * What an actor can do from inside its handlers: reach itself, log, and manage its children. The
 * default state of a {@link SpringActorBehavior.Builder} is this context.
 *
 * <pre>{@code
 * return SpringActorBehavior.builder(Command.class, actorContext)
 *     .withState(ctx -> {
 *         ctx.getLog().info("Actor starting");
 *         return new MyBehavior(ctx);
 *     })
 *     .onMessage(DoWork.class, MyBehavior::handleWork)
 *     .build();
 * }</pre>
 *
 * @param <T> the message type the actor handles
 * @see SpringActorBehavior
 */
public interface SpringBehaviorContext<T> {

    /** A handle to this actor, e.g. to pass as a reply address. */
    SpringActorHandle<T> getSelf();

    /** The actor's logger; its lines carry the actor's path and MDC. */
    Logger getLog();

    /** This actor's path. */
    String path();

    /** The address of the node this actor runs on; see {@link SpringActorSystem#nodeAddress()}. */
    String nodeAddress();

    /** The child actor with this name, if it is running. */
    Optional<SpringActorRef<Void>> getChild(String name);

    /** The running child actors. */
    List<SpringActorRef<Void>> getChildren();

    /** Stops a child actor of this actor. */
    void stop(SpringActorRef<?> child);

    /**
     * Sends {@code message} to this actor once, after {@code delay}. Starting a timer with a key
     * that is already active replaces it.
     */
    void startSingleTimer(Object key, T message, Duration delay);

    /** Sends {@code message} to this actor every {@code interval}, until the timer is cancelled. */
    void startTimerAtFixedRate(Object key, T message, Duration interval);

    /** Cancels the timer with this key; a message it already sent is not delivered. */
    void cancelTimer(Object key);

    /** Watches {@code other}: when it stops, this actor receives the {@link ActorTerminated} signal. */
    void watch(SpringActorRef<?> other);

    /**
     * A reference that accepts messages of another protocol and delivers them to this actor as
     * commands, for example to receive replies from an actor that does not know this actor's type.
     */
    <U> SpringActorRef<U> messageAdapter(Class<U> messageClass, Function<U, ? extends T> toCommand);

    /**
     * Sends this actor a message when {@code future} completes, built from its value or failure.
     * Use it instead of acting on the result in the future's callback, which runs outside the
     * actor and must not touch its state.
     */
    <V> void pipeToSelf(CompletionStage<V> future, BiFunction<? super V, Throwable, ? extends T> toMessage);

    /** Registers this actor with the receptionist under {@code key}. */
    void registerWithReceptionist(ServiceName<T> name);

    /**
     * Escape hatch: the underlying engine's context, e.g. Pekko's
     * {@code org.apache.pekko.actor.typed.javadsl.ActorContext}. Code that uses it depends on the
     * engine and gets no compatibility promise from the starter.
     *
     * @throws IllegalArgumentException when the context is not of that type
     */
    <X> X unwrap(Class<X> type);
}
