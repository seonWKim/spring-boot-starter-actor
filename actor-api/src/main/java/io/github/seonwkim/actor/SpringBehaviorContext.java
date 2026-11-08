package io.github.seonwkim.actor;

import io.github.seonwkim.actor.receptionist.ServiceName;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
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

    /** The child actor with this name, if it is running. */
    Optional<SpringActorRef<Void>> getChild(String name);

    /** The running child actors. */
    List<SpringActorRef<Void>> getChildren();

    /** Stops a child actor of this actor. */
    void stop(SpringActorRef<?> child);

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
