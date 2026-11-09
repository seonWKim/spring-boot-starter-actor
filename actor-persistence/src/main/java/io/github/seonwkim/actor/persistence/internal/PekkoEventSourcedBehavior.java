package io.github.seonwkim.actor.persistence.internal;

import io.github.seonwkim.actor.SpringBehaviorContext;
import io.github.seonwkim.actor.internal.PekkoBehaviorContext;
import io.github.seonwkim.actor.persistence.Effect;
import io.github.seonwkim.actor.persistence.EventSourcedContext;
import io.github.seonwkim.actor.persistence.SpringEventSourcedBehavior;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.EffectBuilder;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;
import org.apache.pekko.persistence.typed.javadsl.RetentionCriteria;

/** Runs a {@link SpringEventSourcedBehavior.Definition} as a Pekko {@link EventSourcedBehavior}. */
final class PekkoEventSourcedBehavior<C, E, S> extends EventSourcedBehavior<C, E, S> {

    private final SpringEventSourcedBehavior.Definition<C, E, S> definition;
    private final EventSourcedContext<C, E, S> context;

    private PekkoEventSourcedBehavior(
            SpringEventSourcedBehavior.Definition<C, E, S> definition, SpringBehaviorContext<C> actorContext) {
        super(PersistenceId.of(
                definition.persistenceId().entityType(),
                definition.persistenceId().entityId()));
        this.definition = definition;
        this.context = new EventSourcedContext<>(actorContext, definition.persistenceId());
    }

    static <C, E, S> Behavior<C> create(SpringEventSourcedBehavior.Definition<C, E, S> definition) {
        return Behaviors.setup(ctx -> Behaviors.withTimers(
                timers -> new PekkoEventSourcedBehavior<>(definition, new PekkoBehaviorContext<>(ctx, timers))));
    }

    @Override
    public S emptyState() {
        return definition.emptyState();
    }

    @Override
    public CommandHandler<C, E, S> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onAnyCommand((state, command) -> toPekko(definition.handleCommand(context, state, command)));
    }

    @Override
    public EventHandler<S, E> eventHandler() {
        return newEventHandlerBuilder().forAnyState().onAnyEvent(definition::applyEvent);
    }

    @Override
    public RetentionCriteria retentionCriteria() {
        return definition.snapshotEvery() == 0
                ? RetentionCriteria.disabled()
                : RetentionCriteria.snapshotEvery(definition.snapshotEvery(), definition.keepSnapshots());
    }

    @Override
    public Set<String> tagsFor(E event) {
        return definition.tagger().apply(event);
    }

    private EffectBuilder<E, S> toPekko(Effect<E, S> effect) {
        EffectBuilder<E, S> pekko =
                switch (effect.kind()) {
                    case PERSIST -> Effect().persist(effect.events());
                    case NONE -> Effect().none();
                    case UNHANDLED -> Effect().unhandled();
                    case STOP -> Effect().stop();
                };
        for (Consumer<S> sideEffect : effect.sideEffects()) {
            pekko = pekko.thenRun((S state) -> sideEffect.accept(state));
        }
        return effect.stopAfter() ? pekko.thenStop() : pekko;
    }
}
