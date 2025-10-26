package io.github.seonwkim.core.impl;

import io.github.seonwkim.core.ActorTypeRegistry;
import io.github.seonwkim.core.RootGuardian;
import io.github.seonwkim.core.SpringActorContext;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

/**
 * Default implementation of the {@code RootGuardian} interface. This class manages the lifecycle of
 * actors by handling spawn commands.
 */
public class DefaultRootGuardian implements RootGuardian {

    /**
     * Creates a new DefaultRootGuardian behavior with the given actor type registry.
     *
     * @param registry The ActorTypeRegistry to use for creating actor behaviors
     * @return A behavior for the DefaultRootGuardian
     */
    public static Behavior<Command> create(ActorTypeRegistry registry) {
        return Behaviors.setup(ctx -> new DefaultRootGuardian(ctx, registry).behavior());
    }

    /** The actor context */
    private final ActorContext<Command> ctx;
    /** The actor type registry */
    private final ActorTypeRegistry registry;

    /**
     * Creates a new DefaultRootGuardian with the given actor context and actor type registry.
     *
     * @param ctx The actor context
     * @param registry The actor type registry
     */
    public DefaultRootGuardian(ActorContext<Command> ctx, ActorTypeRegistry registry) {
        this.ctx = ctx;
        this.registry = registry;
    }

    /**
     * Creates the behavior for this DefaultRootGuardian. The behavior handles SpawnActor, GetActor, and CheckExists commands.
     *
     * @return A behavior for this DefaultRootGuardian
     */
    private Behavior<Command> behavior() {
        return Behaviors.setup(ctx -> Behaviors.receive(Command.class)
                .onMessage(SpawnActor.class, this::handleSpawnActor)
                .onMessage(GetActor.class, this::handleGetActor)
                .onMessage(CheckExists.class, this::handleCheckExists)
                .build());
    }

    /**
     * Handles a SpawnActor command by creating a new actor. Each spawn request creates a new actor
     * instance. Users should implement their own caching if they want to reuse actor references.
     *
     * <p>If an actor with the same name already exists, Pekko will throw an InvalidActorNameException.
     * Users should either use unique IDs or implement caching to reuse actor references.
     *
     * @param msg The SpawnActor command
     * @return The same behavior, as this handler doesn't change the behavior
     */
    public Behavior<RootGuardian.Command> handleSpawnActor(SpawnActor msg) {
        String key = buildActorKey(msg.actorClass, msg.actorContext);

        Behavior<?> behavior = registry.createBehavior(msg.actorClass, msg.actorContext);
        ActorRef<?> ref = ctx.spawn(behavior, key, msg.mailboxSelector);

        msg.replyTo.tell(new Spawned<>(ref));
        return Behaviors.same();
    }

    /**
     * Handles a GetActor command by looking up a child actor using the actor context.
     * No caching is used - the lookup is performed directly on the actor context.
     *
     * @param msg The GetActor command
     * @return The same behavior
     */
    public Behavior<RootGuardian.Command> handleGetActor(GetActor msg) {
        String key = buildActorKey(msg.actorClass, msg.actorContext);
        ActorRef<?> ref = ctx.getChild(key).orElse(null);

        msg.replyTo.tell(new GetActorResponse<>(ref));
        return Behaviors.same();
    }

    /**
     * Handles a CheckExists command by checking if a child actor exists using the actor context.
     * No caching is used - the lookup is performed directly on the actor context.
     *
     * @param msg The CheckExists command
     * @return The same behavior
     */
    public Behavior<RootGuardian.Command> handleCheckExists(CheckExists msg) {
        String key = buildActorKey(msg.actorClass, msg.actorContext);
        boolean exists = ctx.getChild(key).isPresent();

        msg.replyTo.tell(new ExistsResponse(exists));
        return Behaviors.same();
    }

    private String buildActorKey(Class<?> actorClass, SpringActorContext actorContext) {
        return actorClass.getName() + ":" + actorContext.actorId();
    }
}
