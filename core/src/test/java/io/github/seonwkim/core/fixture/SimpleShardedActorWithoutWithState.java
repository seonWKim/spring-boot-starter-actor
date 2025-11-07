package io.github.seonwkim.core.fixture;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.seonwkim.core.AskCommand;
import io.github.seonwkim.core.serialization.JsonSerializable;
import io.github.seonwkim.core.shard.DefaultShardingMessageExtractor;
import io.github.seonwkim.core.shard.ShardEnvelope;
import io.github.seonwkim.core.shard.SpringShardedActor;
import io.github.seonwkim.core.shard.SpringShardedActorBehavior;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

/**
 * Test sharded actor that demonstrates building a sharded actor WITHOUT using withState().
 * This uses the default state type (ActorContext) and handles messages directly with closures.
 */
public class SimpleShardedActorWithoutWithState
        implements SpringShardedActor<SimpleShardedActorWithoutWithState.Command> {

    public static final EntityTypeKey<Command> TYPE_KEY =
            EntityTypeKey.create(Command.class, "SimpleShardedActorWithoutWithState");

    public interface Command extends JsonSerializable {}

    public static class Echo extends AskCommand<String> implements Command {
        public final String message;

        @JsonCreator
        public Echo(@JsonProperty("message") String message) {
            this.message = message;
        }
    }

    public static class GetEntityId extends AskCommand<Object> implements Command {
        @JsonCreator
        public GetEntityId() {}
    }

    @Override
    public EntityTypeKey<Command> typeKey() {
        return TYPE_KEY;
    }

    @Override
    public SpringShardedActorBehavior<Command> create(EntityContext<Command> ctx) {
        final String entityId = ctx.getEntityId();

        return SpringShardedActorBehavior.builder(Command.class, ctx)
                // No withState() - message handlers work directly with ActorContext
                .onMessage(Echo.class, (actorCtx, msg) -> {
                    actorCtx.getLog().info("Entity {} received echo: {}", entityId, msg.message);
                    msg.reply("Echo from entity [" + entityId + "]: " + msg.message);
                    return Behaviors.same();
                })
                .onMessage(GetEntityId.class, (actorCtx, msg) -> {
                    msg.reply(entityId);
                    return Behaviors.same();
                })
                .build();
    }

    @Override
    public ShardingMessageExtractor<ShardEnvelope<Command>, Command> extractor() {
        return new DefaultShardingMessageExtractor<>(5);
    }
}
