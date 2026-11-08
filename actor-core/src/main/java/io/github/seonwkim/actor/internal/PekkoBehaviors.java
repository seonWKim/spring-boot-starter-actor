package io.github.seonwkim.actor.internal;

import io.github.seonwkim.actor.ActorSignal;
import io.github.seonwkim.actor.PostStop;
import io.github.seonwkim.actor.PreRestart;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.SpringActorContext;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Signal;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/** Compiles a {@link SpringActorBehavior} to a Pekko {@link Behavior}. */
public final class PekkoBehaviors {

    private PekkoBehaviors() {}

    @SuppressWarnings("unchecked")
    public static <C> Behavior<C> toPekko(SpringActorBehavior<C> behavior) {
        return switch (behavior.kind()) {
            case SAME -> Behaviors.same();
            case STOPPED -> Behaviors.stopped();
            case UNHANDLED -> Behaviors.unhandled();
            case NATIVE -> {
                if (!(behavior.nativeBehavior() instanceof Behavior<?> pekko)) {
                    throw new IllegalArgumentException("SpringActorBehavior.wrap expects an " + Behavior.class.getName()
                            + ", got " + behavior.nativeBehavior().getClass().getName());
                }
                yield (Behavior<C>) pekko;
            }
            case DEFINED -> compile(behavior.definition());
        };
    }

    private static <C, S> Behavior<C> compile(SpringActorBehavior.Definition<C, S> definition) {
        Behavior<C> behavior = Behaviors.setup(ctx -> {
            S state = definition.stateFactory().apply(new PekkoBehaviorContext<>(ctx));
            return receive(ctx, definition, state);
        });
        if (definition.supervisionStrategy() != null) {
            behavior = Behaviors.supervise(behavior).onFailure(PekkoProps.supervisor(definition.supervisionStrategy()));
        }
        return withMdc(definition, behavior);
    }

    /**
     * One receive for user commands, framework commands and signals. It takes {@code Object} because
     * framework commands are not of the user's command type.
     */
    @SuppressWarnings("unchecked")
    private static <C, S> Behavior<C> receive(
            ActorContext<C> ctx, SpringActorBehavior.Definition<C, S> definition, S state) {
        return Behaviors.receive(Object.class)
                .onMessage(Object.class, msg -> {
                    if (definition.frameworkCommands() && handleFrameworkCommand(ctx, msg)) {
                        return Behaviors.same();
                    }
                    if (definition.commandClass().isInstance(msg)) {
                        for (SpringActorBehavior.MessageHandler<C, S, ?> handler : definition.messageHandlers()) {
                            SpringActorBehavior<C> next = handler.tryHandle(state, msg);
                            if (next != null) {
                                return (Behavior<Object>) (Behavior<?>) toPekko(next);
                            }
                        }
                    }
                    return Behaviors.unhandled();
                })
                .onSignal(Signal.class, signal -> {
                    ActorSignal ours = toActorSignal(signal);
                    if (ours != null) {
                        for (SpringActorBehavior.SignalHandler<C, S, ?> handler : definition.signalHandlers()) {
                            SpringActorBehavior<C> next = handler.tryHandle(state, ours);
                            if (next != null) {
                                return (Behavior<Object>) (Behavior<?>) toPekko(next);
                            }
                        }
                    }
                    return Behaviors.unhandled();
                })
                .build()
                .narrow();
    }

    private static @Nullable ActorSignal toActorSignal(Signal signal) {
        if (signal instanceof org.apache.pekko.actor.typed.PreRestart) {
            return PreRestart.INSTANCE;
        }
        if (signal instanceof org.apache.pekko.actor.typed.PostStop) {
            return PostStop.INSTANCE;
        }
        return null;
    }

    private static <C, S> Behavior<C> withMdc(SpringActorBehavior.Definition<C, S> definition, Behavior<C> behavior) {
        Map<String, String> staticMdc = definition.actorContext().mdcConfig().getMdc();
        Function<C, Map<String, String>> perMessage = definition.mdcForMessage();
        Class<C> type = definition.commandClass();
        if (perMessage != null && !staticMdc.isEmpty()) {
            return Behaviors.withMdc(type, staticMdc, perMessage::apply, behavior);
        }
        if (perMessage != null) {
            return Behaviors.withMdc(type, perMessage::apply, behavior);
        }
        if (!staticMdc.isEmpty()) {
            return Behaviors.withMdc(type, staticMdc, behavior);
        }
        return behavior;
    }

    /** True when {@code msg} was a framework command, now handled. */
    private static boolean handleFrameworkCommand(ActorContext<?> ctx, Object msg) {
        if (msg instanceof FrameworkCommands.SpawnChild<?> spawn) {
            spawnChild(ctx, spawn);
            return true;
        }
        if (msg instanceof FrameworkCommands.GetChild<?> get) {
            getChild(ctx, get);
            return true;
        }
        if (msg instanceof FrameworkCommands.ExistsChild<?> exists) {
            boolean found = ActorSpawner.actorExists(ctx, exists.actorClass, exists.childId);
            exists.replyTo.tell(
                    found
                            ? FrameworkCommands.ExistsChildResponse.exists()
                            : FrameworkCommands.ExistsChildResponse.notExists());
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <CC> void spawnChild(ActorContext<?> ctx, FrameworkCommands.SpawnChild<CC> msg) {
        try {
            SpringActorContext childContext = msg.childContext;
            String childName = ActorSpawner.buildActorName(msg.actorClass, childContext.actorId());
            var existing = ctx.getChild(childName);
            if (existing.isPresent()) {
                msg.replyTo.tell(FrameworkCommands.SpawnChildResponse.alreadyExists((ActorRef<CC>) existing.get()));
                return;
            }
            ActorRef<CC> childRef = ActorSpawner.spawnActor(
                    ctx,
                    msg.actorClass,
                    childContext,
                    childName,
                    msg.strategy,
                    msg.mailboxConfig,
                    msg.dispatcherConfig,
                    msg.tagsConfig,
                    null, // cluster singletons are top-level only
                    false);
            msg.replyTo.tell(FrameworkCommands.SpawnChildResponse.success(childRef));
        } catch (Exception e) {
            ctx.getLog().error("Failed to spawn child actor", e);
            msg.replyTo.tell(FrameworkCommands.SpawnChildResponse.failure("Failed to spawn child: " + e.getMessage()));
        }
    }

    private static <CC> void getChild(ActorContext<?> ctx, FrameworkCommands.GetChild<CC> msg) {
        ActorRef<CC> childRef = ActorSpawner.getActor(ctx, msg.actorClass, msg.childId);
        msg.replyTo.tell(
                childRef != null
                        ? FrameworkCommands.GetChildResponse.found(childRef)
                        : FrameworkCommands.GetChildResponse.notFound());
    }
}
