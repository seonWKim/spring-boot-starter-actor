package io.github.seonwkim.actor.fixture;

import io.github.seonwkim.actor.AskCommand;
import io.github.seonwkim.actor.SpringActorBehavior;
import io.github.seonwkim.actor.serialization.JsonSerializable;
import io.github.seonwkim.actor.shard.ShardedEntityType;
import io.github.seonwkim.actor.shard.ShardingStrategy;
import io.github.seonwkim.actor.shard.SpringShardedActor;
import io.github.seonwkim.actor.shard.SpringShardedActorContext;

/**
 * Test sharded actor that demonstrates building a sharded actor WITHOUT using withState().
 * This uses the default state type (ActorContext) and handles messages directly with closures.
 */
public class SimpleShardedActorWithoutWithState
        implements SpringShardedActor<SimpleShardedActorWithoutWithState.Command> {

    public static final ShardedEntityType<Command> TYPE_KEY =
            ShardedEntityType.of(Command.class, "SimpleShardedActorWithoutWithState");

    public interface Command extends JsonSerializable {}

    public static class Echo extends AskCommand<String> implements Command {
        public final String message;

        public Echo(String message) {
            this.message = message;
        }
    }

    public static class GetEntityId extends AskCommand<Object> implements Command {
        public GetEntityId() {}
    }

    @Override
    public ShardedEntityType<Command> entityType() {
        return TYPE_KEY;
    }

    @Override
    public SpringActorBehavior<Command> create(SpringShardedActorContext<Command> ctx) {
        final String entityId = ctx.getEntityId();

        return SpringActorBehavior.builder(Command.class, ctx)
                // No withState() - message handlers work directly with ActorContext
                .onMessage(Echo.class, (actorCtx, msg) -> {
                    actorCtx.getLog().info("Entity {} received echo: {}", entityId, msg.message);
                    msg.reply("Echo from entity [" + entityId + "]: " + msg.message);
                    return SpringActorBehavior.same();
                })
                .onMessage(GetEntityId.class, (actorCtx, msg) -> {
                    msg.reply(entityId);
                    return SpringActorBehavior.same();
                })
                .build();
    }

    @Override
    public ShardingStrategy sharding() {
        return ShardingStrategy.hashed(5);
    }
}
