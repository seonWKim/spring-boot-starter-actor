package io.github.seonwkim.actor.persistence;

import io.github.seonwkim.actor.AskCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What a command handler of a {@link SpringEventSourcedBehavior} decides: events to persist (or
 * none), then what to run once they are stored.
 *
 * <pre>{@code
 * .onCommand(Deposit.class, (ctx, account, cmd) -> ctx.persist(new Deposited(cmd.amount()))
 *         .thenReply(cmd, Account::balance))
 * }</pre>
 *
 * <p>Start a chain from the {@link EventSourcedContext} ({@code ctx.persist(...)}): it knows the
 * event and state types. The static factories here work when the effect is returned as is
 * ({@code return Effect.none();}).
 *
 * <p>Side effects ({@link #thenRun}, {@link #thenReply}) run after the events are stored and applied,
 * with the new state, in the order they were added. They don't run during recovery.
 *
 * @param <E> the event type
 * @param <S> the state type
 */
public final class Effect<E, S> {

    /** What an effect does before its side effects. Read by the engine; application code doesn't need it. */
    public enum Kind {
        /** Store {@link #events()} and apply them to the state. */
        PERSIST,
        /** Store nothing. */
        NONE,
        /** The command wasn't handled: it is published as unhandled. */
        UNHANDLED,
        /** Store nothing and stop the actor. */
        STOP
    }

    private final Kind kind;
    private final List<E> events;
    private final List<Consumer<S>> sideEffects;
    private final boolean stopAfter;

    private Effect(Kind kind, List<E> events, List<Consumer<S>> sideEffects, boolean stopAfter) {
        this.kind = kind;
        this.events = List.copyOf(events);
        this.sideEffects = List.copyOf(sideEffects);
        this.stopAfter = stopAfter;
    }

    /** Store {@code event}, then apply it to the state. */
    public static <E, S> Effect<E, S> persist(E event) {
        return new Effect<>(Kind.PERSIST, List.of(Objects.requireNonNull(event, "event")), List.of(), false);
    }

    /** Store {@code events} atomically, then apply them to the state in order. */
    public static <E, S> Effect<E, S> persist(List<? extends E> events) {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("events is empty: use Effect.none()");
        }
        return new Effect<>(Kind.PERSIST, List.copyOf(events), List.of(), false);
    }

    /** Store nothing; side effects still run, with the current state. */
    public static <E, S> Effect<E, S> none() {
        return new Effect<>(Kind.NONE, List.of(), List.of(), false);
    }

    /** The command isn't handled in this state. */
    public static <E, S> Effect<E, S> unhandled() {
        return new Effect<>(Kind.UNHANDLED, List.of(), List.of(), false);
    }

    /** Store nothing and stop the actor. */
    public static <E, S> Effect<E, S> stop() {
        return new Effect<>(Kind.STOP, List.of(), List.of(), false);
    }

    /** Run {@code action} with the state after the events are applied. */
    public Effect<E, S> thenRun(Consumer<S> action) {
        List<Consumer<S>> next = new ArrayList<>(sideEffects);
        next.add(Objects.requireNonNull(action, "action"));
        return new Effect<>(kind, events, next, stopAfter);
    }

    /** Reply to {@code command} with a value read from the state after the events are applied. */
    public <R> Effect<E, S> thenReply(AskCommand<R> command, Function<S, R> reply) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(reply, "reply");
        return thenRun(state -> command.reply(reply.apply(state)));
    }

    /** Stop the actor after the side effects. */
    public Effect<E, S> thenStop() {
        return new Effect<>(kind, events, sideEffects, true);
    }

    public Kind kind() {
        return kind;
    }

    /** The events to store when {@link #kind()} is {@link Kind#PERSIST}. */
    public List<E> events() {
        return events;
    }

    public List<Consumer<S>> sideEffects() {
        return sideEffects;
    }

    public boolean stopAfter() {
        return stopAfter;
    }
}
