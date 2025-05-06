package org.github.seonwkim.core.impl;

import java.util.HashMap;
import java.util.Map;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.github.seonwkim.core.ActorTypeRegistry;
import org.github.seonwkim.core.RootGuardian;

public class DefaultRootGuardian implements RootGuardian {

    public static class SpawnActor<T> implements Command {
        public final Class<T> commandClass;
        public final String actorId;
        public final ActorRef<Spawned<T>> replyTo;

        public SpawnActor(Class<T> commandClass, String actorId, ActorRef<Spawned<T>> replyTo) {
            this.commandClass = commandClass;
            this.actorId = actorId;
            this.replyTo = replyTo;
        }
    }

    public static class Spawned<T> {
        public final ActorRef<T> ref;

        public Spawned(ActorRef<T> ref) {
            this.ref = ref;
        }
    }

    public static Behavior<Command> create(ActorTypeRegistry registry) {
        return Behaviors.setup(ctx -> new DefaultRootGuardian(ctx, registry).behavior());
    }

    private final ActorContext<Command> ctx;
    private final ActorTypeRegistry registry;
    private final Map<String, ActorRef<?>> actorRefs = new HashMap<>();

    public DefaultRootGuardian(ActorContext<Command> ctx, ActorTypeRegistry registry) {
        this.ctx = ctx;
        this.registry = registry;
    }

    private Behavior<Command> behavior() {
        return Behaviors.receive(Command.class)
                        .onMessage(SpawnActor.class, this::handleSpawnActor)
                        .build();
    }

    @SuppressWarnings("unchecked")
    public <T> Behavior<RootGuardian.Command> handleSpawnActor(SpawnActor<T> msg) {
        String key = buildActorKey(msg.commandClass, msg.actorId);

        ActorRef<T> ref;
        if (actorRefs.containsKey(key)) {
            ref = (ActorRef<T>) actorRefs.get(key);
        } else {
            Behavior<T> behavior = registry.createBehavior(msg.commandClass, msg.actorId);
            ref = ctx.spawn(behavior, key);
            actorRefs.put(key, ref);
        }

        msg.replyTo.tell(new Spawned<>(ref));
        return Behaviors.same();
    }

    private String buildActorKey(Class<?> clazz, String actorId) {
        return clazz.getName() + "-" + actorId;
    }
}
