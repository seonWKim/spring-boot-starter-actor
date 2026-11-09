package io.github.seonwkim.actor.persistence;

import io.github.seonwkim.actor.SpringBehaviorContext;
import java.util.List;
import java.util.Objects;

/**
 * What a command handler of a {@link SpringEventSourcedBehavior} gets besides the state and the
 * command: the actor's context, and effects typed with the behavior's event and state types.
 *
 * @param <C> the command type
 * @param <E> the event type
 * @param <S> the state type
 */
public final class EventSourcedContext<C, E, S> {

    private final SpringBehaviorContext<C> actorContext;
    private final PersistenceId persistenceId;

    /** Created by the engine for each actor. */
    public EventSourcedContext(SpringBehaviorContext<C> actorContext, PersistenceId persistenceId) {
        this.actorContext = Objects.requireNonNull(actorContext, "actorContext");
        this.persistenceId = Objects.requireNonNull(persistenceId, "persistenceId");
    }

    /** The actor's context: its own handle, its logger, timers, children. */
    public SpringBehaviorContext<C> actorContext() {
        return actorContext;
    }

    public PersistenceId persistenceId() {
        return persistenceId;
    }

    /** See {@link Effect#persist(Object)}. */
    public Effect<E, S> persist(E event) {
        return Effect.persist(event);
    }

    /** See {@link Effect#persist(List)}. */
    public Effect<E, S> persist(List<? extends E> events) {
        return Effect.persist(events);
    }

    /** See {@link Effect#none()}. */
    public Effect<E, S> none() {
        return Effect.none();
    }

    /** See {@link Effect#unhandled()}. */
    public Effect<E, S> unhandled() {
        return Effect.unhandled();
    }

    /** See {@link Effect#stop()}. */
    public Effect<E, S> stop() {
        return Effect.stop();
    }
}
