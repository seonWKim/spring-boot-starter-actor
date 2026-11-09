package io.github.seonwkim.actor.persistence;

import io.github.seonwkim.actor.SpringActorBehavior;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * An event-sourced actor: commands decide which events to store, events change the state, and the
 * state is rebuilt from the stored events (and the latest snapshot) when the actor starts again.
 *
 * <pre>{@code
 * @Component
 * public class AccountActor implements SpringActor<AccountActor.Command> {
 *     @Override
 *     public SpringActorBehavior<Command> create(SpringActorContext actorContext) {
 *         return SpringEventSourcedBehavior.builder(
 *                         Command.class, Event.class, PersistenceId.of("Account", actorContext.actorId()), Account.EMPTY)
 *                 .onCommand(Deposit.class, (ctx, account, cmd) -> ctx.persist(new Deposited(cmd.amount()))
 *                         .thenReply(cmd, Account::balance))
 *                 .onCommand(GetBalance.class, (ctx, account, cmd) -> ctx.none().thenReply(cmd, Account::balance))
 *                 .onEvent(Deposited.class, (account, event) -> account.add(event.amount()))
 *                 .snapshotEvery(100)
 *                 .build();
 *     }
 * }
 * }</pre>
 *
 * <p>Events and the state (for snapshots) are serialized: implement {@code JsonSerializable} or
 * {@code CborSerializable} from {@code io.github.seonwkim.actor.serialization}. The state should be
 * immutable: an event handler returns the next state.
 *
 * <p>The journal and the snapshots live on the application's {@code DataSource}. Framework commands
 * ({@code FrameworkCommand}) are not handled by event-sourced actors.
 */
public final class SpringEventSourcedBehavior {

    private SpringEventSourcedBehavior() {}

    /**
     * Starts building an event-sourced behavior.
     *
     * @param commandClass the actor's command type
     * @param eventClass the type of the events it stores
     * @param persistenceId the key of its events; the same on every start
     * @param emptyState the state before any event
     */
    public static <C, E, S> Builder<C, E, S> builder(
            Class<C> commandClass, Class<E> eventClass, PersistenceId persistenceId, S emptyState) {
        return new Builder<>(commandClass, eventClass, persistenceId, emptyState);
    }

    /** Handles one command type: returns the effect (events to store, then side effects). */
    @FunctionalInterface
    public interface CommandHandler<C, E, S, M> {
        Effect<E, S> handle(EventSourcedContext<C, E, S> ctx, S state, M command);
    }

    /**
     * A built event-sourced behavior, as data. Read by the engine; application code doesn't need it.
     *
     * @param snapshotEvery store a snapshot every this many events; 0 for no snapshots
     * @param keepSnapshots how many snapshots to keep (older ones are deleted)
     */
    public record Definition<C, E, S>(
            Class<C> commandClass,
            Class<E> eventClass,
            PersistenceId persistenceId,
            S emptyState,
            List<OnCommand<C, E, S, ?>> commandHandlers,
            List<OnEvent<E, S, ?>> eventHandlers,
            int snapshotEvery,
            int keepSnapshots,
            Function<E, Set<String>> tagger) {
        public Definition {
            commandHandlers = List.copyOf(commandHandlers);
            eventHandlers = List.copyOf(eventHandlers);
        }

        /** The effect of the first handler for the command's type, or unhandled. */
        public Effect<E, S> handleCommand(EventSourcedContext<C, E, S> ctx, S state, Object command) {
            for (OnCommand<C, E, S, ?> handler : commandHandlers) {
                Effect<E, S> effect = handler.tryHandle(ctx, state, command);
                if (effect != null) {
                    return effect;
                }
            }
            return Effect.unhandled();
        }

        /** The state after the first handler for the event's type. */
        public S applyEvent(S state, E event) {
            for (OnEvent<E, S, ?> handler : eventHandlers) {
                S next = handler.tryApply(state, event);
                if (next != null) {
                    return next;
                }
            }
            throw new IllegalStateException(
                    "No event handler for " + event.getClass().getName() + " in " + persistenceId.id()
                            + ": add .onEvent(" + event.getClass().getSimpleName() + ".class, ...)");
        }
    }

    /** A command handler of a {@link Definition}. */
    public record OnCommand<C, E, S, M>(Class<M> type, CommandHandler<C, E, S, M> handler) {
        /** The effect, or null when the command is not of this handler's type. */
        @SuppressWarnings("unchecked")
        public @Nullable Effect<E, S> tryHandle(EventSourcedContext<C, E, S> ctx, S state, Object command) {
            return type.isInstance(command) ? handler.handle(ctx, state, (M) command) : null;
        }
    }

    /** An event handler of a {@link Definition}. */
    public record OnEvent<E, S, V>(Class<V> type, BiFunction<S, V, S> handler) {
        /** The next state, or null when the event is not of this handler's type. */
        @SuppressWarnings("unchecked")
        public @Nullable S tryApply(S state, Object event) {
            if (!type.isInstance(event)) {
                return null;
            }
            return Objects.requireNonNull(
                    handler.apply(state, (V) event),
                    () -> "The event handler for " + type.getName() + " returned null");
        }
    }

    public static final class Builder<C, E, S> {
        private final Class<C> commandClass;
        private final Class<E> eventClass;
        private final PersistenceId persistenceId;
        private final S emptyState;
        private final List<OnCommand<C, E, S, ?>> commandHandlers = new ArrayList<>();
        private final List<OnEvent<E, S, ?>> eventHandlers = new ArrayList<>();
        private int snapshotEvery;
        private int keepSnapshots = 2;
        private Function<E, Set<String>> tagger = event -> Set.of();

        private Builder(Class<C> commandClass, Class<E> eventClass, PersistenceId persistenceId, S emptyState) {
            this.commandClass = Objects.requireNonNull(commandClass, "commandClass");
            this.eventClass = Objects.requireNonNull(eventClass, "eventClass");
            this.persistenceId = Objects.requireNonNull(persistenceId, "persistenceId");
            this.emptyState = Objects.requireNonNull(emptyState, "emptyState");
        }

        /** Handles commands of {@code type}. The first handler whose type matches wins. */
        public <M extends C> Builder<C, E, S> onCommand(Class<M> type, CommandHandler<C, E, S, M> handler) {
            commandHandlers.add(new OnCommand<>(Objects.requireNonNull(type, "type"), handler));
            return this;
        }

        /**
         * Applies events of {@code type} to the state: after they are stored, and again on every
         * recovery. It must not have side effects.
         */
        public <V extends E> Builder<C, E, S> onEvent(Class<V> type, BiFunction<S, V, S> handler) {
            eventHandlers.add(new OnEvent<>(Objects.requireNonNull(type, "type"), handler));
            return this;
        }

        /**
         * Stores a snapshot of the state every {@code numberOfEvents} events, keeping the latest two.
         * Recovery then starts from the latest snapshot and replays only the events after it.
         */
        public Builder<C, E, S> snapshotEvery(int numberOfEvents) {
            return snapshotEvery(numberOfEvents, 2);
        }

        /** Like {@link #snapshotEvery(int)}, keeping the latest {@code keepSnapshots} snapshots. */
        public Builder<C, E, S> snapshotEvery(int numberOfEvents, int keepSnapshots) {
            if (numberOfEvents <= 0 || keepSnapshots <= 0) {
                throw new IllegalArgumentException("numberOfEvents and keepSnapshots must be positive, got "
                        + numberOfEvents + " and " + keepSnapshots);
            }
            this.snapshotEvery = numberOfEvents;
            this.keepSnapshots = keepSnapshots;
            return this;
        }

        /** Tags stored with each event; a projection reads the events of one tag. */
        public Builder<C, E, S> withTagger(Function<E, Set<String>> tagger) {
            this.tagger = Objects.requireNonNull(tagger, "tagger");
            return this;
        }

        public SpringActorBehavior<C> build() {
            return SpringActorBehavior.ofModule(new Definition<>(
                    commandClass,
                    eventClass,
                    persistenceId,
                    emptyState,
                    commandHandlers,
                    eventHandlers,
                    snapshotEvery,
                    keepSnapshots,
                    tagger));
        }
    }
}
